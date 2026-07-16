package tree

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"erii-cli/internal/path"
	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/lipgloss"
)

// Reload performs the full reload workflow:
// 1. Config directory merge, 2. Plugin configs, 3. Metadata schemas.
func Reload() error {
	var created, merged, skipped, errors int

	fmt.Println()
	fmt.Println(reloadTitleStyle.Render("Reload configuration") + " " + reloadStatusBadge("running"))

	if err := reloadConfigDirs(&created, &merged, &skipped, &errors); err != nil {
		return err
	}
	if err := reloadPlugins(&created, &merged, &skipped, &errors); err != nil {
		return err
	}
	if err := reloadMetadata(); err != nil {
		return err
	}

	total := created + merged + skipped + errors
	status := "ok"
	if errors > 0 {
		status = "error"
	}
	fmt.Println()
	fmt.Println(reloadTitleStyle.Render("Reload result") + " " + reloadStatusBadge(status))
	fmt.Println(reloadSectionStyle.Render("Summary"))
	fmt.Print(reloadRow("Total", fmt.Sprintf("%d files", total)))
	fmt.Print(reloadRow("Created", fmt.Sprintf("%d files", created)))
	fmt.Print(reloadRow("Merged", fmt.Sprintf("%d files", merged)))
	fmt.Print(reloadRow("Skipped", fmt.Sprintf("%d files", skipped)))
	fmt.Print(reloadRow("Errors", fmt.Sprintf("%d files", errors)))

	return nil
}

func reloadPlugins(created, merged, skipped, errors *int) error {
	summary, err := InitializePluginConfigs(
		path.PluginDir,
		path.PluginConfigDir,
		path.PluginSchemaDir,
	)
	if err != nil {
		return fmt.Errorf("plugin initialization failed: %w", err)
	}
	printPluginSummary(summary, created, merged, skipped, errors)
	return nil
}

func reloadConfigDirs(created, merged, skipped, errors *int) error {
	updateDir := FindUpdateConfDir()
	if _, err := os.Stat(updateDir); os.IsNotExist(err) {
		fmt.Println()
		fmt.Println(reloadSectionStyle.Render("Config directory merge"))
		fmt.Print(reloadRow("Status", "skipped"))
		fmt.Print(reloadRow("Reason", ".update-conf/ not found"))
		return nil
	}

	fmt.Println()
	fmt.Println(reloadSectionStyle.Render("Config directory merge"))

	metaResults, err := ReloadMetaDir(
		filepath.Join(updateDir, ".conf"),
		path.ConfMetaDir,
	)
	if err != nil {
		fmt.Print(reloadRow("Meta", "error: "+err.Error()))
	}

	confResult, err := ReloadConfDirs(
		filepath.Join(updateDir, "conf"),
		path.ConfDir,
	)
	if err != nil {
		fmt.Print(reloadRow("Conf", "error: "+err.Error()))
	} else {
		printFileResult("application.conf", confResult.AppConfResult)
		printFileResult(".env.local", confResult.EnvResult)
	}

	printFileResults(".conf", metaResults)
	if confResult != nil {
		printFileResults("rules/", confResult.RulesResults)
		printFileResults("souls/", confResult.SoulsResults)
	}

	var allResults []FileMergeResult
	if metaResults != nil {
		allResults = append(allResults, metaResults...)
	}
	if confResult != nil {
		allResults = append(allResults, confResult.AppConfResult)
		allResults = append(allResults, confResult.EnvResult)
		allResults = append(allResults, confResult.RulesResults...)
		allResults = append(allResults, confResult.SoulsResults...)
	}

	for _, r := range allResults {
		*created, *merged, *skipped, *errors = tally(*created, *merged, *skipped, *errors, r.Action)
	}

	fmt.Print(reloadRow("Status", "completed"))
	return nil
}

func reloadMetadata() error {
	if err := LoadMetadata(path.ConfMetaDir); err != nil {
		return fmt.Errorf("metadata reload failed: %w", err)
	}
	fmt.Println()
	fmt.Println(reloadSectionStyle.Render("Metadata schemas"))
	fmt.Print(reloadRow("Status", "reloaded"))
	return nil
}

var (
	reloadTitleStyle = lipgloss.NewStyle().
				Foreground(style.Primary).
				Bold(true)
	reloadMutedStyle = lipgloss.NewStyle().
				Foreground(style.TextMuted)
	reloadLabelStyle = lipgloss.NewStyle().
				Foreground(style.TextMuted).
				Width(14)
	reloadValueStyle = lipgloss.NewStyle().
				Foreground(style.Text).
				Bold(true)
	reloadSectionStyle = lipgloss.NewStyle().
				Foreground(style.Secondary).
				Bold(true)
	reloadPluginStyle = lipgloss.NewStyle().
				Foreground(style.Info).
				Bold(true)
	reloadOkBadge = lipgloss.NewStyle().
			Foreground(style.Background).
			Background(style.Success).
			Bold(true).
			Padding(0, 1)
	reloadErrorBadge = lipgloss.NewStyle().
				Foreground(style.Background).
				Background(style.Error).
				Bold(true).
				Padding(0, 1)
	reloadWarningBadge = lipgloss.NewStyle().
				Foreground(style.Background).
				Background(style.Warning).
				Bold(true).
				Padding(0, 1)
	reloadCreatedStyle = lipgloss.NewStyle().Foreground(style.Success).Bold(true)
	reloadMergedStyle  = lipgloss.NewStyle().Foreground(style.Info).Bold(true)
	reloadSkippedStyle = lipgloss.NewStyle().Foreground(style.Warning).Bold(true)
	reloadErrorStyle   = lipgloss.NewStyle().Foreground(style.Error).Bold(true)
)

func printPluginSummary(summary *PluginInitSummary, created, merged, skipped, errors *int) {
	if summary == nil || len(summary.Results) == 0 {
		return
	}

	fmt.Println()
	fmt.Println(reloadSectionStyle.Render("Plugin configuration reload"))

	for _, r := range summary.Results {
		fmt.Println()
		fmt.Println("  " + reloadPluginStyle.Render(r.PluginID))

		if r.Error != nil {
			fmt.Print(reloadIndentedRow("Error", r.Error.Error()))
			*errors++
			continue
		}

		printFileResult("plugin.json", r.ConfigResult)
		printFileResult("schema.json", r.SchemaResult)

		*created, *merged, *skipped, *errors = tally(
			*created, *merged, *skipped, *errors,
			r.ConfigResult.Action, r.SchemaResult.Action,
		)
	}
}

func tally(created, merged, skipped, errors int, actions ...string) (int, int, int, int) {
	for _, a := range actions {
		switch a {
		case "created":
			created++
		case "merged":
			merged++
		case "skipped", "source_missing":
			skipped++
		case "error":
			errors++
		}
	}
	return created, merged, skipped, errors
}

func printFileResult(label string, r FileMergeResult) {
	switch r.Action {
	case "created":
		fmt.Print(reloadIndentedRow(label, reloadCreatedStyle.Render("created")+reloadKeysSuffix("keys", r.AddedKeys)))
	case "merged":
		fmt.Print(reloadIndentedRow(label, reloadMergedStyle.Render("merged")+reloadKeysSuffix("new keys", r.AddedKeys)))
	case "skipped":
		fmt.Print(reloadIndentedRow(label, reloadSkippedStyle.Render("skipped")+" "+reloadMutedStyle.Render("up to date")))
	case "source_missing":
		fmt.Print(reloadIndentedRow(label, reloadSkippedStyle.Render("skipped")+" "+reloadMutedStyle.Render("source not found")))
	case "error":
		fmt.Print(reloadIndentedRow(label, reloadErrorStyle.Render("error")+" "+r.Error.Error()))
	}
}

func printFileResults(label string, results []FileMergeResult) {
	if len(results) == 0 {
		return
	}
	var created, merged, skipped, errors int
	for _, r := range results {
		created, merged, skipped, errors = tally(created, merged, skipped, errors, r.Action)
	}
	parts := []string{
		reloadCreatedStyle.Render("created") + " " + fmt.Sprint(created),
		reloadMergedStyle.Render("merged") + " " + fmt.Sprint(merged),
		reloadSkippedStyle.Render("skipped") + " " + fmt.Sprint(skipped),
	}
	if errors > 0 {
		parts = append(parts, reloadErrorStyle.Render("errors")+" "+fmt.Sprint(errors))
	}
	fmt.Print(reloadIndentedRow(label, strings.Join(parts, ", ")))
}

func reloadStatusBadge(status string) string {
	label := strings.ToUpper(status)
	switch strings.ToLower(status) {
	case "ok", "success", "completed":
		return reloadOkBadge.Render(label)
	case "error", "failed":
		return reloadErrorBadge.Render(label)
	default:
		return reloadWarningBadge.Render(label)
	}
}

func reloadRow(label, value string) string {
	return "  " + reloadLabelStyle.Render(label) + reloadValueStyle.Render(value) + "\n"
}

func reloadIndentedRow(label, value string) string {
	return "    " + reloadLabelStyle.Render(label) + value + "\n"
}

func reloadKeysSuffix(label string, keys []string) string {
	if len(keys) == 0 {
		return ""
	}
	return " " + reloadMutedStyle.Render(fmt.Sprintf("%s: %s", label, strings.Join(keys, ", ")))
}
