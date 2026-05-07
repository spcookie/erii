package manage

import (
	"fmt"
	"sort"
	"strconv"
	"strings"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/table"
	"github.com/charmbracelet/bubbles/textinput"
	"github.com/charmbracelet/bubbles/viewport"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
	"github.com/charmbracelet/lipgloss"
)

type previewHelpKeyMap struct{}

func (previewHelpKeyMap) ShortHelp() []key.Binding {
	return []key.Binding{
		key.NewBinding(key.WithKeys("esc", "enter"), key.WithHelp("esc/enter", "close")),
		key.NewBinding(key.WithKeys("up", "down", "k", "j"), key.WithHelp("↑/↓/k/j", "scroll")),
		key.NewBinding(key.WithKeys("pgup", "pgdown", "b", "f"), key.WithHelp("pgup/pgdown/b/f", "page")),
		key.NewBinding(key.WithKeys("u", "d"), key.WithHelp("u/d", "half page")),
	}
}

func (previewHelpKeyMap) FullHelp() [][]key.Binding {
	return [][]key.Binding{
		{
			key.NewBinding(key.WithKeys("esc", "enter"), key.WithHelp("esc/enter", "close")),
			key.NewBinding(key.WithKeys("up", "down", "k", "j"), key.WithHelp("↑/↓/k/j", "scroll")),
			key.NewBinding(key.WithKeys("pgup", "pgdown", "b", "f"), key.WithHelp("pgup/pgdown/b/f", "page")),
			key.NewBinding(key.WithKeys("u", "d"), key.WithHelp("u/d", "half page")),
		},
	}
}

var previewHelpKeys = previewHelpKeyMap{}

// Messages

type dataLoadedMsg struct {
	Items []any
	Error error
}

type deleteDoneMsg struct {
	Error error
}

type batchDeleteDoneMsg struct {
	Deleted int
	Error   error
}

// DataTableModel

type DataTableModel struct {
	resourceType ResourceType
	botID        string
	groupID      string
	api          *API

	items         []any
	filteredItems []any
	selected      map[string]bool

	searchInput textinput.Model
	searching   bool
	searchQuery string

	loading bool
	err     error

	confirmForm  *huh.Form
	confirmValue bool
	confirmBatch bool
	deleteKey    string

	previewing  bool
	previewItem any
	previewVP   viewport.Model

	table     table.Model
	formatter tableFormatter
	width     int
	height    int
	keys      tableKeys
	help      help.Model

	pageSize    int
	currentPage int
	sortCol     int
	sortAsc     bool
}

func NewDataTableModel(api *API, rt ResourceType, bot BotInfo, group GroupInfo) *DataTableModel {
	ti := textinput.New()
	ti.Placeholder = "Search..."
	ti.PromptStyle = lipgloss.NewStyle().Foreground(style.Accent)
	ti.TextStyle = lipgloss.NewStyle().Foreground(style.Text)
	ti.Cursor.Style = lipgloss.NewStyle().Foreground(style.Accent)

	t := table.New()
	s := table.DefaultStyles()
	s.Header = s.Header.
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderColor).
		BorderBottom(true).
		Bold(true).
		Foreground(style.Primary)
	s.Cell = s.Cell.
		Padding(0, 1)
	s.Selected = s.Selected.
		Foreground(style.Primary).
		Bold(true)
	t.SetStyles(s)

	km := table.DefaultKeyMap()
	km.PageDown = key.NewBinding(key.WithKeys("f", "pgdown"))
	t.KeyMap = km

	return &DataTableModel{
		resourceType: rt,
		botID:        bot.BotID,
		groupID:      group.GroupID,
		api:          api,
		selected:     make(map[string]bool),
		searchInput:  ti,
		table:        t,
		formatter:    getFormatter(rt),
		keys:         defaultTableKeys,
		help:         help.New(),
		loading:      true,
		width:        80,
		height:       24,
		pageSize:     20,
		currentPage:  0,
		sortCol:      -1,
		sortAsc:      true,
	}
}

func newConfirmForm(title, description string, value *bool, width int) *huh.Form {
	t := huh.ThemeBase()
	redTitle := lipgloss.NewStyle().Align(lipgloss.Center).Foreground(style.Error).Bold(true)
	redDesc := lipgloss.NewStyle().Align(lipgloss.Center).Foreground(style.Error)
	t.Focused.Base = lipgloss.NewStyle().Align(lipgloss.Center)
	t.Focused.Title = redTitle
	t.Focused.Description = redDesc
	t.Blurred.Base = lipgloss.NewStyle().Align(lipgloss.Center)
	t.Blurred.Title = redTitle
	t.Blurred.Description = redDesc
	km := huh.NewDefaultKeyMap()
	km.Quit = key.NewBinding(key.WithKeys("esc"))
	return huh.NewForm(
		huh.NewGroup(
			huh.NewConfirm().
				Title(title).
				Description(description).
				Affirmative("Confirm").
				Negative("Cancel").
				Value(value),
		),
	).WithWidth(width).WithShowHelp(false).WithTheme(t).WithKeyMap(km)
}

func (m *DataTableModel) Init() tea.Cmd {
	return func() tea.Msg {
		items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
		return dataLoadedMsg{Items: items, Error: err}
	}
}

func (m *DataTableModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	if m.confirmForm != nil {
		if keyMsg, ok := msg.(tea.KeyMsg); ok && keyMsg.Type == tea.KeyCtrlC {
			return m, tea.Quit
		}
		if wmsg, ok := msg.(tea.WindowSizeMsg); ok {
			m.width = wmsg.Width
			m.height = wmsg.Height
			m.confirmForm.WithWidth(m.width)
		}
		newModel, cmd := m.confirmForm.Update(msg)
		m.confirmForm = newModel.(*huh.Form)
		switch m.confirmForm.State {
		case huh.StateCompleted:
			m.confirmForm = nil
			confirmed := m.confirmValue
			isBatch := m.confirmBatch
			key := m.deleteKey
			m.confirmBatch = false
			m.deleteKey = ""
			if confirmed {
				if isBatch {
					return m, m.doBatchDelete()
				}
				return m, m.doDelete(key)
			}
			return m, nil
		case huh.StateAborted:
			m.confirmForm = nil
			m.confirmBatch = false
			m.deleteKey = ""
			return m, nil
		}
		return m, cmd
	}

	if m.previewing {
		switch msg := msg.(type) {
		case tea.KeyMsg:
			if msg.Type == tea.KeyCtrlC {
				return m, tea.Quit
			}
			if key.Matches(msg, m.keys.Back) || msg.Type == tea.KeyEnter {
				m.previewing = false
				m.previewItem = nil
				return m, nil
			}
			var cmd tea.Cmd
			m.previewVP, cmd = m.previewVP.Update(msg)
			return m, cmd
		case tea.WindowSizeMsg:
			m.width = msg.Width
			m.height = msg.Height
			m.previewVP.Width = msg.Width - 10
			if m.previewVP.Width < 20 {
				m.previewVP.Width = 20
			}
			m.previewVP.Height = msg.Height - 6
			if m.previewVP.Height < 5 {
				m.previewVP.Height = 5
			}
			return m, nil
		}
		return m, nil
	}

	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.updateTableSize()
		return m, nil

	case dataLoadedMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.items = msg.Items
		m.applyFilter()
		return m, nil

	case deleteDoneMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		return m, func() tea.Msg {
			items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
			return dataLoadedMsg{Items: items, Error: err}
		}

	case batchDeleteDoneMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
		}
		m.selected = make(map[string]bool)
		return m, func() tea.Msg {
			items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
			return dataLoadedMsg{Items: items, Error: err}
		}

	case RefreshMsg:
		m.loading = true
		m.err = nil
		return m, func() tea.Msg {
			items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
			return dataLoadedMsg{Items: items, Error: err}
		}

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}

		if m.searching {
			if key.Matches(msg, m.keys.Up) {
				return m.moveUp()
			}
			if key.Matches(msg, m.keys.Down) {
				return m.moveDown()
			}
			if key.Matches(msg, m.keys.PageUp) {
				return m.prevPage()
			}
			if key.Matches(msg, m.keys.PageDown) {
				return m.nextPage()
			}

			switch msg.Type {
			case tea.KeyEsc:
				m.searching = false
				m.searchInput.Blur()
				m.searchQuery = ""
				m.applyFilter()
				m.updateTableSize()
				return m, nil
			case tea.KeyEnter:
				m.searching = false
				m.searchInput.Blur()
				m.searchQuery = m.searchInput.Value()
				m.applyFilter()
				m.updateTableSize()
				return m, nil
			default:
				if msg.Type == tea.KeyRunes || msg.Type == tea.KeyBackspace {
					var cmd tea.Cmd
					m.searchInput, cmd = m.searchInput.Update(msg)
					m.searchQuery = m.searchInput.Value()
					m.applyFilter()
					return m, cmd
				}
			}
			return m, nil
		}

		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			m.updateTableSize()
			return m, nil
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return PopMsg{} }
		}
		if key.Matches(msg, m.keys.Search) {
			m.searching = true
			m.searchInput.Focus()
			m.updateTableSize()
			return m, textinput.Blink
		}
		if key.Matches(msg, m.keys.Refresh) {
			m.loading = true
			m.err = nil
			return m, func() tea.Msg {
				items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
				return dataLoadedMsg{Items: items, Error: err}
			}
		}
		if key.Matches(msg, m.keys.New) && m.formatter.canCreate {
			return m, func() tea.Msg {
				return PushEditMsg{
					ResourceType: m.resourceType,
					Bot:          BotInfo{BotID: m.botID},
					Group:        GroupInfo{GroupID: m.groupID},
					Data:         nil,
					IsCreate:     true,
				}
			}
		}
		if key.Matches(msg, m.keys.Enter) {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			idx := m.cursorItemIndex()
			if idx < 0 || idx >= len(m.filteredItems) {
				return m, nil
			}
			item := m.filteredItems[idx]
			if m.formatter.canEdit {
				return m, func() tea.Msg {
					return PushEditMsg{
						ResourceType: m.resourceType,
						Bot:          BotInfo{BotID: m.botID},
						Group:        GroupInfo{GroupID: m.groupID},
						Data:         item,
						IsCreate:     false,
					}
				}
			}
			m.enterPreview(item)
			return m, nil
		}
		if key.Matches(msg, m.keys.Preview) {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			idx := m.cursorItemIndex()
			if idx < 0 || idx >= len(m.filteredItems) {
				return m, nil
			}
			m.enterPreview(m.filteredItems[idx])
			return m, nil
		}
		if key.Matches(msg, m.keys.Select) {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			idx := m.cursorItemIndex()
			if idx >= 0 && idx < len(m.filteredItems) {
				item := m.filteredItems[idx]
				k := m.formatter.getKey(item)
				if m.selected[k] {
					delete(m.selected, k)
				} else {
					m.selected[k] = true
				}
				m.updateTableRows()
				return m, nil
			}
		}
		if key.Matches(msg, m.keys.Delete) && m.formatter.canDelete {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			m.deleteKey = m.getKeyAtCursor()
			m.confirmBatch = false
			m.confirmValue = false
			m.confirmForm = newConfirmForm("Confirm Delete", "Delete this item?", &m.confirmValue, m.width)
			return m, m.confirmForm.Init()
		}
		if key.Matches(msg, m.keys.BatchDel) && m.formatter.canDelete {
			if len(m.selected) == 0 {
				return m, nil
			}
			m.confirmBatch = true
			m.confirmValue = false
			m.confirmForm = newConfirmForm("Confirm Batch Delete", fmt.Sprintf("Delete %d selected items?", len(m.selected)), &m.confirmValue, m.width)
			return m, m.confirmForm.Init()
		}

		if key.Matches(msg, m.keys.Up) {
			return m.moveUp()
		}
		if key.Matches(msg, m.keys.Down) {
			return m.moveDown()
		}
		if key.Matches(msg, m.keys.PageUp) {
			return m.prevPage()
		}
		if key.Matches(msg, m.keys.PageDown) {
			return m.nextPage()
		}
		if key.Matches(msg, m.keys.Sort1) {
			return m.applySortBy(0)
		}
		if key.Matches(msg, m.keys.Sort2) {
			return m.applySortBy(1)
		}
		if key.Matches(msg, m.keys.Sort3) {
			return m.applySortBy(2)
		}
		if key.Matches(msg, m.keys.Sort4) {
			return m.applySortBy(3)
		}
		if key.Matches(msg, m.keys.SortToggle) {
			if m.sortCol >= 0 {
				m.sortAsc = !m.sortAsc
				m.applySort()
				m.currentPage = 0
				m.updateTableRows()
				m.table.SetCursor(0)
			}
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.table, cmd = m.table.Update(msg)
	return m, cmd
}

func (m *DataTableModel) applyFilter() {
	if m.searchQuery == "" {
		m.filteredItems = make([]any, len(m.items))
		copy(m.filteredItems, m.items)
	} else {
		m.filteredItems = nil
		kw := strings.ToLower(m.searchQuery)
		for _, item := range m.items {
			if m.formatter.searchMatch(item, kw) {
				m.filteredItems = append(m.filteredItems, item)
			}
		}
	}
	m.currentPage = 0
	m.applySort()
	m.updateTableRows()
	m.table.SetCursor(0)
	m.table.GotoTop()
}

func (m *DataTableModel) updateTableRows() {
	start := m.currentPage * m.pageSize
	end := start + m.pageSize
	if start > len(m.filteredItems) {
		start = len(m.filteredItems)
	}
	if end > len(m.filteredItems) {
		end = len(m.filteredItems)
	}
	if start > end {
		start = end
	}

	rows := make([]table.Row, end-start)
	for i, item := range m.filteredItems[start:end] {
		k := m.formatter.getKey(item)
		rows[i] = m.formatter.getRow(item, m.selected[k])
	}
	m.table.SetRows(rows)
}

func (m *DataTableModel) updateTableSize() {
	widths := m.formatter.calcWidths(m.width)
	cols := m.formatter.columns(widths)
	m.table.SetColumns(cols)

	fixed := 2 // title + status (header is inside table height)
	if m.searching {
		fixed++
	}
	helpLines := strings.Count(m.help.View(m.keys), "\n") + 1
	fixed += helpLines
	fixed += 2 // lipgloss border around table (top + bottom)

	h := m.height - fixed
	if h < 3 {
		h = 3
	}
	m.table.SetHeight(h)
	m.table.SetWidth(m.width - 2)
}

func (m *DataTableModel) cursorItemIndex() int {
	if len(m.filteredItems) == 0 {
		return -1
	}
	idx := m.currentPage*m.pageSize + m.table.Cursor()
	if idx < 0 || idx >= len(m.filteredItems) {
		return -1
	}
	return idx
}

func (m *DataTableModel) getKeyAtCursor() string {
	idx := m.cursorItemIndex()
	if idx < 0 {
		return ""
	}
	return m.formatter.getKey(m.filteredItems[idx])
}

func (m *DataTableModel) pageCount() int {
	if len(m.filteredItems) == 0 {
		return 1
	}
	return (len(m.filteredItems)-1)/m.pageSize + 1
}

func (m *DataTableModel) prevPage() (tea.Model, tea.Cmd) {
	if m.currentPage > 0 {
		m.currentPage--
		m.updateTableRows()
		m.table.SetCursor(0)
	}
	return m, nil
}

func (m *DataTableModel) nextPage() (tea.Model, tea.Cmd) {
	totalPages := m.pageCount()
	if m.currentPage < totalPages-1 {
		m.currentPage++
		m.updateTableRows()
		m.table.SetCursor(0)
	}
	return m, nil
}

func (m *DataTableModel) moveUp() (tea.Model, tea.Cmd) {
	if len(m.table.Rows()) == 0 {
		return m, nil
	}
	if m.table.Cursor() > 0 {
		m.table.MoveUp(1)
		return m, nil
	}
	if m.currentPage > 0 {
		m.currentPage--
		m.updateTableRows()
		m.table.SetCursor(len(m.table.Rows()) - 1)
	}
	return m, nil
}

func (m *DataTableModel) moveDown() (tea.Model, tea.Cmd) {
	if len(m.table.Rows()) == 0 {
		return m, nil
	}
	if m.table.Cursor() < len(m.table.Rows())-1 {
		m.table.MoveDown(1)
		return m, nil
	}
	totalPages := m.pageCount()
	if m.currentPage < totalPages-1 {
		m.currentPage++
		m.updateTableRows()
		m.table.SetCursor(0)
	}
	return m, nil
}

func (m *DataTableModel) applySort() {
	if m.sortCol < 0 || m.sortCol >= len(m.formatter.sortColumns) {
		return
	}
	col := m.formatter.sortColumns[m.sortCol]
	sort.SliceStable(m.filteredItems, func(i, j int) bool {
		if m.sortAsc {
			return col.less(m.filteredItems[i], m.filteredItems[j])
		}
		return col.less(m.filteredItems[j], m.filteredItems[i])
	})
}

func (m *DataTableModel) applySortBy(colIdx int) (tea.Model, tea.Cmd) {
	if colIdx < 0 || colIdx >= len(m.formatter.sortColumns) {
		return m, nil
	}
	if m.sortCol == colIdx {
		m.sortAsc = !m.sortAsc
	} else {
		m.sortCol = colIdx
		m.sortAsc = true
	}
	m.applySort()
	m.currentPage = 0
	m.updateTableRows()
	m.table.SetCursor(0)
	return m, nil
}

func (m *DataTableModel) doDelete(key string) tea.Cmd {
	rt := m.resourceType
	api := m.api
	botID := m.botID
	groupID := m.groupID
	return func() tea.Msg {
		var err error
		switch rt {
		case ResourceFacts:
			id, _ := strconv.Atoi(key)
			err = api.DeleteFact(botID, groupID, id)
		case ResourceProfiles:
			err = api.DeleteUserProfile(botID, groupID, key)
		case ResourceMemes:
			id, _ := strconv.Atoi(key)
			err = api.DeleteMeme(botID, groupID, id)
		case ResourceVocabularies:
			id, _ := strconv.Atoi(key)
			err = api.DeleteVocabulary(botID, groupID, id)
		case ResourceSummaries:
			id, _ := strconv.Atoi(key)
			err = api.DeleteSummary(botID, groupID, id)
		case ResourceHistory:
			id, _ := strconv.Atoi(key)
			err = api.DeleteHistory(botID, groupID, id)
		}
		return deleteDoneMsg{Error: err}
	}
}

func (m *DataTableModel) doBatchDelete() tea.Cmd {
	rt := m.resourceType
	api := m.api
	botID := m.botID
	groupID := m.groupID
	selected := make(map[string]bool)
	for k, v := range m.selected {
		selected[k] = v
	}
	return func() tea.Msg {
		var lastErr error
		deleted := 0
		for key := range selected {
			var err error
			switch rt {
			case ResourceFacts:
				id, _ := strconv.Atoi(key)
				err = api.DeleteFact(botID, groupID, id)
			case ResourceProfiles:
				err = api.DeleteUserProfile(botID, groupID, key)
			case ResourceMemes:
				id, _ := strconv.Atoi(key)
				err = api.DeleteMeme(botID, groupID, id)
			case ResourceVocabularies:
				id, _ := strconv.Atoi(key)
				err = api.DeleteVocabulary(botID, groupID, id)
			case ResourceSummaries:
				id, _ := strconv.Atoi(key)
				err = api.DeleteSummary(botID, groupID, id)
			case ResourceHistory:
				id, _ := strconv.Atoi(key)
				err = api.DeleteHistory(botID, groupID, id)
			}
			if err != nil {
				lastErr = err
			} else {
				deleted++
			}
		}
		return batchDeleteDoneMsg{Deleted: deleted, Error: lastErr}
	}
}

// ── View ──

func (m *DataTableModel) View() string {
	if m.loading {
		return "\n  " + style.Muted("Loading...")
	}
	if m.err != nil {
		return "\n  " + style.ErrorText("Error: "+m.err.Error()) + "\n\n  " + style.Muted("Press r to retry, ESC to go back")
	}
	if m.confirmForm != nil {
		view := m.confirmForm.View()
		lines := strings.Split(view, "\n")
		padTop := (m.height - len(lines)) / 2
		if padTop < 0 {
			padTop = 0
		}
		return strings.Repeat("\n", padTop) + view
	}

	if m.previewing {
		borderStyle := lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(style.Accent).
			Padding(0, 1)
		panel := borderStyle.Render(m.previewVP.View())

		helpView := m.help.View(previewHelpKeys)
		helpLines := strings.Count(helpView, "\n") + 1

		panelLines := strings.Split(panel, "\n")
		totalHeight := len(panelLines) + helpLines + 1
		padTop := (m.height - totalHeight) / 2
		if padTop < 0 {
			padTop = 0
		}
		padLeft := (m.width - m.previewVP.Width - 6) / 2
		if padLeft < 0 {
			padLeft = 0
		}

		leftPad := strings.Repeat(" ", padLeft)
		var result []string
		for i := 0; i < padTop; i++ {
			result = append(result, "")
		}
		for _, line := range panelLines {
			result = append(result, leftPad+line)
		}
		result = append(result, "")
		for _, line := range strings.Split(helpView, "\n") {
			result = append(result, leftPad+line)
		}
		return strings.Join(result, "\n")
	}

	var parts []string
	parts = append(parts, m.renderTitleBar())
	if m.searching {
		parts = append(parts, "  "+m.searchInput.View())
	}

	tableView := lipgloss.NewStyle().
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderColor).
		Render(m.table.View())
	parts = append(parts, tableView)

	parts = append(parts, m.renderStatusBar())
	parts = append(parts, m.help.View(m.keys))
	return strings.Join(parts, "\n")
}

func (m *DataTableModel) renderTitleBar() string {
	icon := m.formatter.title
	count := len(m.filteredItems)
	total := len(m.items)
	label := fmt.Sprintf("%s  \xe2\x80\x94  %d items", icon, count)
	if total != count {
		label = fmt.Sprintf("%s  \xe2\x80\x94  %d / %d items", icon, count, total)
	}
	if m.searching {
		label += "  " + SearchActiveStyle.Render(fmt.Sprintf("[Search: %s]", m.searchInput.Value()))
	}
	label = TruncateEnd(label, m.width-2)
	return TitleBarStyle.Width(m.width).Render(label)
}

func (m *DataTableModel) renderStatusBar() string {
	total := len(m.filteredItems)
	selectedInfo := fmt.Sprintf("%d selected", len(m.selected))

	var parts []string
	cursorIdx := m.cursorItemIndex()
	if cursorIdx >= 0 {
		parts = append(parts, fmt.Sprintf("%d/%d item", cursorIdx+1, total))
	} else {
		parts = append(parts, "0/0 item")
	}
	if total > m.pageSize {
		parts = append(parts, fmt.Sprintf("Page %d/%d", m.currentPage+1, m.pageCount()))
	}
	parts = append(parts, selectedInfo)
	if m.sortCol >= 0 && m.sortCol < len(m.formatter.sortColumns) {
		dir := "↑"
		if !m.sortAsc {
			dir = "↓"
		}
		parts = append(parts, fmt.Sprintf("Sort: %s %s", m.formatter.sortColumns[m.sortCol].name, dir))
	}
	if m.searchQuery != "" && !m.searching {
		parts = append(parts, "Filter: "+m.searchQuery)
	}

	text := "  " + strings.Join(parts, "  \xe2\x94\x82  ")
	text = TruncateEnd(text, m.width-2)
	return StatusBarStyle.Width(m.width).Render(text)
}

func (m *DataTableModel) enterPreview(item any) {
	m.previewItem = item
	m.previewing = true
	m.previewVP.SetContent(m.buildPreviewContent())
	m.previewVP.Width = m.width - 10
	if m.previewVP.Width < 20 {
		m.previewVP.Width = 20
	}
	m.previewVP.Height = m.height - 6
	if m.previewVP.Height < 5 {
		m.previewVP.Height = 5
	}
	m.previewVP.GotoTop()
}

func (m *DataTableModel) buildPreviewContent() string {
	panelWidth := m.width - 8
	if panelWidth > 80 {
		panelWidth = 80
	}
	if panelWidth < 30 {
		panelWidth = 30
	}

	innerWidth := panelWidth - 4

	wrap := func(label, value string) string {
		lines := wrapText(value, innerWidth-len(label)-2)
		var result []string
		for i, line := range lines {
			if i == 0 {
				result = append(result, fmt.Sprintf("%s: %s", label, line))
			} else {
				result = append(result, strings.Repeat(" ", len(label)+2)+line)
			}
		}
		return strings.Join(result, "\n")
	}

	ptrStr := func(s *string) string {
		if s == nil {
			return ""
		}
		return *s
	}

	var title string
	var contentLines []string

	switch r := m.previewItem.(type) {
	case FactRecord:
		title = r.Keyword
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("Keyword", r.Keyword),
			wrap("Description", r.Description),
			wrap("Values", r.Values),
			wrap("Subjects", r.Subjects),
			wrap("Scope", r.ScopeType),
			wrap("ValidFrom", r.ValidFrom),
			wrap("ValidTo", ptrStr(r.ValidTo)),
			wrap("Created", r.CreatedAt),
		}
	case UserProfileRecord:
		title = r.UserID
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("UserID", r.UserID),
			wrap("Profile", r.Profile),
			wrap("Preferences", r.Preferences),
			wrap("Created", r.CreatedAt),
		}
	case MemeRecord:
		title = fmt.Sprintf("Meme #%d", r.ID)
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("Md5", r.Md5),
			wrap("SeenCount", fmt.Sprintf("%d", r.SeenCount)),
			wrap("Description", ptrStr(r.Description)),
			wrap("Purpose", ptrStr(r.Purpose)),
			wrap("Tags", ptrStr(r.Tags)),
			wrap("UsageCount", fmt.Sprintf("%d", r.UsageCount)),
			wrap("Created", r.CreatedAt),
			wrap("Updated", r.UpdatedAt),
		}
	case VocabRecord:
		title = r.Word
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("Word", r.Word),
			wrap("Type", r.Type),
			wrap("Meaning", r.Meaning),
			wrap("Example", r.Example),
			wrap("Weight", fmt.Sprintf("%d", r.Weight)),
			wrap("LastSeen", r.LastSeen),
			wrap("Created", r.CreatedAt),
		}
	case SummaryRecord:
		title = r.TimeRange
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("TimeRange", r.TimeRange),
			wrap("Content", r.Content),
			wrap("KeyPoints", r.KeyPoints),
			wrap("EmotionalTone", ptrStr(r.EmotionalTone)),
			wrap("Participants", fmt.Sprintf("%d", r.ParticipantCount)),
			wrap("Messages", fmt.Sprintf("%d", r.MessageCount)),
			wrap("Created", r.CreatedAt),
		}
	case HistoryRecord:
		title = fmt.Sprintf("History #%d", r.ID)
		content := ""
		if r.Content != nil {
			content = *r.Content
		}
		resource := ""
		if r.Resource != nil {
			resource = fmt.Sprintf("%s (%s)", r.Resource.FileName, r.Resource.URL)
		}
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("UserID", r.UserID),
			wrap("Nick", r.Nick),
			wrap("Type", r.MessageType),
			wrap("Content", content),
			wrap("Resource", resource),
			wrap("Created", r.CreatedAt),
		}
	case ResourceRecord:
		title = r.FileName
		contentLines = []string{
			wrap("ID", fmt.Sprintf("%d", r.ID)),
			wrap("FileName", r.FileName),
			wrap("URL", r.URL),
			wrap("Size", fmt.Sprintf("%d", r.Size)),
			wrap("MD5", r.Md5),
			wrap("Created", r.CreatedAt),
		}
	default:
		return ""
	}

	content := strings.Join(contentLines, "\n")

	return style.Title(title) + "\n\n" + content + "\n"
}

func wrapText(text string, maxWidth int) []string {
	if maxWidth <= 0 {
		return []string{text}
	}
	wrapped := lipgloss.NewStyle().Width(maxWidth).Render(text)
	return strings.Split(wrapped, "\n")
}

// ── Formatter ──

type sortColumn struct {
	name string
	less func(a, b any) bool
}

type tableFormatter struct {
	title       string
	minWidths   []int
	columns     func(widths []int) []table.Column
	getRow      func(any, bool) table.Row
	getID       func(any) int
	getKey      func(any) string
	searchMatch func(any, string) bool
	canCreate   bool
	canEdit     bool
	canDelete   bool
	sortColumns []sortColumn
}

func (f tableFormatter) calcWidths(totalWidth int) []int {
	n := len(f.minWidths)
	totalCols := n + 1 // +checkbox

	avail := totalWidth - 2 - totalCols*2
	if avail < totalCols*3 {
		avail = totalCols * 3
	}

	minW := make([]int, totalCols)
	minW[0] = 3 // checkbox
	for i, w := range f.minWidths {
		minW[i+1] = w
	}

	widths := make([]int, totalCols)
	used := 0
	for i := range widths {
		widths[i] = minW[i]
		used += minW[i]
	}

	remaining := avail - used
	if remaining >= 0 {
		extra := remaining / totalCols
		remainder := remaining % totalCols
		for i := range widths {
			widths[i] += extra
		}
		for i := 0; i < remainder && i < totalCols; i++ {
			widths[i]++
		}
	} else {
		ratio := float64(avail) / float64(used)
		for i := range widths {
			widths[i] = int(float64(minW[i]) * ratio)
			if widths[i] < 3 {
				widths[i] = 3
			}
		}
	}
	return widths
}

func getFormatter(rt ResourceType) tableFormatter {
	switch rt {
	case ResourceFacts:
		return tableFormatter{
			title:     "Memory (Facts)",
			minWidths: []int{5, 12, 15, 10, 10, 6},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "Keyword", Width: widths[2]},
					{Title: "Description", Width: widths[3]},
					{Title: "Values", Width: widths[4]},
					{Title: "Subjects", Width: widths[5]},
					{Title: "Scope", Width: widths[6]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(FactRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.Keyword,
					r.Description,
					r.Values,
					r.Subjects,
					r.ScopeType,
				}
			},
			getID:  func(a any) int { return a.(FactRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(FactRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(FactRecord)
				return strings.Contains(strings.ToLower(r.Keyword), kw) ||
					strings.Contains(strings.ToLower(r.Description), kw) ||
					strings.Contains(strings.ToLower(r.Values), kw) ||
					strings.Contains(strings.ToLower(r.Subjects), kw) ||
					strings.Contains(strings.ToLower(r.ScopeType), kw)
			},
			canCreate: true,
			canEdit:   true,
			canDelete: true,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(FactRecord).ID < b.(FactRecord).ID }},
				{name: "Keyword", less: func(a, b any) bool { return strings.Compare(a.(FactRecord).Keyword, b.(FactRecord).Keyword) < 0 }},
				{name: "Scope", less: func(a, b any) bool { return strings.Compare(a.(FactRecord).ScopeType, b.(FactRecord).ScopeType) < 0 }},
			},
		}

	case ResourceProfiles:
		return tableFormatter{
			title:     "User Profiles",
			minWidths: []int{5, 12, 20, 20},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "UserID", Width: widths[2]},
					{Title: "Profile", Width: widths[3]},
					{Title: "Preferences", Width: widths[4]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(UserProfileRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.UserID,
					r.Profile,
					r.Preferences,
				}
			},
			getID:  func(a any) int { return a.(UserProfileRecord).ID },
			getKey: func(a any) string { return a.(UserProfileRecord).UserID },
			searchMatch: func(a any, kw string) bool {
				r := a.(UserProfileRecord)
				return strings.Contains(strings.ToLower(r.UserID), kw) ||
					strings.Contains(strings.ToLower(r.Profile), kw) ||
					strings.Contains(strings.ToLower(r.Preferences), kw)
			},
			canCreate: false,
			canEdit:   true,
			canDelete: true,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(UserProfileRecord).ID < b.(UserProfileRecord).ID }},
				{name: "UserID", less: func(a, b any) bool {
					return strings.Compare(a.(UserProfileRecord).UserID, b.(UserProfileRecord).UserID) < 0
				}},
			},
		}

	case ResourceMemes:
		return tableFormatter{
			title:     "Memes",
			minWidths: []int{5, 15, 10, 10, 6, 6},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "Description", Width: widths[2]},
					{Title: "Purpose", Width: widths[3]},
					{Title: "Tags", Width: widths[4]},
					{Title: "Seen", Width: widths[5]},
					{Title: "Usage", Width: widths[6]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(MemeRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				desc := ""
				if r.Description != nil {
					desc = *r.Description
				}
				purpose := ""
				if r.Purpose != nil {
					purpose = *r.Purpose
				}
				tags := ""
				if r.Tags != nil {
					tags = *r.Tags
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					desc,
					purpose,
					tags,
					fmt.Sprintf("%d", r.SeenCount),
					fmt.Sprintf("%d", r.UsageCount),
				}
			},
			getID:  func(a any) int { return a.(MemeRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(MemeRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(MemeRecord)
				if r.Description != nil && strings.Contains(strings.ToLower(*r.Description), kw) {
					return true
				}
				if r.Purpose != nil && strings.Contains(strings.ToLower(*r.Purpose), kw) {
					return true
				}
				if r.Tags != nil && strings.Contains(strings.ToLower(*r.Tags), kw) {
					return true
				}
				return strings.Contains(strings.ToLower(fmt.Sprintf("%d", r.ID)), kw)
			},
			canCreate: false,
			canEdit:   true,
			canDelete: true,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(MemeRecord).ID < b.(MemeRecord).ID }},
				{name: "Description", less: func(a, b any) bool {
					da, db := "", ""
					if a.(MemeRecord).Description != nil {
						da = *a.(MemeRecord).Description
					}
					if b.(MemeRecord).Description != nil {
						db = *b.(MemeRecord).Description
					}
					return strings.Compare(da, db) < 0
				}},
				{name: "Seen", less: func(a, b any) bool { return a.(MemeRecord).SeenCount < b.(MemeRecord).SeenCount }},
				{name: "Usage", less: func(a, b any) bool { return a.(MemeRecord).UsageCount < b.(MemeRecord).UsageCount }},
			},
		}

	case ResourceVocabularies:
		return tableFormatter{
			title:     "Vocabulary",
			minWidths: []int{5, 12, 8, 20, 6},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "Word", Width: widths[2]},
					{Title: "Type", Width: widths[3]},
					{Title: "Meaning", Width: widths[4]},
					{Title: "Weight", Width: widths[5]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(VocabRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.Word,
					r.Type,
					r.Meaning,
					fmt.Sprintf("%d", r.Weight),
				}
			},
			getID:  func(a any) int { return a.(VocabRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(VocabRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(VocabRecord)
				return strings.Contains(strings.ToLower(r.Word), kw) ||
					strings.Contains(strings.ToLower(r.Type), kw) ||
					strings.Contains(strings.ToLower(r.Meaning), kw) ||
					strings.Contains(strings.ToLower(r.Example), kw)
			},
			canCreate: true,
			canEdit:   true,
			canDelete: true,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(VocabRecord).ID < b.(VocabRecord).ID }},
				{name: "Word", less: func(a, b any) bool { return strings.Compare(a.(VocabRecord).Word, b.(VocabRecord).Word) < 0 }},
				{name: "Weight", less: func(a, b any) bool { return a.(VocabRecord).Weight < b.(VocabRecord).Weight }},
			},
		}

	case ResourceSummaries:
		return tableFormatter{
			title:     "Summaries",
			minWidths: []int{5, 12, 15, 15, 8, 8, 8},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "TimeRange", Width: widths[2]},
					{Title: "Content", Width: widths[3]},
					{Title: "KeyPoints", Width: widths[4]},
					{Title: "Tone", Width: widths[5]},
					{Title: "Participants", Width: widths[6]},
					{Title: "Messages", Width: widths[7]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(SummaryRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				tone := ""
				if r.EmotionalTone != nil {
					tone = *r.EmotionalTone
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.TimeRange,
					r.Content,
					r.KeyPoints,
					tone,
					fmt.Sprintf("%d", r.ParticipantCount),
					fmt.Sprintf("%d", r.MessageCount),
				}
			},
			getID:  func(a any) int { return a.(SummaryRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(SummaryRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(SummaryRecord)
				if strings.Contains(strings.ToLower(r.TimeRange), kw) {
					return true
				}
				if strings.Contains(strings.ToLower(r.Content), kw) {
					return true
				}
				if strings.Contains(strings.ToLower(r.KeyPoints), kw) {
					return true
				}
				if r.EmotionalTone != nil && strings.Contains(strings.ToLower(*r.EmotionalTone), kw) {
					return true
				}
				return false
			},
			canCreate: false,
			canEdit:   true,
			canDelete: true,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(SummaryRecord).ID < b.(SummaryRecord).ID }},
				{name: "TimeRange", less: func(a, b any) bool {
					return strings.Compare(a.(SummaryRecord).TimeRange, b.(SummaryRecord).TimeRange) < 0
				}},
				{name: "Participants", less: func(a, b any) bool { return a.(SummaryRecord).ParticipantCount < b.(SummaryRecord).ParticipantCount }},
				{name: "Messages", less: func(a, b any) bool { return a.(SummaryRecord).MessageCount < b.(SummaryRecord).MessageCount }},
			},
		}

	case ResourceHistory:
		return tableFormatter{
			title:     "History",
			minWidths: []int{8, 12, 10, 8, 15, 12, 16},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "UserID", Width: widths[2]},
					{Title: "Nick", Width: widths[3]},
					{Title: "Type", Width: widths[4]},
					{Title: "Content", Width: widths[5]},
					{Title: "Resource", Width: widths[6]},
					{Title: "Created", Width: widths[7]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(HistoryRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				content := ""
				if r.Content != nil {
					content = *r.Content
				}
				resource := ""
				if r.Resource != nil {
					resource = r.Resource.FileName
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.UserID,
					r.Nick,
					r.MessageType,
					TruncateEnd(content, 30),
					resource,
					r.CreatedAt,
				}
			},
			getID:  func(a any) int { return a.(HistoryRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(HistoryRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(HistoryRecord)
				if strings.Contains(strings.ToLower(r.UserID), kw) ||
					strings.Contains(strings.ToLower(r.Nick), kw) ||
					strings.Contains(strings.ToLower(r.MessageType), kw) {
					return true
				}
				if r.Content != nil && strings.Contains(strings.ToLower(*r.Content), kw) {
					return true
				}
				if r.Resource != nil && strings.Contains(strings.ToLower(r.Resource.FileName), kw) {
					return true
				}
				return false
			},
			canCreate: false,
			canEdit:   true,
			canDelete: true,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(HistoryRecord).ID < b.(HistoryRecord).ID }},
				{name: "Nick", less: func(a, b any) bool { return strings.Compare(a.(HistoryRecord).Nick, b.(HistoryRecord).Nick) < 0 }},
				{name: "Type", less: func(a, b any) bool {
					return strings.Compare(a.(HistoryRecord).MessageType, b.(HistoryRecord).MessageType) < 0
				}},
				{name: "Created", less: func(a, b any) bool {
					return strings.Compare(a.(HistoryRecord).CreatedAt, b.(HistoryRecord).CreatedAt) < 0
				}},
			},
		}

	case ResourceResource:
		return tableFormatter{
			title:     "Resources",
			minWidths: []int{5, 8, 20, 10, 12, 20},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "FileName", Width: widths[2]},
					{Title: "Size", Width: widths[3]},
					{Title: "MD5", Width: widths[4]},
					{Title: "URL", Width: widths[5]},
					{Title: "Created", Width: widths[6]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(ResourceRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.FileName,
					fmt.Sprintf("%d", r.Size),
					TruncateEnd(r.Md5, 12),
					TruncateMiddle(r.URL, 30),
					r.CreatedAt,
				}
			},
			getID:  func(a any) int { return a.(ResourceRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(ResourceRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(ResourceRecord)
				return strings.Contains(strings.ToLower(r.FileName), kw) ||
					strings.Contains(strings.ToLower(r.Md5), kw) ||
					strings.Contains(strings.ToLower(r.URL), kw)
			},
			canCreate: false,
			canEdit:   false,
			canDelete: false,
			sortColumns: []sortColumn{
				{name: "ID", less: func(a, b any) bool { return a.(ResourceRecord).ID < b.(ResourceRecord).ID }},
				{name: "FileName", less: func(a, b any) bool {
					return strings.Compare(a.(ResourceRecord).FileName, b.(ResourceRecord).FileName) < 0
				}},
				{name: "Size", less: func(a, b any) bool { return a.(ResourceRecord).Size < b.(ResourceRecord).Size }},
			},
		}
	}
	return tableFormatter{}
}

// ── Data loading ──

func loadResourceData(rt ResourceType, api *API, botID, groupID string) ([]any, error) {
	switch rt {
	case ResourceFacts:
		records, err := api.GetFacts(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceProfiles:
		records, err := api.GetUserProfiles(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceMemes:
		records, err := api.GetMemes(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceVocabularies:
		records, err := api.GetVocabularies(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceSummaries:
		records, err := api.GetSummaries(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceHistory:
		records, err := api.GetHistory(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceResource:
		records, err := api.GetResources(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	}
	return nil, fmt.Errorf("unknown resource type: %d", rt)
}
