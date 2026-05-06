package manage

import (
	"fmt"
	"regexp"
	"strconv"
	"strings"

	"erii-cli/internal/tui/style"

	"github.com/charmbracelet/bubbles/help"
	"github.com/charmbracelet/bubbles/key"
	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/huh"
)

type EditFormModel struct {
	resourceType ResourceType
	isCreate     bool
	botID        string
	groupID      string
	api          *API
	data         any
	form         *huh.Form
	width        int
	height       int
	errMsg       string
	keys         editFormKeys
	help         help.Model

	// Keep references to request objects so Text fields update them directly.
	factReq     FactRequest
	profileReq  UpdateUserProfileRequest
	memeDesc    string
	memePurpose string
	memeTags    string
	vocabReq    VocabRequest
	vocabWeight string
	summaryReq  UpdateSummaryRequest
	summaryTone string
}

func NewEditFormModel(api *API, rt ResourceType, bot BotInfo, group GroupInfo, data any, isCreate bool) *EditFormModel {
	m := &EditFormModel{
		resourceType: rt,
		isCreate:     isCreate,
		botID:        bot.BotID,
		groupID:      group.GroupID,
		api:          api,
		data:         data,
		keys:         defaultEditFormKeys,
		help:         help.New(),
	}

	w := m.width - 4
	if w < 20 {
		w = 60
	}

	switch rt {
	case ResourceFacts:
		m.form = m.buildFactForm(data, isCreate, w)
	case ResourceProfiles:
		m.form = m.buildProfileForm(data, isCreate, w)
	case ResourceMemes:
		m.form = m.buildMemeForm(data, isCreate, w)
	case ResourceVocabularies:
		m.form = m.buildVocabForm(data, isCreate, w)
	case ResourceSummaries:
		m.form = m.buildSummaryForm(data, isCreate, w)
	}

	if m.form != nil {
		km := huh.NewDefaultKeyMap()
		km.Quit = key.NewBinding(key.WithKeys("esc"))
		m.form.WithKeyMap(km)
	}

	return m
}

func (m *EditFormModel) formTitle() string {
	action := "Edit"
	if m.isCreate {
		action = "New"
	}
	return fmt.Sprintf("%s %s", action, m.resourceType.Title())
}

func (m *EditFormModel) buildFactForm(data any, isCreate bool, width int) *huh.Form {
	if !isCreate && data != nil {
		r := data.(FactRecord)
		m.factReq = FactRequest{
			Keyword:     r.Keyword,
			Description: r.Description,
			Values:      r.Values,
			Subjects:    r.Subjects,
			ScopeType:   r.ScopeType,
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("keyword").Title("Keyword").Value(&m.factReq.Keyword).Validate(huh.ValidateNotEmpty()),
			huh.NewText().Key("description").Title("Description (multi-line)").Value(&m.factReq.Description),
			huh.NewInput().Key("values").Title("Values").Value(&m.factReq.Values),
			huh.NewInput().Key("subjects").Title("Subjects (comma-separated)").Value(&m.factReq.Subjects).Validate(validateCommaSeparated),
			huh.NewSelect[string]().Key("scopeType").Title("Scope Type").
				Options(huh.NewOption("User", "USER"), huh.NewOption("Group", "GROUP")).
				Value(&m.factReq.ScopeType),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildProfileForm(data any, isCreate bool, width int) *huh.Form {
	if !isCreate && data != nil {
		r := data.(UserProfileRecord)
		m.profileReq = UpdateUserProfileRequest{
			Profile:     r.Profile,
			Preferences: r.Preferences,
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewText().Key("profile").Title("Profile (multi-line)").
				Value(&m.profileReq.Profile).
				WithHeight(4),
			huh.NewText().Key("preferences").Title("Preferences (multi-line)").
				Value(&m.profileReq.Preferences).
				WithHeight(4),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildMemeForm(data any, isCreate bool, width int) *huh.Form {
	if !isCreate && data != nil {
		r := data.(MemeRecord)
		if r.Description != nil {
			m.memeDesc = *r.Description
		}
		if r.Purpose != nil {
			m.memePurpose = *r.Purpose
		}
		if r.Tags != nil {
			m.memeTags = *r.Tags
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewText().Key("description").Title("Description (multi-line)").
				Value(&m.memeDesc).
				WithHeight(3),
			huh.NewText().Key("purpose").Title("Purpose (multi-line)").
				Value(&m.memePurpose).
				WithHeight(3),
			huh.NewInput().Key("tags").Title("Tags").Value(&m.memeTags),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildVocabForm(data any, isCreate bool, width int) *huh.Form {
	if !isCreate && data != nil {
		r := data.(VocabRecord)
		m.vocabReq = VocabRequest{
			Word:    r.Word,
			Type:    r.Type,
			Meaning: r.Meaning,
			Example: r.Example,
			Weight:  r.Weight,
		}
		m.vocabWeight = fmt.Sprintf("%d", r.Weight)
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("word").Title("Word").Value(&m.vocabReq.Word).Validate(huh.ValidateNotEmpty()),
			huh.NewInput().Key("type").Title("Type").Value(&m.vocabReq.Type),
			huh.NewInput().Key("meaning").Title("Meaning").Value(&m.vocabReq.Meaning),
			huh.NewInput().Key("example").Title("Example").Value(&m.vocabReq.Example),
			huh.NewInput().Key("weight").Title("Weight (0-100)").Value(&m.vocabWeight),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildSummaryForm(data any, isCreate bool, width int) *huh.Form {
	if !isCreate && data != nil {
		r := data.(SummaryRecord)
		m.summaryReq = UpdateSummaryRequest{
			TimeRange: r.TimeRange,
			Content:   r.Content,
			KeyPoints: r.KeyPoints,
		}
		if r.EmotionalTone != nil {
			m.summaryTone = *r.EmotionalTone
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("timeRange").Title("Time Range (YYYY-MM-DD HH:MM ~ YYYY-MM-DD HH:MM)").
				Placeholder("2026-05-01 23:59 ~ 2026-05-02 13:30").
				Value(&m.summaryReq.TimeRange).
				Validate(validateTimeRange),
			huh.NewText().Key("content").Title("Content (multi-line)").
				Value(&m.summaryReq.Content).
				WithHeight(4),
			huh.NewText().Key("keyPoints").Title("Key Points (topic: description / line)").
				Placeholder("话题：具体描述内容").
				Value(&m.summaryReq.KeyPoints).
				Validate(validateKeyPoints).
				WithHeight(4),
			huh.NewInput().Key("emotionalTone").Title("Emotional Tone").Value(&m.summaryTone),
		),
	).WithWidth(width).WithShowHelp(false)
}

var timeRangeRegex = regexp.MustCompile(`^\d{4}-\d{2}-\d{2} \d{2}:\d{2} ~ \d{4}-\d{2}-\d{2} \d{2}:\d{2}$`)

func validateTimeRange(s string) error {
	if s == "" {
		return nil
	}
	if !timeRangeRegex.MatchString(s) {
		return fmt.Errorf("格式错误，示例：2026-05-01 23:59 ~ 2026-05-02 13:30")
	}
	return nil
}

func validateKeyPoints(s string) error {
	if s == "" {
		return nil
	}
	for _, line := range strings.Split(s, "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		if !strings.Contains(line, "：") {
			return fmt.Errorf("每行必须包含中文冒号，示例：主题：描述")
		}
	}
	return nil
}

func validateCommaSeparated(s string) error {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	for _, p := range parts {
		if strings.TrimSpace(p) == "" {
			return fmt.Errorf("逗号分隔的每一项不能为空")
		}
	}
	return nil
}

func validateNumber(s string) error {
	if s == "" {
		return nil
	}
	if _, err := strconv.Atoi(s); err != nil {
		return fmt.Errorf("必须是数字")
	}
	return nil
}

func (m *EditFormModel) Init() tea.Cmd {
	if m.form != nil {
		return m.form.Init()
	}
	return nil
}

func (m *EditFormModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		m.help.Width = msg.Width
		if m.form != nil {
			w := m.width - 4
			if w < 20 {
				w = 60
			}
			m.form.WithWidth(w)
		}
		return m, nil

	case tea.KeyMsg:
		if msg.Type == tea.KeyCtrlC {
			return m, tea.Quit
		}

	case error:
		m.errMsg = msg.Error()
		return m, nil
	}

	if m.form != nil {
		var cmd tea.Cmd
		var newModel tea.Model
		newModel, cmd = m.form.Update(msg)
		m.form = newModel.(*huh.Form)

		if m.form.State == huh.StateCompleted {
			return m, m.submit()
		}
		if m.form.State == huh.StateAborted {
			return m, func() tea.Msg { return PopMsg{} }
		}
		return m, cmd
	}

	return m, nil
}

func (m *EditFormModel) submit() tea.Cmd {
	rt := m.resourceType
	api := m.api
	botID := m.botID
	groupID := m.groupID
	isCreate := m.isCreate

	var factID, memeID, vocabID, summaryID int
	var userID string

	if !isCreate && m.data != nil {
		switch d := m.data.(type) {
		case FactRecord:
			factID = d.ID
		case UserProfileRecord:
			userID = d.UserID
		case MemeRecord:
			memeID = d.ID
		case VocabRecord:
			vocabID = d.ID
		case SummaryRecord:
			summaryID = d.ID
		}
	}

	return func() tea.Msg {
		var err error
		switch rt {
		case ResourceFacts:
			req := m.factReq
			if isCreate {
				err = api.CreateFact(botID, groupID, req)
			} else {
				err = api.UpdateFact(botID, groupID, factID, req)
			}
		case ResourceProfiles:
			err = api.UpdateUserProfile(botID, groupID, userID, m.profileReq)
		case ResourceMemes:
			var descPtr, purposePtr, tagsPtr *string
			if m.memeDesc != "" {
				descPtr = &m.memeDesc
			}
			if m.memePurpose != "" {
				purposePtr = &m.memePurpose
			}
			if m.memeTags != "" {
				tagsPtr = &m.memeTags
			}
			req := UpdateMemeRequest{
				Description: descPtr,
				Purpose:     purposePtr,
				Tags:        tagsPtr,
			}
			err = api.UpdateMeme(botID, groupID, memeID, req)
		case ResourceVocabularies:
			weight, _ := strconv.Atoi(m.vocabWeight)
			m.vocabReq.Weight = weight
			if isCreate {
				err = api.CreateVocabulary(botID, groupID, m.vocabReq)
			} else {
				err = api.UpdateVocabulary(botID, groupID, vocabID, m.vocabReq)
			}
		case ResourceSummaries:
			var tonePtr *string
			if m.summaryTone != "" {
				tonePtr = &m.summaryTone
			}
			req := UpdateSummaryRequest{
				TimeRange:     m.summaryReq.TimeRange,
				Content:       m.summaryReq.Content,
				KeyPoints:     m.summaryReq.KeyPoints,
				EmotionalTone: tonePtr,
			}
			err = api.UpdateSummary(botID, groupID, summaryID, req)
		}
		if err != nil {
			return err
		}
		return PopAndRefreshMsg{}
	}
}

func (m *EditFormModel) View() string {
	var parts []string
	parts = append(parts, style.Title(m.formTitle()))
	if m.errMsg != "" {
		parts = append(parts, style.ErrorStyle.Render("Error: "+m.errMsg))
	}
	if m.form != nil {
		parts = append(parts, m.form.View())
	}
	parts = append(parts, m.help.View(m.keys))
	return strings.Join(parts, "\n")
}
