package manage

import (
	"erii-cli/internal/api"
	"erii-cli/internal/tui/components"

	tea "github.com/charmbracelet/bubbletea"
)

func NewBotListModel(client *api.Client) *components.BotListModel {
	return components.NewBotListModel(client, components.BotListConfig{
		EnterAction: func(idx int, bots []api.BotInfo) tea.Cmd {
			if idx >= 0 && idx < len(bots) {
				return func() tea.Msg { return PushGroupListMsg{Bot: bots[idx]} }
			}
			return nil
		},
	})
}

func NewGroupListModel(client *api.Client, bot api.BotInfo) *components.GroupListModel {
	return components.NewGroupListModel(client, bot, components.GroupListConfig{
		EnterAction: func(idx int, bot api.BotInfo, groups []api.GroupInfo) tea.Cmd {
			if idx >= 0 && idx < len(groups) {
				return func() tea.Msg {
					return PushManageMenuMsg{Bot: bot, Group: groups[idx]}
				}
			}
			return nil
		},
		BackAction:    func() tea.Cmd { return func() tea.Msg { return PopMsg{} } },
		ErrorCardHint: "press r to retry    esc back    q quit",
	})
}
