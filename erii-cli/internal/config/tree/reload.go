package tree

import (
	"fmt"
	"os"
	"path/filepath"

	"erii-cli/internal/path"
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
		fmt.Println("\n=== Config Directory Merge ===")
		fmt.Println("  skipped (.update-conf/ not found)")
		return nil
	}

	fmt.Println("\n=== Config Directory Merge ===")

	metaResults, err := ReloadMetaDir(
		filepath.Join(updateDir, ".conf"),
		path.ConfMetaDir,
	)
	if err != nil {
		fmt.Printf("  Meta merge error: %v\n", err)
	} else {
		printFileResults(".conf", metaResults)
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
		printFileResults("rules/", confResult.RulesResults)
		printFileResults("souls/", confResult.SoulsResults)
	}

	fmt.Println("\nConfig directory merge completed.")
	return nil
}

func reloadMetadata() error {
	if err := LoadMetadata(path.ConfMetaDir); err != nil {
		return fmt.Errorf("metadata reload failed: %w", err)
	}
	fmt.Println("\nMetadata schemas reloaded successfully.")
	return nil
}

func printPluginSummary(summary *PluginInitSummary) {
	if summary == nil || len(summary.Results) == 0 {
		fmt.Println("No plugins found.")
		return
	}

	fmt.Println("\n=== Plugin Configuration Reload ===")

	var created, merged, skipped, errors int
	for _, r := range summary.Results {
		fmt.Printf("\n[%s]\n", r.PluginID)

		if r.Error != nil {
			fmt.Printf("  Error: %v\n", r.Error)
			errors++
			continue
		}

		printFileResult("plugin.json", r.ConfigResult)
		printFileResult("schema.json", r.SchemaResult)

		created, merged, skipped, errors = tally(
			created, merged, skipped, errors,
			r.ConfigResult.Action, r.SchemaResult.Action,
		)
	}

	fmt.Printf("\n=== Summary ===\n")
	fmt.Printf("Total: %d | Created: %d | Merged: %d | Skipped: %d | Errors: %d\n",
		len(summary.Results), created, merged, skipped, errors)
}

func tally(created, merged, skipped, errors int, actions ...string) (int, int, int, int) {
	for _, a := range actions {
		switch a {
		case "created":
			created++
		case "merged":
			merged++
		case "skipped":
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
		fmt.Printf("  %s: created (keys: %v)\n", label, r.AddedKeys)
	case "merged":
		fmt.Printf("  %s: merged (new keys: %v)\n", label, r.AddedKeys)
	case "skipped":
		fmt.Printf("  %s: skipped (up to date)\n", label)
	case "source_missing":
		fmt.Printf("  %s: skipped (source not found)\n", label)
	case "error":
		fmt.Printf("  %s: error - %v\n", label, r.Error)
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
	if errors > 0 {
		fmt.Printf("  %s: %d created, %d merged, %d skipped, %d errors\n", label, created, merged, skipped, errors)
	} else {
		fmt.Printf("  %s: %d created, %d merged, %d skipped\n", label, created, merged, skipped)
	}
}
