package manage

import (
	"fmt"
	"strconv"
	"strings"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	"github.com/charmbracelet/bubbles/table"
	"github.com/charmbracelet/bubbles/textinput"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

// Messages

type dataLoadedMsg struct {
	Items []any
	Error error
}

type deleteDoneMsg struct {
	Error error
}

type batchDeleteDoneMsg struct {
	Deleted int
	Error   error
}

// DataTableModel

type DataTableModel struct {
	resourceType ResourceType
	botID        string
	groupID      string
	api          *API

	items         []any
	filteredItems []any
	selected      map[string]bool

	searchInput textinput.Model
	searching   bool
	searchQuery string

	loading bool
	err     error

	confirmingDelete      bool
	confirmingBatchDelete bool

	table     table.Model
	formatter tableFormatter
	width     int
	height    int
	keys      tableKeys
	help      help.Model
}

func NewDataTableModel(api *API, rt ResourceType, bot BotInfo, group GroupInfo) *DataTableModel {
	ti := textinput.New()
	ti.Placeholder = "Search..."
	ti.PromptStyle = lipgloss.NewStyle().Foreground(style.Accent)

	t := table.New()
	s := table.DefaultStyles()
	s.Header = s.Header.
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderColor).
		BorderBottom(true).
		Bold(true).
		Foreground(style.Primary)
	s.Cell = s.Cell.
		Padding(0, 1).
		Foreground(style.Text)
	s.Selected = s.Selected.
		Background(style.Surface).
		Foreground(style.Primary).
		Bold(true)
	t.SetStyles(s)

	km := table.DefaultKeyMap()
	km.PageDown = key.NewBinding(key.WithKeys("f", "pgdown"))
	t.KeyMap = km

	return &DataTableModel{
		resourceType: rt,
		botID:        bot.BotID,
		groupID:      group.GroupID,
		api:          api,
		selected:     make(map[string]bool),
		searchInput:  ti,
		table:        t,
		formatter:    getFormatter(rt),
		keys:         defaultTableKeys,
		help:         help.New(),
		loading:      true,
	}
}

func (m *DataTableModel) Init() tea.Cmd {
	return func() tea.Msg {
		items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
		return dataLoadedMsg{Items: items, Error: err}
	}
}

func (m *DataTableModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		m.updateTableSize()
		return m, nil

	case dataLoadedMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		m.items = msg.Items
		m.applyFilter()
		return m, nil

	case deleteDoneMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
			return m, nil
		}
		return m, func() tea.Msg {
			items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
			return dataLoadedMsg{Items: items, Error: err}
		}

	case batchDeleteDoneMsg:
		m.loading = false
		if msg.Error != nil {
			m.err = msg.Error
		}
		m.selected = make(map[string]bool)
		return m, func() tea.Msg {
			items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
			return dataLoadedMsg{Items: items, Error: err}
		}

	case RefreshMsg:
		m.loading = true
		m.err = nil
		return m, func() tea.Msg {
			items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
			return dataLoadedMsg{Items: items, Error: err}
		}

	case tea.KeyMsg:
		if key.Matches(msg, m.keys.Quit) {
			return m, tea.Quit
		}

		if m.confirmingDelete || m.confirmingBatchDelete {
			switch msg.Type {
			case tea.KeyEnter:
				if m.confirmingDelete {
					m.confirmingDelete = false
					key := m.getKeyAtCursor()
					return m, m.doDelete(key)
				}
				if m.confirmingBatchDelete {
					m.confirmingBatchDelete = false
					return m, m.doBatchDelete()
				}
			case tea.KeyEsc:
				m.confirmingDelete = false
				m.confirmingBatchDelete = false
				return m, nil
			}
			return m, nil
		}

		if m.searching {
			switch msg.Type {
			case tea.KeyEsc:
				m.searching = false
				m.searchInput.Blur()
				m.searchQuery = ""
				m.applyFilter()
				m.updateTableSize()
				return m, nil
			case tea.KeyEnter:
				m.searching = false
				m.searchInput.Blur()
				m.searchQuery = m.searchInput.Value()
				m.applyFilter()
				m.updateTableSize()
				return m, nil
			default:
				var cmd tea.Cmd
				m.searchInput, cmd = m.searchInput.Update(msg)
				m.searchQuery = m.searchInput.Value()
				m.applyFilter()
				return m, cmd
			}
		}

		if key.Matches(msg, m.keys.Help) {
			m.help.ShowAll = !m.help.ShowAll
			m.updateTableSize()
			return m, nil
		}
		if key.Matches(msg, m.keys.Back) {
			return m, func() tea.Msg { return PopMsg{} }
		}
		if key.Matches(msg, m.keys.Search) {
			m.searching = true
			m.searchInput.Focus()
			m.updateTableSize()
			return m, textinput.Blink
		}
		if key.Matches(msg, m.keys.Refresh) {
			m.loading = true
			m.err = nil
			return m, func() tea.Msg {
				items, err := loadResourceData(m.resourceType, m.api, m.botID, m.groupID)
				return dataLoadedMsg{Items: items, Error: err}
			}
		}
		if key.Matches(msg, m.keys.New) && m.formatter.canCreate {
			return m, func() tea.Msg {
				return PushEditMsg{
					ResourceType: m.resourceType,
					Bot:          BotInfo{BotID: m.botID},
					Group:        GroupInfo{GroupID: m.groupID},
					Data:         nil,
					IsCreate:     true,
				}
			}
		}
		if key.Matches(msg, m.keys.Enter) {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			idx := m.table.Cursor()
			if idx >= 0 && idx < len(m.filteredItems) {
				item := m.filteredItems[idx]
				return m, func() tea.Msg {
					return PushEditMsg{
						ResourceType: m.resourceType,
						Bot:          BotInfo{BotID: m.botID},
						Group:        GroupInfo{GroupID: m.groupID},
						Data:         item,
						IsCreate:     false,
					}
				}
			}
		}
		if key.Matches(msg, m.keys.Select) {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			idx := m.table.Cursor()
			if idx >= 0 && idx < len(m.filteredItems) {
				item := m.filteredItems[idx]
				k := m.formatter.getKey(item)
				if m.selected[k] {
					delete(m.selected, k)
				} else {
					m.selected[k] = true
				}
				m.updateTableRows()
				return m, nil
			}
		}
		if key.Matches(msg, m.keys.Delete) {
			if len(m.filteredItems) == 0 {
				return m, nil
			}
			m.confirmingDelete = true
			return m, nil
		}
		if key.Matches(msg, m.keys.BatchDel) {
			if len(m.selected) == 0 {
				return m, nil
			}
			m.confirmingBatchDelete = true
			return m, nil
		}
	}

	var cmd tea.Cmd
	m.table, cmd = m.table.Update(msg)
	return m, cmd
}

func (m *DataTableModel) applyFilter() {
	if m.searchQuery == "" {
		m.filteredItems = make([]any, len(m.items))
		copy(m.filteredItems, m.items)
	} else {
		m.filteredItems = nil
		kw := strings.ToLower(m.searchQuery)
		for _, item := range m.items {
			if m.formatter.searchMatch(item, kw) {
				m.filteredItems = append(m.filteredItems, item)
			}
		}
	}
	m.updateTableRows()
	m.table.SetCursor(0)
	m.table.GotoTop()
}

func (m *DataTableModel) updateTableRows() {
	rows := make([]table.Row, len(m.filteredItems))
	for i, item := range m.filteredItems {
		k := m.formatter.getKey(item)
		rows[i] = m.formatter.getRow(item, m.selected[k])
	}
	m.table.SetRows(rows)
}

func (m *DataTableModel) updateTableSize() {
	widths := m.formatter.calcWidths(m.width)
	cols := m.formatter.columns(widths)
	m.table.SetColumns(cols)

	fixed := 4 // title + header + status + help short
	if m.searching {
		fixed++
	}
	if m.help.ShowAll {
		fixed += 2
	}
	h := m.height - fixed
	if h < 3 {
		h = 3
	}
	m.table.SetHeight(h)
	m.table.SetWidth(m.width - 2)
}

func (m *DataTableModel) getKeyAtCursor() string {
	idx := m.table.Cursor()
	if idx < 0 || idx >= len(m.filteredItems) {
		return ""
	}
	return m.formatter.getKey(m.filteredItems[idx])
}

func (m *DataTableModel) doDelete(key string) tea.Cmd {
	rt := m.resourceType
	api := m.api
	botID := m.botID
	groupID := m.groupID
	return func() tea.Msg {
		var err error
		switch rt {
		case ResourceFacts:
			id, _ := strconv.Atoi(key)
			err = api.DeleteFact(botID, groupID, id)
		case ResourceProfiles:
			err = api.DeleteUserProfile(botID, groupID, key)
		case ResourceMemes:
			id, _ := strconv.Atoi(key)
			err = api.DeleteMeme(botID, groupID, id)
		case ResourceVocabularies:
			id, _ := strconv.Atoi(key)
			err = api.DeleteVocabulary(botID, groupID, id)
		case ResourceSummaries:
			id, _ := strconv.Atoi(key)
			err = api.DeleteSummary(botID, groupID, id)
		}
		return deleteDoneMsg{Error: err}
	}
}

func (m *DataTableModel) doBatchDelete() tea.Cmd {
	rt := m.resourceType
	api := m.api
	botID := m.botID
	groupID := m.groupID
	selected := make(map[string]bool)
	for k, v := range m.selected {
		selected[k] = v
	}
	return func() tea.Msg {
		var lastErr error
		deleted := 0
		for key := range selected {
			var err error
			switch rt {
			case ResourceFacts:
				id, _ := strconv.Atoi(key)
				err = api.DeleteFact(botID, groupID, id)
			case ResourceProfiles:
				err = api.DeleteUserProfile(botID, groupID, key)
			case ResourceMemes:
				id, _ := strconv.Atoi(key)
				err = api.DeleteMeme(botID, groupID, id)
			case ResourceVocabularies:
				id, _ := strconv.Atoi(key)
				err = api.DeleteVocabulary(botID, groupID, id)
			case ResourceSummaries:
				id, _ := strconv.Atoi(key)
				err = api.DeleteSummary(botID, groupID, id)
			}
			if err != nil {
				lastErr = err
			} else {
				deleted++
			}
		}
		return batchDeleteDoneMsg{Deleted: deleted, Error: lastErr}
	}
}

// ── View ──

func (m *DataTableModel) View() string {
	if m.loading {
		return "\n  " + style.Muted("Loading...")
	}
	if m.err != nil {
		return "\n  " + style.ErrorText("Error: "+m.err.Error()) + "\n\n  " + style.Muted("Press r to retry, ESC to go back")
	}
	if m.confirmingDelete {
		return m.renderConfirmDialog("Confirm Delete", "Delete this item?")
	}
	if m.confirmingBatchDelete {
		return m.renderConfirmDialog("Confirm Batch Delete", fmt.Sprintf("Delete %d selected items?", len(m.selected)))
	}

	var parts []string
	parts = append(parts, m.renderTitleBar())
	if m.searching {
		parts = append(parts, "  "+m.searchInput.View())
	}

	tableView := lipgloss.NewStyle().
		BorderStyle(lipgloss.NormalBorder()).
		BorderForeground(style.BorderColor).
		Render(m.table.View())
	parts = append(parts, tableView)

	parts = append(parts, m.renderStatusBar())
	parts = append(parts, m.help.View(m.keys))
	return strings.Join(parts, "\n")
}

func (m *DataTableModel) renderTitleBar() string {
	icon := m.formatter.title
	count := len(m.filteredItems)
	total := len(m.items)
	label := fmt.Sprintf("%s  \xe2\x80\x94  %d items", icon, count)
	if total != count {
		label = fmt.Sprintf("%s  \xe2\x80\x94  %d / %d items", icon, count, total)
	}
	if m.searching {
		label += "  [" + SearchActiveStyle.Render("Search: "+m.searchInput.Value()) + "]"
	}
	label = TruncateEnd(label, m.width-2)
	return TitleBarStyle.Width(m.width).Render(label)
}

func (m *DataTableModel) renderStatusBar() string {
	total := len(m.filteredItems)
	selectedInfo := fmt.Sprintf("%d selected", len(m.selected))
	text := fmt.Sprintf("  %d items  \xe2\x94\x82  %s", total, selectedInfo)
	if m.searchQuery != "" && !m.searching {
		text += "  \xe2\x94\x82  Filter: " + m.searchQuery
	}
	text = TruncateEnd(text, m.width-2)
	return StatusBarStyle.Width(m.width).Render(text)
}

func (m *DataTableModel) renderConfirmDialog(title, message string) string {
	cardW := min(50, m.width-4)
	content := lipgloss.NewStyle().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(style.BorderColor).
		Background(style.SurfaceAlt).
		Padding(1, 2).
		Width(cardW).
		Render(
			style.ErrorStyle.Render(title) + "\n\n" +
				message + "\n\n" +
				lipgloss.NewStyle().Foreground(style.TextMuted).Render("[Enter] Confirm  [Esc] Cancel"),
		)
	return lipgloss.Place(m.width, m.height, lipgloss.Center, lipgloss.Center, content)
}

// ── Formatter ──

type tableFormatter struct {
	title       string
	minWidths   []int
	columns     func(widths []int) []table.Column
	getRow      func(any, bool) table.Row
	getID       func(any) int
	getKey      func(any) string
	searchMatch func(any, string) bool
	canCreate   bool
}

func (f tableFormatter) calcWidths(totalWidth int) []int {
	n := len(f.minWidths)
	totalCols := n + 1 // +checkbox

	avail := totalWidth - 2 - totalCols*2
	if avail < totalCols*3 {
		avail = totalCols * 3
	}

	minW := make([]int, totalCols)
	minW[0] = 3 // checkbox
	for i, w := range f.minWidths {
		minW[i+1] = w
	}

	widths := make([]int, totalCols)
	used := 0
	for i := range widths {
		widths[i] = minW[i]
		used += minW[i]
	}

	remaining := avail - used
	if remaining >= 0 {
		extra := remaining / totalCols
		remainder := remaining % totalCols
		for i := range widths {
			widths[i] += extra
		}
		for i := 0; i < remainder && i < totalCols; i++ {
			widths[i]++
		}
	} else {
		ratio := float64(avail) / float64(used)
		for i := range widths {
			widths[i] = int(float64(minW[i]) * ratio)
			if widths[i] < 3 {
				widths[i] = 3
			}
		}
	}
	return widths
}

func getFormatter(rt ResourceType) tableFormatter {
	switch rt {
	case ResourceFacts:
		return tableFormatter{
			title:     "Memory (Facts)",
			minWidths: []int{5, 12, 15, 10, 10, 6},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "Keyword", Width: widths[2]},
					{Title: "Description", Width: widths[3]},
					{Title: "Values", Width: widths[4]},
					{Title: "Subjects", Width: widths[5]},
					{Title: "Scope", Width: widths[6]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(FactRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.Keyword,
					r.Description,
					r.Values,
					r.Subjects,
					r.ScopeType,
				}
			},
			getID:  func(a any) int { return a.(FactRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(FactRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(FactRecord)
				return strings.Contains(strings.ToLower(r.Keyword), kw) ||
					strings.Contains(strings.ToLower(r.Description), kw) ||
					strings.Contains(strings.ToLower(r.Values), kw) ||
					strings.Contains(strings.ToLower(r.Subjects), kw) ||
					strings.Contains(strings.ToLower(r.ScopeType), kw)
			},
			canCreate: true,
		}

	case ResourceProfiles:
		return tableFormatter{
			title:     "User Profiles",
			minWidths: []int{5, 12, 20, 20},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "UserID", Width: widths[2]},
					{Title: "Profile", Width: widths[3]},
					{Title: "Preferences", Width: widths[4]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(UserProfileRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.UserID,
					r.Profile,
					r.Preferences,
				}
			},
			getID:  func(a any) int { return a.(UserProfileRecord).ID },
			getKey: func(a any) string { return a.(UserProfileRecord).UserID },
			searchMatch: func(a any, kw string) bool {
				r := a.(UserProfileRecord)
				return strings.Contains(strings.ToLower(r.UserID), kw) ||
					strings.Contains(strings.ToLower(r.Profile), kw) ||
					strings.Contains(strings.ToLower(r.Preferences), kw)
			},
			canCreate: false,
		}

	case ResourceMemes:
		return tableFormatter{
			title:     "Memes",
			minWidths: []int{5, 15, 10, 10, 6, 6},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "Description", Width: widths[2]},
					{Title: "Purpose", Width: widths[3]},
					{Title: "Tags", Width: widths[4]},
					{Title: "Seen", Width: widths[5]},
					{Title: "Usage", Width: widths[6]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(MemeRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				desc := ""
				if r.Description != nil {
					desc = *r.Description
				}
				purpose := ""
				if r.Purpose != nil {
					purpose = *r.Purpose
				}
				tags := ""
				if r.Tags != nil {
					tags = *r.Tags
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					desc,
					purpose,
					tags,
					fmt.Sprintf("%d", r.SeenCount),
					fmt.Sprintf("%d", r.UsageCount),
				}
			},
			getID:  func(a any) int { return a.(MemeRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(MemeRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(MemeRecord)
				if r.Description != nil && strings.Contains(strings.ToLower(*r.Description), kw) {
					return true
				}
				if r.Purpose != nil && strings.Contains(strings.ToLower(*r.Purpose), kw) {
					return true
				}
				if r.Tags != nil && strings.Contains(strings.ToLower(*r.Tags), kw) {
					return true
				}
				return strings.Contains(strings.ToLower(fmt.Sprintf("%d", r.ID)), kw)
			},
			canCreate: false,
		}

	case ResourceVocabularies:
		return tableFormatter{
			title:     "Vocabulary",
			minWidths: []int{5, 12, 8, 20, 6},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "Word", Width: widths[2]},
					{Title: "Type", Width: widths[3]},
					{Title: "Meaning", Width: widths[4]},
					{Title: "Weight", Width: widths[5]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(VocabRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.Word,
					r.Type,
					r.Meaning,
					fmt.Sprintf("%d", r.Weight),
				}
			},
			getID:  func(a any) int { return a.(VocabRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(VocabRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(VocabRecord)
				return strings.Contains(strings.ToLower(r.Word), kw) ||
					strings.Contains(strings.ToLower(r.Type), kw) ||
					strings.Contains(strings.ToLower(r.Meaning), kw) ||
					strings.Contains(strings.ToLower(r.Example), kw)
			},
			canCreate: true,
		}

	case ResourceSummaries:
		return tableFormatter{
			title:     "Summaries",
			minWidths: []int{5, 12, 15, 15, 8, 8, 8},
			columns: func(widths []int) []table.Column {
				return []table.Column{
					{Title: "", Width: widths[0]},
					{Title: "ID", Width: widths[1]},
					{Title: "TimeRange", Width: widths[2]},
					{Title: "Content", Width: widths[3]},
					{Title: "KeyPoints", Width: widths[4]},
					{Title: "Tone", Width: widths[5]},
					{Title: "Participants", Width: widths[6]},
					{Title: "Messages", Width: widths[7]},
				}
			},
			getRow: func(a any, selected bool) table.Row {
				r := a.(SummaryRecord)
				cb := "[ ]"
				if selected {
					cb = "[x]"
				}
				tone := ""
				if r.EmotionalTone != nil {
					tone = *r.EmotionalTone
				}
				return table.Row{
					cb,
					fmt.Sprintf("%d", r.ID),
					r.TimeRange,
					r.Content,
					r.KeyPoints,
					tone,
					fmt.Sprintf("%d", r.ParticipantCount),
					fmt.Sprintf("%d", r.MessageCount),
				}
			},
			getID:  func(a any) int { return a.(SummaryRecord).ID },
			getKey: func(a any) string { return fmt.Sprintf("%d", a.(SummaryRecord).ID) },
			searchMatch: func(a any, kw string) bool {
				r := a.(SummaryRecord)
				if strings.Contains(strings.ToLower(r.TimeRange), kw) {
					return true
				}
				if strings.Contains(strings.ToLower(r.Content), kw) {
					return true
				}
				if strings.Contains(strings.ToLower(r.KeyPoints), kw) {
					return true
				}
				if r.EmotionalTone != nil && strings.Contains(strings.ToLower(*r.EmotionalTone), kw) {
					return true
				}
				return false
			},
			canCreate: false,
		}
	}
	return tableFormatter{}
}

// ── Data loading ──

func loadResourceData(rt ResourceType, api *API, botID, groupID string) ([]any, error) {
	switch rt {
	case ResourceFacts:
		records, err := api.GetFacts(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceProfiles:
		records, err := api.GetUserProfiles(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceMemes:
		records, err := api.GetMemes(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceVocabularies:
		records, err := api.GetVocabularies(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	case ResourceSummaries:
		records, err := api.GetSummaries(botID, groupID)
		if err != nil {
			return nil, err
		}
		items := make([]any, len(records))
		for i, r := range records {
			items[i] = r
		}
		return items, nil
	}
	return nil, fmt.Errorf("unknown resource type: %d", rt)
}
