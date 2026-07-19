package cmd

import (
	"bytes"
	"errors"
	"strings"
	"testing"

	"github.com/spf13/cobra"
)

func TestFlagParseErrorUsesCobraOutput(t *testing.T) {
	root := &cobra.Command{Use: "test", SilenceErrors: true, SilenceUsage: true}
	root.Flags().Int("count", 0, "item count")
	root.RunE = func(cmd *cobra.Command, args []string) error { return nil }

	stdout, stderr, err := executeTestCommand(root, "--count", "nope")
	if err == nil {
		t.Fatal("expected flag parse error")
	}
	assertCobraErrorOutput(t, stdout, stderr, "invalid argument")
}

func TestMissingArgsUsesCobraOutput(t *testing.T) {
	root := &cobra.Command{
		Use:           "test VALUE",
		Args:          cobra.ExactArgs(1),
		SilenceErrors: true,
		SilenceUsage:  true,
		RunE:          func(cmd *cobra.Command, args []string) error { return nil },
	}

	stdout, stderr, err := executeTestCommand(root)
	if err == nil {
		t.Fatal("expected argument validation error")
	}
	assertCobraErrorOutput(t, stdout, stderr, "accepts 1 arg(s), received 0")
}

func TestMissingRequiredOptionUsesCobraOutput(t *testing.T) {
	root := &cobra.Command{
		Use:           "test",
		SilenceErrors: true,
		SilenceUsage:  true,
		RunE:          func(cmd *cobra.Command, args []string) error { return nil },
	}
	root.Flags().String("token", "", "access token")
	if err := root.MarkFlagRequired("token"); err != nil {
		t.Fatalf("mark required flag: %v", err)
	}

	stdout, stderr, err := executeTestCommand(root)
	if err == nil {
		t.Fatal("expected required option error")
	}
	assertCobraErrorOutput(t, stdout, stderr, `required flag(s) "token" not set`)
}

func TestOptionValidationUsesCobraOutput(t *testing.T) {
	root := &cobra.Command{
		Use:           "test",
		SilenceErrors: true,
		SilenceUsage:  true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return commandLineErrorf("invalid format %q, valid: text, json", "yaml")
		},
	}

	stdout, stderr, err := executeTestCommand(root)
	if err == nil {
		t.Fatal("expected option validation error")
	}
	assertCobraErrorOutput(t, stdout, stderr, `invalid format "yaml"`)
}

func TestBusinessErrorUsesErrorResult(t *testing.T) {
	root := &cobra.Command{
		Use:           "test",
		SilenceErrors: true,
		SilenceUsage:  true,
		RunE: func(cmd *cobra.Command, args []string) error {
			return errors.New("backend unavailable")
		},
	}

	stdout, stderr, err := executeTestCommand(root)
	if err == nil {
		t.Fatal("expected business error")
	}
	if strings.Contains(stdout, "Usage:") || strings.Contains(stderr, "Usage:") {
		t.Fatalf("business error unexpectedly rendered Cobra usage:\nstdout: %s\nstderr: %s", stdout, stderr)
	}
	for _, want := range []string{"Erii CLI", "Command", "backend unavailable"} {
		if !strings.Contains(stderr, want) {
			t.Fatalf("styled business error missing %q: %s", want, stderr)
		}
	}
}

func TestUnknownCommandUsesCobraHelpHint(t *testing.T) {
	root := &cobra.Command{Use: "test", SilenceErrors: true, SilenceUsage: true}
	root.AddCommand(&cobra.Command{Use: "known", Run: func(cmd *cobra.Command, args []string) {}})

	stdout, stderr, err := executeTestCommand(root, "unknown")
	if err == nil {
		t.Fatal("expected unknown command error")
	}
	if stdout != "" {
		t.Fatalf("unknown command unexpectedly wrote stdout: %s", stdout)
	}
	for _, want := range []string{`Error: unknown command "unknown" for "test"`, "Run 'test --help' for usage."} {
		if !strings.Contains(stderr, want) {
			t.Fatalf("unknown command output missing %q: %s", want, stderr)
		}
	}
	if strings.Contains(stderr, "Erii CLI") {
		t.Fatalf("unknown command used business error renderer: %s", stderr)
	}
}

func executeTestCommand(root *cobra.Command, args ...string) (stdout, stderr string, err error) {
	var out bytes.Buffer
	var errOut bytes.Buffer
	root.SetOut(&out)
	root.SetErr(&errOut)
	root.SetArgs(args)
	markCommandLineErrors(root)

	executed, err := root.ExecuteC()
	if err != nil {
		printExecutionError(executed, err)
	}
	return out.String(), errOut.String(), err
}

func assertCobraErrorOutput(t *testing.T, stdout, stderr, message string) {
	t.Helper()
	if !strings.Contains(stderr, "Error: "+message) {
		t.Fatalf("Cobra error missing %q:\nstdout: %s\nstderr: %s", message, stdout, stderr)
	}
	if !strings.Contains(stdout, "Usage:") {
		t.Fatalf("Cobra usage missing:\nstdout: %s\nstderr: %s", stdout, stderr)
	}
	if strings.Contains(stdout, "Erii CLI") || strings.Contains(stderr, "Erii CLI") {
		t.Fatalf("command-line error used business renderer:\nstdout: %s\nstderr: %s", stdout, stderr)
	}
}
