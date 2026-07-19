package cmd

import (
	"errors"
	"fmt"
	"strings"

	uioutput "erii-cli/internal/ui/output"

	"github.com/spf13/cobra"
)

// commandLineError marks errors caused by invoking a command incorrectly.
// Cobra does not expose a common error type for flag and argument validation,
// so the CLI adds one at the framework boundaries it controls.
type commandLineError struct {
	err error
}

func (e *commandLineError) Error() string { return e.err.Error() }
func (e *commandLineError) Unwrap() error { return e.err }

func asCommandLineError(err error) error {
	if err == nil {
		return nil
	}
	var cliErr *commandLineError
	if errors.As(err, &cliErr) {
		return err
	}
	return &commandLineError{err: err}
}

func commandLineErrorf(format string, args ...any) error {
	return asCommandLineError(fmt.Errorf(format, args...))
}

func markCommandLineErrors(cmd *cobra.Command) {
	cmd.SetFlagErrorFunc(func(_ *cobra.Command, err error) error {
		return asCommandLineError(err)
	})

	var wrapArgs func(*cobra.Command)
	wrapArgs = func(current *cobra.Command) {
		if current.Args != nil {
			validate := current.Args
			current.Args = func(cmd *cobra.Command, args []string) error {
				return asCommandLineError(validate(cmd, args))
			}
		}
		for _, child := range current.Commands() {
			wrapArgs(child)
		}
	}
	wrapArgs(cmd)
}

func printExecutionError(cmd *cobra.Command, err error) {
	if cmd == nil {
		cmd = rootCmd
	}

	cliError, findError := classifyCommandLineError(err)
	if cliError {
		root := cmd.Root()
		root.PrintErrln(cmd.ErrPrefix(), err.Error())
		if findError {
			root.PrintErrf("Run '%v --help' for usage.\n", cmd.CommandPath())
		} else {
			root.Println(cmd.UsageString())
		}
		return
	}

	_ = configureTheme(cmd)
	fmt.Fprint(cmd.ErrOrStderr(), uioutput.ErrorResult("Erii CLI", "Command", err))
}

func classifyCommandLineError(err error) (isCommandLine, isFindError bool) {
	var cliErr *commandLineError
	if errors.As(err, &cliErr) {
		return true, false
	}

	message := err.Error()
	if strings.HasPrefix(message, "unknown command ") {
		// Unknown commands are rejected by Command.Find before flag/Args
		// validation runs. Cobra's default output uses the short help hint.
		return true, true
	}

	// Required flags and flag groups are validated internally by Cobra after
	// PreRunE, where Cobra provides no hook for attaching a custom error type.
	cobraValidationPrefixes := []string{
		"required flag(s) ",
		"if any flags in the group ",
		"at least one of the flags in the group ",
	}
	for _, prefix := range cobraValidationPrefixes {
		if strings.HasPrefix(message, prefix) {
			return true, false
		}
	}
	return false, false
}
