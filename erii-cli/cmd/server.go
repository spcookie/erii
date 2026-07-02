package cmd

import (
	"bufio"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"erii-cli/internal/path"

	"github.com/spf13/cobra"
)

var serverCmd = &cobra.Command{
	Use:                "server",
	Short:              "Manage the Erii backend Java server",
	DisableFlagParsing: true,
	Long: `Manage the Erii backend Java server.

Subcommands:
  start     Start the server (default)
  stop      Stop a running server
  status    Show server status
  logs      View server logs
  restart   Restart the server
`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return runServerStart()
	},
}

var serverStartCmd = &cobra.Command{
	Use:                "start",
	Short:              "Start the Erii backend server",
	DisableFlagParsing: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		return runServerStart()
	},
}

var serverStopCmd = &cobra.Command{
	Use:   "stop",
	Short: "Stop the running Erii backend server",
	RunE: func(cmd *cobra.Command, args []string) error {
		return runServerStop()
	},
}

var serverStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show Erii backend server status",
	RunE: func(cmd *cobra.Command, args []string) error {
		return runServerStatus()
	},
}

var serverLogsCmd = &cobra.Command{
	Use:   "logs",
	Short: "View Erii backend server logs",
	RunE: func(cmd *cobra.Command, args []string) error {
		follow, _ := cmd.Flags().GetBool("follow")
			lines, _ := cmd.Flags().GetInt("lines")
			return runServerLogs(follow, lines)
	},
}

var serverRestartCmd = &cobra.Command{
	Use:                "restart",
	Short:              "Restart the Erii backend server",
	DisableFlagParsing: true,
	RunE: func(cmd *cobra.Command, args []string) error {
		return runServerRestart()
	},
}

func init() {
	serverCmd.AddCommand(serverStartCmd)
	serverCmd.AddCommand(serverStopCmd)
	serverCmd.AddCommand(serverStatusCmd)
	serverLogsCmd.Flags().BoolP("follow", "f", true, "Follow log output (like tail -f)")
	serverLogsCmd.Flags().IntP("lines", "n", 50, "Number of lines to show from the end")
	serverCmd.AddCommand(serverLogsCmd)
	serverCmd.AddCommand(serverRestartCmd)
	rootCmd.AddCommand(serverCmd)
}

// ---- path helpers ----

func projectRoot() string {
	if path.ConfDir != "" {
		return filepath.Dir(path.ConfDir)
	}
	cwd, _ := os.Getwd()
	return cwd
}

func pidFilePath() string {
	if path.ConfMetaDir != "" {
		return filepath.Join(path.ConfMetaDir, "erii.pid")
	}
	return filepath.Join(projectRoot(), ".conf", "erii.pid")
}

// ---- Java finding ----

func findJava() (string, error) {
	javaBin := "java"
	if runtime.GOOS == "windows" {
		javaBin = "java.exe"
	}

	runtimeJava := filepath.Join(projectRoot(), "runtime", "bin", javaBin)
	if _, err := os.Stat(runtimeJava); err == nil {
		return runtimeJava, nil
	}

	if jh := os.Getenv("JAVA_HOME"); jh != "" {
		homeJava := filepath.Join(jh, "bin", javaBin)
		if _, err := os.Stat(homeJava); err == nil {
			return homeJava, nil
		}
	}

	return exec.LookPath("java")
}

// ---- classpath ----

func buildClasspath(root string) string {
	libDir := filepath.Join(root, "lib")
	parts := []string{
		filepath.Join(libDir, "browser", "base", "*"),
		filepath.Join(libDir, "browser", "driver", "*"),
		filepath.Join(libDir, "core", "*"),
		filepath.Join(libDir, "deps", "*"),
	}
	return strings.Join(parts, string(filepath.ListSeparator))
}

// ---- opts reading ----

func readOpts(filename string) ([]string, error) {
	file, err := os.Open(filename)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	defer file.Close()

	var lines []string
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		lines = append(lines, line)
	}
	return lines, scanner.Err()
}

// ---- env loading ----

func loadServerEnv() []string {
	env := os.Environ()

	envFile := filepath.Join(path.ConfDir, ".env.local")
	if f, err := os.Open(envFile); err == nil {
		defer f.Close()
		scanner := bufio.NewScanner(f)
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if k, v, ok := parseEnvLine(line); ok {
				env = append(env, k+"="+v)
			}
		}
	}

	envOptsFile := filepath.Join(path.OptsPath, "env.opts")
	if f, err := os.Open(envOptsFile); err == nil {
		defer f.Close()
		scanner := bufio.NewScanner(f)
		for scanner.Scan() {
			line := strings.TrimSpace(scanner.Text())
			if k, v, ok := parseEnvLine(line); ok {
				env = append(env, k+"="+v)
			}
		}
	}

	return env
}

func parseEnvLine(line string) (string, string, bool) {
	idx := strings.IndexByte(line, '=')
	if idx <= 0 {
		return "", "", false
	}
	key := strings.TrimSpace(line[:idx])
	if !isEnvVarKey(key) {
		return "", "", false
	}
	val := strings.TrimSpace(line[idx+1:])
	return key, val, true
}

func isEnvVarKey(s string) bool {
	if len(s) == 0 {
		return false
	}
	for i := 0; i < len(s); i++ {
		c := s[i]
		if !((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
			return false
		}
	}
	return (s[0] >= 'A' && s[0] <= 'Z') || s[0] == '_'
}

// ---- passthrough args ----

// passthroughArgs extracts args after "server <subcommand>" from os.Args.
// Returns the filtered args and whether --foreground/-f was set.
func passthroughArgs(subcommand string) ([]string, bool) {
	state := 0 // 0=before server, 1=after server, 2=after subcommand
	var args []string
	foreground := false
	for _, arg := range os.Args[1:] {
		if state == 0 {
			if arg == "server" {
				state = 1
			}
		} else if state == 1 {
			if subcommand != "" && arg == subcommand {
				state = 2
			} else if subcommand == "" {
				// No subcommand expected — collect remaining args directly
				state = 2
				if arg == "--foreground" || arg == "-f" {
					foreground = true
				} else {
					args = append(args, arg)
				}
			}
		} else {
			if arg == "--foreground" || arg == "-f" {
				foreground = true
			} else {
				args = append(args, arg)
			}
		}
	}
	return args, foreground
}

// ---- Java args building ----

func buildJavaArgs(passthrough []string) ([]string, error) {
	javaOpts, err := readOpts(filepath.Join(path.OptsPath, "java.opts"))
	if err != nil {
		return nil, fmt.Errorf("reading java.opts: %w", err)
	}
	eriiCoreOpts, err := readOpts(filepath.Join(path.OptsPath, "erii-core.opts"))
	if err != nil {
		return nil, fmt.Errorf("reading erii-core.opts: %w", err)
	}

	var userSystemProps []string
	var userProgramArgs []string
	for _, arg := range passthrough {
		if strings.HasPrefix(arg, "-D") {
			userSystemProps = append(userSystemProps, arg)
		} else {
			userProgramArgs = append(userProgramArgs, arg)
		}
	}

	userPropKeys := make(map[string]bool)
	for _, arg := range userSystemProps {
		if idx := strings.IndexByte(arg, '='); idx > 0 {
			userPropKeys[arg[:idx]] = true
		}
	}
	var filteredCoreOpts []string
	for _, arg := range eriiCoreOpts {
		if strings.HasPrefix(arg, "-D") {
			key := arg
			if idx := strings.IndexByte(arg, '='); idx > 0 {
				key = arg[:idx]
			}
			if userPropKeys[key] {
				continue
			}
		}
		filteredCoreOpts = append(filteredCoreOpts, arg)
	}

	cp := buildClasspath(projectRoot())

	all := make([]string, 0, len(javaOpts)+len(userSystemProps)+len(filteredCoreOpts)+len(userProgramArgs)+3)
	all = append(all, javaOpts...)
	all = append(all, userSystemProps...)
	all = append(all, filteredCoreOpts...)
	all = append(all, "-cp", cp)
	all = append(all, "io.ktor.server.netty.EngineMain")
	all = append(all, userProgramArgs...)

	return all, nil
}

// ---- PID file management ----

func readPidFile() (int, error) {
	data, err := os.ReadFile(pidFilePath())
	if err != nil {
		if os.IsNotExist(err) {
			return 0, nil
		}
		return 0, err
	}
	pidStr := strings.TrimSpace(string(data))
	if pidStr == "" {
		return 0, nil
	}
	return strconv.Atoi(pidStr)
}

func writePidFile(pid int) error {
	if err := os.MkdirAll(filepath.Dir(pidFilePath()), 0755); err != nil {
		return err
	}
	return os.WriteFile(pidFilePath(), []byte(strconv.Itoa(pid)), 0644)
}

func removePidFile() {
	os.Remove(pidFilePath())
}

// ---- process management ----

func stopServer(pid int) error {
	if err := killProcess(pid); err != nil {
		return err
	}
	for i := 0; i < 50; i++ {
		if !isProcessRunning(pid) {
			removePidFile()
			return nil
		}
		time.Sleep(100 * time.Millisecond)
	}
	return fmt.Errorf("timeout waiting for server to stop")
}

func spawnDaemon(javaBin string, args []string, env []string) (int, error) {
	cmd := exec.Command(javaBin, args...)
	cmd.Dir = projectRoot()
	cmd.Env = env
	cmd.Stdin = nil
	cmd.Stdout = io.Discard
	cmd.Stderr = io.Discard
	configureDaemonProcess(cmd)

	if err := cmd.Start(); err != nil {
		return 0, err
	}
	return cmd.Process.Pid, nil
}

// ---- command implementations ----

func runServerStart() error {
	javaBin, err := findJava()
	if err != nil {
		return fmt.Errorf("Java not found. Install Java 17+ or set JAVA_HOME: %w", err)
	}

	passthrough, foreground := passthroughArgs("start")
	args, err := buildJavaArgs(passthrough)
	if err != nil {
		return err
	}
	env := loadServerEnv()

	if foreground {
		cmd := exec.Command(javaBin, args...)
		cmd.Dir = projectRoot()
		cmd.Env = env
		cmd.Stdin = os.Stdin
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr

		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, os.Interrupt)
		go func() {
			for sig := range sigCh {
				if cmd.Process != nil {
					cmd.Process.Signal(sig)
				}
			}
		}()

		if err := cmd.Run(); err != nil {
			if exitErr, ok := err.(*exec.ExitError); ok {
				os.Exit(exitErr.ExitCode())
			}
			return err
		}
		return nil
	}

	return daemonStartOrRestart(javaBin, args, env)
}

func daemonStartOrRestart(javaBin string, args []string, env []string) error {
	existingPid, err := readPidFile()
	if err != nil {
		return fmt.Errorf("reading PID file: %w", err)
	}

	if existingPid > 0 && isProcessRunning(existingPid) {
		fmt.Printf("Server is already running (PID: %d). Restarting...\n", existingPid)
		if err := stopServer(existingPid); err != nil {
			fmt.Println("Failed to stop old server. You may need to kill it manually.")
			os.Exit(1)
		}
		fmt.Println("Server stopped. Starting server...")
	} else if existingPid > 0 {
		removePidFile()
	}

	pid, err := spawnDaemon(javaBin, args, env)
	if err != nil {
		return fmt.Errorf("starting server: %w", err)
	}
	if err := writePidFile(pid); err != nil {
		return fmt.Errorf("writing PID file: %w", err)
	}
	fmt.Printf("Server started in background. PID: %d\n", pid)
	return nil
}

func runServerStop() error {
	pid, err := readPidFile()
	if err != nil {
		return fmt.Errorf("reading PID file: %w", err)
	}
	if pid == 0 {
		fmt.Println("No PID file found. Server is not running in daemon mode.")
		return nil
	}
	if !isProcessRunning(pid) {
		fmt.Printf("Process %d is not running. Cleaning up PID file.\n", pid)
		removePidFile()
		return nil
	}

	fmt.Printf("Stopping server (PID: %d)...\n", pid)
	if err := stopServer(pid); err != nil {
		fmt.Println("Server did not stop gracefully. You may need to kill it manually.")
		os.Exit(1)
	}
	fmt.Println("Server stopped.")
	return nil
}

func runServerStatus() error {
	pid, err := readPidFile()
	if err != nil {
		return fmt.Errorf("reading PID file: %w", err)
	}
	if pid == 0 {
		fmt.Println("Server is not running (no PID file).")
		return nil
	}
	if isProcessRunning(pid) {
		fmt.Printf("Server is running. PID: %d\n", pid)
		return nil
	}
	fmt.Printf("Server is not running (stale PID file: %d).\n", pid)
	os.Exit(1)
	return nil
}

func runServerLogs(follow bool, numLines int) error {
	logFile := findLogFile()
	if logFile == "" {
		fmt.Println("No log files found. Java manages its own logging.")
		return nil
	}

	f, err := os.Open(logFile)
	if err != nil {
		return fmt.Errorf("opening log file %s: %w", logFile, err)
	}
	defer f.Close()

	if err := printLastLines(f, numLines); err != nil {
		return err
	}

	if !follow {
		return nil
	}

	// tail -f: follow new lines
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, os.Interrupt)
	defer signal.Stop(sigCh)

	for {
		select {
		case <-sigCh:
			return nil
		default:
		}

		buf := make([]byte, 4096)
		n, err := f.Read(buf)
		if n > 0 {
			os.Stdout.Write(buf[:n])
		}
		if err != nil {
			if err == io.EOF {
				time.Sleep(200 * time.Millisecond)
				continue
			}
			return fmt.Errorf("reading log file: %w", err)
		}
	}
}

func findLogFile() string {
	logDir := filepath.Join(projectRoot(), "logs")
	entries, err := os.ReadDir(logDir)
	if err != nil {
		return ""
	}

	var best string
	var bestTime time.Time
	for _, entry := range entries {
		if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".log") {
			continue
		}
		info, err := entry.Info()
		if err != nil {
			continue
		}
		// Prefer the most recently modified log file
		if info.ModTime().After(bestTime) {
			bestTime = info.ModTime()
			best = entry.Name()
		}
	}
	if best != "" {
		fmt.Printf("  => %s\n\n", best)
		return filepath.Join(logDir, best)
	}
	return ""
}

func printLastLines(f *os.File, n int) error {
	stat, err := f.Stat()
	if err != nil {
		return err
	}

	// For small files or n=0, just read from the beginning
	if n <= 0 || stat.Size() < 8192 {
		_, err := io.Copy(os.Stdout, f)
		return err
	}

	// Read the last ~8KB and find the nth line from the end
	chunkSize := int64(8192)
	if stat.Size() < chunkSize {
		chunkSize = stat.Size()
	}
	buf := make([]byte, chunkSize)
	_, err = f.ReadAt(buf, stat.Size()-chunkSize)
	if err != nil && err != io.EOF {
		return err
	}

	// Count backwards to find the start of the nth last line
	start := len(buf)
	lineCount := 0
	for i := len(buf) - 1; i >= 0; i-- {
		if buf[i] == '\n' {
			lineCount++
			if lineCount > n {
				start = i + 1
				break
			}
		}
	}

	os.Stdout.Write(buf[start:])
	return nil
}

func runServerRestart() error {
	javaBin, err := findJava()
	if err != nil {
		return fmt.Errorf("Java not found. Install Java 17+ or set JAVA_HOME: %w", err)
	}

	passthrough, _ := passthroughArgs("restart")
	args, err := buildJavaArgs(passthrough)
	if err != nil {
		return err
	}
	env := loadServerEnv()

	return daemonStartOrRestart(javaBin, args, env)
}
