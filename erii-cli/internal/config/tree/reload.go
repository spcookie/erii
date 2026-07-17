package tree

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"

	"erii-cli/internal/path"
	uioutput "erii-cli/internal/ui/output"
)

// Reload performs the full reload workflow:
// 1. Config directory merge, 2. Plugin configs, 3. Metadata schemas.
func Reload(w io.Writer) error {
	var created, merged, skipped, errors int

	fmt.Fprintln(w)
	fmt.Fprintln(w, uioutput.Title("Reload configuration")+"  "+uioutput.Status("running"))

	if err := reloadConfigDirs(w, &created, &merged, &skipped, &errors); err != nil {
		return err
	}
	if err := reloadPlugins(w, &created, &merged, &skipped, &errors); err != nil {
		return err
	}
	if err := reloadMetadata(w); err != nil {
		return err
	}

	total := created + merged + skipped + errors
	status := "ok"
	if errors > 0 {
		status = "error"
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w, uioutput.Title("Reload result")+"  "+uioutput.Status(status))
	fmt.Fprintln(w, uioutput.Section("Summary"))
	fmt.Fprint(w, reloadRow("Total", fmt.Sprintf("%d files", total)))
	fmt.Fprint(w, reloadRow("Created", fmt.Sprintf("%d files", created)))
	fmt.Fprint(w, reloadRow("Merged", fmt.Sprintf("%d files", merged)))
	fmt.Fprint(w, reloadRow("Skipped", fmt.Sprintf("%d files", skipped)))
	fmt.Fprint(w, reloadRow("Errors", fmt.Sprintf("%d files", errors)))

	return nil
}

func reloadPlugins(w io.Writer, created, merged, skipped, errors *int) error {
	summary, err := InitializePluginConfigs(
		path.PluginDir,
		path.PluginConfigDir,
		path.PluginSchemaDir,
	)
	if err != nil {
		return fmt.Errorf("plugin initialization failed: %w", err)
	}
	printPluginSummary(w, summary, created, merged, skipped, errors)
	return nil
}

func reloadConfigDirs(w io.Writer, created, merged, skipped, errors *int) error {
	updateDir := FindUpdateConfDir()
	if _, err := os.Stat(updateDir); os.IsNotExist(err) {
		fmt.Fprintln(w)
		fmt.Fprintln(w, uioutput.Section("Config directory merge"))
		fmt.Fprint(w, reloadRow("Status", "skipped"))
		fmt.Fprint(w, reloadRow("Reason", ".update-conf/ not found"))
		return nil
	}

	fmt.Fprintln(w)
	fmt.Fprintln(w, uioutput.Section("Config directory merge"))

	metaResults, err := ReloadMetaDir(
		filepath.Join(updateDir, ".conf"),
		path.ConfMetaDir,
	)
	if err != nil {
		fmt.Fprint(w, reloadRow("Meta", "error: "+err.Error()))
	}

	confResult, err := ReloadConfDirs(
		filepath.Join(updateDir, "conf"),
		path.ConfDir,
	)
	if err != nil {
		fmt.Fprint(w, reloadRow("Conf", "error: "+err.Error()))
	} else {
		printFileResult(w, "application.conf", confResult.AppConfResult)
		printFileResult(w, ".env.local", confResult.EnvResult)
	}

	printFileResults(w, ".conf", metaResults)
	if confResult != nil {
		printFileResults(w, "rules/", confResult.RulesResults)
		printFileResults(w, "souls/", confResult.SoulsResults)
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

	fmt.Fprint(w, reloadRow("Status", "completed"))
	return nil
}

func reloadMetadata(w io.Writer) error {
	if err := LoadMetadata(path.ConfMetaDir); err != nil {
		return fmt.Errorf("metadata reload failed: %w", err)
	}
	fmt.Fprintln(w)
	fmt.Fprintln(w, uioutput.Section("Metadata schemas"))
	fmt.Fprint(w, reloadRow("Status", "reloaded"))
	return nil
}

func printPluginSummary(w io.Writer, summary *PluginInitSummary, created, merged, skipped, errors *int) {
	if summary == nil || len(summary.Results) == 0 {
		return
	}

	fmt.Fprintln(w)
	fmt.Fprintln(w, uioutput.Section("Plugin configuration reload"))

	for _, r := range summary.Results {
		fmt.Fprintln(w)
		fmt.Fprintln(w, "  "+uioutput.Plugin(r.PluginID))

		if r.Error != nil {
			fmt.Fprint(w, reloadIndentedRow("Error", r.Error.Error()))
			*errors++
			continue
		}

		printFileResult(w, "plugin.json", r.ConfigResult)
		printFileResult(w, "schema.json", r.SchemaResult)

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

func printFileResult(w io.Writer, label string, r FileMergeResult) {
	switch r.Action {
	case "created":
		fmt.Fprint(w, reloadIndentedRow(label, uioutput.Success("created")+reloadKeysSuffix("keys", r.AddedKeys)))
	case "merged":
		fmt.Fprint(w, reloadIndentedRow(label, uioutput.Plugin("merged")+reloadKeysSuffix("new keys", r.AddedKeys)))
	case "skipped":
		fmt.Fprint(w, reloadIndentedRow(label, uioutput.Warning("skipped")+" "+uioutput.Muted("up to date")))
	case "source_missing":
		fmt.Fprint(w, reloadIndentedRow(label, uioutput.Warning("skipped")+" "+uioutput.Muted("source not found")))
	case "error":
		fmt.Fprint(w, reloadIndentedRow(label, uioutput.Error("error")+" "+r.Error.Error()))
	}
}

func printFileResults(w io.Writer, label string, results []FileMergeResult) {
	if len(results) == 0 {
		return
	}
	var created, merged, skipped, errors int
	for _, r := range results {
		created, merged, skipped, errors = tally(created, merged, skipped, errors, r.Action)
	}
	parts := []string{
		uioutput.Success("created") + " " + fmt.Sprint(created),
		uioutput.Plugin("merged") + " " + fmt.Sprint(merged),
		uioutput.Warning("skipped") + " " + fmt.Sprint(skipped),
	}
	if errors > 0 {
		parts = append(parts, uioutput.Error("errors")+" "+fmt.Sprint(errors))
	}
	fmt.Fprint(w, reloadIndentedRow(label, strings.Join(parts, ", ")))
}

func reloadStatusBadge(status string) string {
	return uioutput.Status(status)
}

func reloadRow(label, value string) string {
	return uioutput.Row(label, value)
}

func reloadIndentedRow(label, value string) string {
	return uioutput.IndentedRow(label, value)
}

func reloadKeysSuffix(label string, keys []string) string {
	if len(keys) == 0 {
		return ""
	}
	return " " + uioutput.Muted(fmt.Sprintf("%s: %s", label, strings.Join(keys, ", ")))
}
