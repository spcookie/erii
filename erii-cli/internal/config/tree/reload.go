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
	if err := reloadConfigDirs(); err != nil {
		return err
	}
	if err := reloadPlugins(); err != nil {
		return err
	}
	return reloadMetadata()
}

func reloadPlugins() error {
	summary, err := InitializePluginConfigs(
		path.PluginDir,
		path.PluginConfigDir,
		path.PluginSchemaDir,
	)
	if err != nil {
		return fmt.Errorf("plugin initialization failed: %w", err)
	}
	printPluginSummary(summary)
	return nil
}

func reloadConfigDirs() error {
	updateDir := FindUpdateConfDir()
	if _, err := os.Stat(updateDir); os.IsNotExist(err) {
		fmt.Println("\n" + sectionStyle.Render("=== Config Directory Merge ==="))
		fmt.Println("  " + skippedStyle.Render("skipped") + " (.update-conf/ not found)")
		return nil
	}

	fmt.Println("\n" + sectionStyle.Render("=== Config Directory Merge ==="))

	metaResults, err := ReloadMetaDir(
		filepath.Join(updateDir, ".conf"),
		path.ConfMetaDir,
	)
	if err != nil {
		fmt.Printf("  Meta merge error: %v\n", err)
	}

	confResult, err := ReloadConfDirs(
		filepath.Join(updateDir, "conf"),
		path.ConfDir,
	)
	if err != nil {
		fmt.Printf("  Conf merge error: %v\n", err)
	} else {
		printFileResult("application.conf", confResult.AppConfResult)
		printFileResult(".env.local", confResult.EnvResult)
	}

	// Collect all results for summary
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

	var created, merged, skipped, errors int
	for _, r := range allResults {
		created, merged, skipped, errors = tally(created, merged, skipped, errors, r.Action)
	}

	printSummary(created+merged+skipped+errors, created, merged, skipped, errors)

	fmt.Println("\n" + createdStyle.Render("✓") + " Config directory merge completed.")
	return nil
}

func reloadMetadata() error {
	if err := LoadMetadata(path.ConfMetaDir); err != nil {
		return fmt.Errorf("metadata reload failed: %w", err)
	}
	fmt.Println("\n" + createdStyle.Render("✓") + " Metadata schemas reloaded successfully.")
	return nil
}

var (
	sectionStyle = lipgloss.NewStyle().Foreground(style.Primary).Bold(true)
	pluginStyle  = lipgloss.NewStyle().Foreground(style.Secondary).Bold(true)
	createdStyle = lipgloss.NewStyle().Foreground(style.Success)
	mergedStyle  = lipgloss.NewStyle().Foreground(style.Info)
	skippedStyle = lipgloss.NewStyle().Foreground(style.Warning)
	errorStyle   = lipgloss.NewStyle().Foreground(style.Error).Bold(true)
	mutedStyle   = lipgloss.NewStyle().Foreground(style.TextMuted)
)

func printPluginSummary(summary *PluginInitSummary) {
	if summary == nil || len(summary.Results) == 0 {
		fmt.Println("No plugins found.")
		return
	}

	fmt.Println("\n" + sectionStyle.Render("=== Plugin Configuration Reload ==="))

	var fileCreated, fileMerged, fileSkipped, fileErrors int
	for _, r := range summary.Results {
		fmt.Printf("\n%s\n", pluginStyle.Render("["+r.PluginID+"]"))

		if r.Error != nil {
			fmt.Printf("  %s: %v\n", errorStyle.Render("Error"), r.Error)
			fileErrors++
			continue
		}

		printFileResult("plugin.json", r.ConfigResult)
		printFileResult("schema.json", r.SchemaResult)

		fileCreated, fileMerged, fileSkipped, fileErrors = tally(
			fileCreated, fileMerged, fileSkipped, fileErrors,
			r.ConfigResult.Action, r.SchemaResult.Action,
		)
	}

	printSummary(fileCreated+fileMerged+fileSkipped+fileErrors, fileCreated, fileMerged, fileSkipped, fileErrors)
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
		fmt.Printf("  %s: %s (keys: %v)\n", label, createdStyle.Render("created"), r.AddedKeys)
	case "merged":
		fmt.Printf("  %s: %s (new keys: %v)\n", label, mergedStyle.Render("merged"), r.AddedKeys)
	case "skipped":
		fmt.Printf("  %s: %s (up to date)\n", label, skippedStyle.Render("skipped"))
	case "source_missing":
		fmt.Printf("  %s: %s (%s)\n", label, skippedStyle.Render("skipped"), mutedStyle.Render("source not found"))
	case "error":
		fmt.Printf("  %s: %s - %v\n", label, errorStyle.Render("error"), r.Error)
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
		createdStyle.Render("created") + " " + fmt.Sprint(created),
		mergedStyle.Render("merged") + " " + fmt.Sprint(merged),
		skippedStyle.Render("skipped") + " " + fmt.Sprint(skipped),
	}
	if errors > 0 {
		parts = append(parts, errorStyle.Render("errors")+" "+fmt.Sprint(errors))
	}
	fmt.Printf("  %s: %s\n", label, strings.Join(parts, ", "))
}

func printSummary(total, created, merged, skipped, errors int) {
	fmt.Printf("\n%s\n", sectionStyle.Render("=== Summary ==="))
	fmt.Printf("Total: %d | %s: %d | %s: %d | %s: %d | %s: %d\n",
		total,
		createdStyle.Render("Created"), created,
		mergedStyle.Render("Merged"), merged,
		skippedStyle.Render("Skipped"), skipped,
		errorStyle.Render("Errors"), errors,
	)
}
