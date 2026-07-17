package web

import (
	"context"
	"fmt"
	"log"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

const (
	runtimeFileDebounce   = 100 * time.Millisecond
	runtimeHealthInterval = 15 * time.Second
)

type RuntimeMonitor struct {
	checker RuntimeStatusChecker
	watcher *fsnotify.Watcher

	mu          sync.RWMutex
	status      RuntimeStatus
	subscribers map[chan RuntimeStatus]struct{}

	done      chan struct{}
	closeOnce sync.Once
}

func NewRuntimeMonitor(checker RuntimeStatusChecker) (*RuntimeMonitor, error) {
	runtimeDir, err := filepath.Abs(checker.EriiDir)
	if err != nil {
		return nil, fmt.Errorf("resolve Erii runtime directory: %w", err)
	}
	checker.EriiDir = filepath.Clean(runtimeDir)
	if err := os.MkdirAll(checker.EriiDir, 0755); err != nil {
		return nil, fmt.Errorf("create Erii runtime directory: %w", err)
	}
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return nil, fmt.Errorf("create Erii runtime watcher: %w", err)
	}
	if err := watcher.Add(checker.EriiDir); err != nil {
		_ = watcher.Close()
		return nil, fmt.Errorf("watch Erii runtime directory: %w", err)
	}

	return &RuntimeMonitor{
		checker:     checker,
		watcher:     watcher,
		status:      checker.Check(context.Background()),
		subscribers: make(map[chan RuntimeStatus]struct{}),
		done:        make(chan struct{}),
	}, nil
}

func (m *RuntimeMonitor) Run(ctx context.Context) {
	healthTicker := time.NewTicker(runtimeHealthInterval)
	defer healthTicker.Stop()

	var debounceTimer *time.Timer
	var debounceC <-chan time.Time
	defer func() {
		if debounceTimer != nil {
			debounceTimer.Stop()
		}
	}()

	for {
		select {
		case <-ctx.Done():
			return
		case <-m.done:
			return
		case event, ok := <-m.watcher.Events:
			if !ok {
				return
			}
			if !isRuntimeFileEvent(event) {
				continue
			}
			if debounceTimer == nil {
				debounceTimer = time.NewTimer(runtimeFileDebounce)
			} else {
				if !debounceTimer.Stop() {
					select {
					case <-debounceTimer.C:
					default:
					}
				}
				debounceTimer.Reset(runtimeFileDebounce)
			}
			debounceC = debounceTimer.C
		case <-debounceC:
			debounceC = nil
			m.refresh(ctx)
		case <-healthTicker.C:
			// File events handle normal lifecycle changes immediately. This lower
			// frequency heartbeat detects crashes that leave stale runtime files.
			m.refresh(ctx)
		case err, ok := <-m.watcher.Errors:
			if !ok {
				return
			}
			log.Printf("Erii runtime watcher error: %v", err)
		}
	}
}

func (m *RuntimeMonitor) Current() RuntimeStatus {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return m.status
}

func (m *RuntimeMonitor) Subscribe() (<-chan RuntimeStatus, func()) {
	updates := make(chan RuntimeStatus, 1)
	m.mu.Lock()
	m.subscribers[updates] = struct{}{}
	m.mu.Unlock()

	var once sync.Once
	return updates, func() {
		once.Do(func() {
			m.mu.Lock()
			delete(m.subscribers, updates)
			m.mu.Unlock()
		})
	}
}

func (m *RuntimeMonitor) Done() <-chan struct{} {
	return m.done
}

func (m *RuntimeMonitor) Close() error {
	var err error
	m.closeOnce.Do(func() {
		close(m.done)
		err = m.watcher.Close()
	})
	return err
}

func (m *RuntimeMonitor) refresh(ctx context.Context) {
	checkCtx, cancel := context.WithTimeout(ctx, 2*time.Second)
	defer cancel()
	status := m.checker.Check(checkCtx)

	m.mu.Lock()
	if status == m.status {
		m.mu.Unlock()
		return
	}
	m.status = status
	subscribers := make([]chan RuntimeStatus, 0, len(m.subscribers))
	for subscriber := range m.subscribers {
		subscribers = append(subscribers, subscriber)
	}
	m.mu.Unlock()

	for _, subscriber := range subscribers {
		select {
		case subscriber <- status:
		default:
			select {
			case <-subscriber:
			default:
			}
			select {
			case subscriber <- status:
			default:
			}
		}
	}
}

func isRuntimeFileEvent(event fsnotify.Event) bool {
	name := filepath.Base(event.Name)
	if name != "erii.pid" && name != "erii.sock" {
		return false
	}
	return event.Has(fsnotify.Create) ||
		event.Has(fsnotify.Write) ||
		event.Has(fsnotify.Remove) ||
		event.Has(fsnotify.Rename)
}
