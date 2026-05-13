package cmd

import (
	"fmt"

	"erii-cli/internal/config/tree"
	"erii-cli/internal/path"

	"github.com/spf13/cobra"
)

var reloadCmd = &cobra.Command{
	Use:   "reload",
	Short: "Reload plugin configurations and metadata schemas",
	Long: `Re-initializes plugin configuration files using deep JSON merge logic.
Existing user-modified values are preserved. New keys from updated
plugin archives are added. Metadata schemas are reloaded afterward.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		summary, err := tree.InitializePluginConfigs(
			path.PluginDir,
			path.PluginConfigDir,
			path.PluginSchemaDir,
		)
		if err != nil {
			return fmt.Errorf("plugin initialization failed: %w", err)
		}

		printReloadSummary(summary)

		if err := tree.LoadMetadata(path.ConfMetaDir); err != nil {
			return fmt.Errorf("metadata reload failed: %w", err)
		}

		fmt.Println("\nMetadata schemas reloaded successfully.")
		return nil
	},
}

func init() {
	rootCmd.AddCommand(reloadCmd)
}

func printReloadSummary(summary *tree.PluginInitSummary) {
	if summary == nil || len(summary.Results) == 0 {
		fmt.Println("No plugins found.")
		return
	}

	var created, merged, skipped, errors int

	fmt.Println("\n=== Plugin Configuration Reload ===")

	for _, r := range summary.Results {
		fmt.Printf("\n[%s] (%s)\n", r.PluginID, r.PluginZip)

		if r.Error != nil {
			fmt.Printf("  Error: %v\n", r.Error)
			errors++
			continue
		}

		printFileResult("plugin.json", &r.ConfigResult)
		printFileResult("schema.json", &r.SchemaResult)

		switch r.ConfigResult.Action {
		case "created":
			created++
		case "merged":
			merged++
		case "skipped":
			skipped++
		case "error":
			errors++
		}
		switch r.SchemaResult.Action {
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

	fmt.Printf("\n=== Summary ===\n")
	fmt.Printf("Total: %d | Created: %d | Merged: %d | Skipped: %d | Errors: %d\n",
		len(summary.Results), created, merged, skipped, errors)
}

func printFileResult(label string, r *tree.FileMergeResult) {
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
