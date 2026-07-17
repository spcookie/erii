package cmd

import (
	"erii-cli/internal/path"
	"erii-cli/internal/ui/theme"
	"erii-cli/internal/web"
	"os"

	"github.com/spf13/cobra"
)

var (
	webPort  string
	webHost  string
	webToken string
)

var webCmd = &cobra.Command{
	Use:   "web",
	Short: "Start web console with xterm.js terminal",
	Long: `Start a web server that provides remote access to Erii TUI
via xterm.js in the browser.

The server listens on localhost by default and generates a random
access token. Use --host 0.0.0.0 to expose it on the network.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		token := webToken
		if token == "" {
			token = web.GenerateToken()
		}

		// Find the erii binary.
		eriiBin, _ := os.Executable()

		cfg := web.Config{
			Port:        webPort,
			Host:        webHost,
			Token:       token,
			EriiBin:     eriiBin,
			ConfDir:     path.ConfDir,
			MetaConfDir: path.ConfMetaDir,
			EriiDir:     path.EriiDir,
			PluginDir:   path.PluginDir,
			OptsPath:    path.OptsPath,
			Theme:       string(theme.Requested()),
			Output:      cmd.OutOrStdout(),
		}

		return web.Start(cfg)
	},
}

func init() {
	webCmd.Flags().StringVar(&webPort, "port", "9527", "HTTP listen port")
	webCmd.Flags().StringVar(&webHost, "host", "127.0.0.1", "HTTP listen host")
	webCmd.Flags().StringVar(&webToken, "token", "", "Custom access token (default: random generated)")
	rootCmd.AddCommand(webCmd)
}
