package cmd

import (
	"erii-cli/internal/tui/chatui"

	"github.com/spf13/cobra"
)

var chatCmd = &cobra.Command{
	Use:   "chat",
	Short: "Chat with Erii through the CLI",
	Long:  `Open an interactive chat TUI to talk with Erii bot via a mock OneBot bridge.`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return chatui.StartWithImageConfig(chatImageConfig(cmd))
	},
}

func chatImageConfig(cmd *cobra.Command) chatui.ImageConfig {
	cfg := chatui.LoadImageConfigFromEnv()
	flags := cmd.Root().PersistentFlags()
	if flags.Changed("chat-image-max-cols") {
		cfg = cfg.WithMaxCols(chatImageMaxColsFlag)
	}
	if flags.Changed("chat-image-max-rows") {
		cfg = cfg.WithMaxRows(chatImageMaxRowsFlag)
	}
	if flags.Changed("chat-image-fit") {
		cfg = cfg.WithFit(chatImageFitFlag)
	}
	if flags.Changed("chat-image-background") {
		cfg = cfg.WithBackground(chatImageBackgroundFlag)
	}
	if flags.Changed("chat-image-mode") {
		cfg = cfg.WithMode(chatImageModeFlag)
	}
	return cfg
}

func init() {
	rootCmd.AddCommand(chatCmd)
}
