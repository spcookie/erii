package cmd

import (
	"fmt"

	"erii-cli/internal/config/setter"
	"erii-cli/internal/path"

	"github.com/spf13/cobra"
)

var configAppCmd = &cobra.Command{
	Use:   "app",
	Short: "Manage application.conf (HOCON format)",
	Long:  `Get or set values in the HOCON application.conf file.`,
}

var configEnvCmd = &cobra.Command{
	Use:   "env",
	Short: "Manage .env.local environment variables",
	Long:  `Get or set values in the .env.local environment file.`,
}

var configPluginCmd = &cobra.Command{
	Use:   "plugin",
	Short: "Manage plugin JSON config files",
	Long:  `Get or set values in plugin JSON config files.`,
}

// --- app set ---
var configAppSetCmd = &cobra.Command{
	Use:   "set <key> <value>",
	Short: "Set a value in application.conf",
	Long: `Set a key to a value in the HOCON application.conf file.
Key supports dot-separated paths (e.g. llm.providers.openai.api-key).
Value auto-detects type: true/false/yes/no → boolean, null → null,
numbers → numeric, [a,b] → array, ${X} → substitution.`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		if err := setter.HoconSet(path.AppFile, args[0], args[1]); err != nil {
			return fmt.Errorf("config app set: %w", err)
		}
		fmt.Printf("Set %s = %s in application.conf\n", args[0], args[1])
		return nil
	},
}

// --- app get ---
var configAppGetCmd = &cobra.Command{
	Use:   "get <key>",
	Short: "Get a value from application.conf",
	Long:  `Get the value of a key from the HOCON application.conf file.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		val, err := setter.HoconGet(path.AppFile, args[0])
		if err != nil {
			return fmt.Errorf("config app get: %w", err)
		}
		fmt.Println(val)
		return nil
	},
}

// --- env set ---
var configEnvSetCmd = &cobra.Command{
	Use:   "set <key> <value>",
	Short: "Set a value in .env.local",
	Long: `Set a key to a value in the .env.local environment file.
Key must be a single environment variable name (no dots).`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		if err := setter.EnvSet(path.EnvFile, args[0], args[1]); err != nil {
			return fmt.Errorf("config env set: %w", err)
		}
		fmt.Printf("Set %s = %s in .env.local\n", args[0], args[1])
		return nil
	},
}

// --- env get ---
var configEnvGetCmd = &cobra.Command{
	Use:   "get <key>",
	Short: "Get a value from .env.local",
	Long:  `Get the value of a key from the .env.local environment file.`,
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		val, err := setter.EnvGet(path.EnvFile, args[0])
		if err != nil {
			return fmt.Errorf("config env get: %w", err)
		}
		fmt.Println(val)
		return nil
	},
}

// --- plugin set ---
var configPluginSetCmd = &cobra.Command{
	Use:   "set <plugin> <key> <value>",
	Short: "Set a value in plugin config",
	Long: `Set a key to a value in the plugin's JSON config file.
Key supports dot-separated paths for nested values.
Value supports the same auto-detection as app config.`,
	Args: cobra.ExactArgs(3),
	RunE: func(cmd *cobra.Command, args []string) error {
		pluginFile := path.PluginConfigDir + "/" + args[0] + ".json"
		if err := setter.JSONSet(pluginFile, args[1], args[2]); err != nil {
			return fmt.Errorf("config plugin set: %w", err)
		}
		fmt.Printf("Set %s = %s in plugin %s\n", args[1], args[2], args[0])
		return nil
	},
}

// --- plugin get ---
var configPluginGetCmd = &cobra.Command{
	Use:   "get <plugin> <key>",
	Short: "Get a value from plugin config",
	Long:  `Get the value of a key from the plugin's JSON config file.`,
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		pluginFile := path.PluginConfigDir + "/" + args[0] + ".json"
		val, err := setter.JSONGet(pluginFile, args[1])
		if err != nil {
			return fmt.Errorf("config plugin get: %w", err)
		}
		fmt.Println(val)
		return nil
	},
}

func init() {
	configAppCmd.AddCommand(configAppSetCmd)
	configAppCmd.AddCommand(configAppGetCmd)
	configEnvCmd.AddCommand(configEnvSetCmd)
	configEnvCmd.AddCommand(configEnvGetCmd)
	configPluginCmd.AddCommand(configPluginSetCmd)
	configPluginCmd.AddCommand(configPluginGetCmd)
	configCmd.AddCommand(configAppCmd)
	configCmd.AddCommand(configEnvCmd)
	configCmd.AddCommand(configPluginCmd)
}
