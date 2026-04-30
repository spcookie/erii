package manage

import (
	"fmt"
	"strconv"
	"strings"

	"erii-cli/internal/tui/style"

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
}

func NewEditFormModel(api *API, rt ResourceType, bot BotInfo, group GroupInfo, data any, isCreate bool) *EditFormModel {
	m := &EditFormModel{
		resourceType: rt,
		isCreate:     isCreate,
		botID:        bot.BotID,
		groupID:      group.GroupID,
		api:          api,
		data:         data,
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
		km.Quit = key.NewBinding(key.WithKeys("esc", "ctrl+c"))
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
	var req FactRequest
	if !isCreate && data != nil {
		r := data.(FactRecord)
		req = FactRequest{
			Keyword:     r.Keyword,
			Description: r.Description,
			Values:      r.Values,
			Subjects:    r.Subjects,
			ScopeType:   r.ScopeType,
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("keyword").Title("Keyword").Value(&req.Keyword).Validate(huh.ValidateNotEmpty()),
			huh.NewInput().Key("description").Title("Description").Value(&req.Description),
			huh.NewInput().Key("values").Title("Values").Value(&req.Values),
			huh.NewInput().Key("subjects").Title("Subjects").Value(&req.Subjects),
			huh.NewSelect[string]().Key("scopeType").Title("Scope Type").
				Options(huh.NewOption("User", "USER"), huh.NewOption("Group", "GROUP")).
				Value(&req.ScopeType),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildProfileForm(data any, isCreate bool, width int) *huh.Form {
	var req UpdateUserProfileRequest
	if !isCreate && data != nil {
		r := data.(UserProfileRecord)
		req = UpdateUserProfileRequest{
			Profile:     r.Profile,
			Preferences: r.Preferences,
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("profile").Title("Profile").Value(&req.Profile),
			huh.NewInput().Key("preferences").Title("Preferences").Value(&req.Preferences),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildMemeForm(data any, isCreate bool, width int) *huh.Form {
	var desc, purpose, tags string
	if !isCreate && data != nil {
		r := data.(MemeRecord)
		if r.Description != nil {
			desc = *r.Description
		}
		if r.Purpose != nil {
			purpose = *r.Purpose
		}
		if r.Tags != nil {
			tags = *r.Tags
		}
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("description").Title("Description").Value(&desc),
			huh.NewInput().Key("purpose").Title("Purpose").Value(&purpose),
			huh.NewInput().Key("tags").Title("Tags").Value(&tags),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildVocabForm(data any, isCreate bool, width int) *huh.Form {
	var req VocabRequest
	var weightStr string
	if !isCreate && data != nil {
		r := data.(VocabRecord)
		req = VocabRequest{
			Word:    r.Word,
			Type:    r.Type,
			Meaning: r.Meaning,
			Example: r.Example,
			Weight:  r.Weight,
		}
		weightStr = fmt.Sprintf("%d", r.Weight)
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("word").Title("Word").Value(&req.Word).Validate(huh.ValidateNotEmpty()),
			huh.NewInput().Key("type").Title("Type").Value(&req.Type),
			huh.NewInput().Key("meaning").Title("Meaning").Value(&req.Meaning),
			huh.NewInput().Key("example").Title("Example").Value(&req.Example),
			huh.NewInput().Key("weight").Title("Weight (0-100)").Value(&weightStr),
		),
	).WithWidth(width).WithShowHelp(false)
}

func (m *EditFormModel) buildSummaryForm(data any, isCreate bool, width int) *huh.Form {
	var req UpdateSummaryRequest
	var toneStr, pcStr, mcStr string
	if !isCreate && data != nil {
		r := data.(SummaryRecord)
		req = UpdateSummaryRequest{
			TimeRange: r.TimeRange,
			Content:   r.Content,
			KeyPoints: r.KeyPoints,
		}
		if r.EmotionalTone != nil {
			toneStr = *r.EmotionalTone
		}
		pcStr = fmt.Sprintf("%d", r.ParticipantCount)
		mcStr = fmt.Sprintf("%d", r.MessageCount)
	}
	return huh.NewForm(
		huh.NewGroup(
			huh.NewInput().Key("timeRange").Title("Time Range").Value(&req.TimeRange),
			huh.NewInput().Key("content").Title("Content").Value(&req.Content),
			huh.NewInput().Key("keyPoints").Title("Key Points").Value(&req.KeyPoints),
			huh.NewInput().Key("emotionalTone").Title("Emotional Tone").Value(&toneStr),
			huh.NewInput().Key("participantCount").Title("Participant Count").Value(&pcStr),
			huh.NewInput().Key("messageCount").Title("Message Count").Value(&mcStr),
		),
	).WithWidth(width).WithShowHelp(false)
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
		if m.form != nil {
			w := m.width - 4
			if w < 20 {
				w = 60
			}
			m.form.WithWidth(w)
		}
		return m, nil

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
			req := FactRequest{
				Keyword:     m.form.GetString("keyword"),
				Description: m.form.GetString("description"),
				Values:      m.form.GetString("values"),
				Subjects:    m.form.GetString("subjects"),
				ScopeType:   m.form.GetString("scopeType"),
			}
			if isCreate {
				err = api.CreateFact(botID, groupID, req)
			} else {
				err = api.UpdateFact(botID, groupID, factID, req)
			}
		case ResourceProfiles:
			req := UpdateUserProfileRequest{
				Profile:     m.form.GetString("profile"),
				Preferences: m.form.GetString("preferences"),
			}
			err = api.UpdateUserProfile(botID, groupID, userID, req)
		case ResourceMemes:
			desc := m.form.GetString("description")
			purpose := m.form.GetString("purpose")
			tags := m.form.GetString("tags")
			var descPtr, purposePtr, tagsPtr *string
			if desc != "" {
				descPtr = &desc
			}
			if purpose != "" {
				purposePtr = &purpose
			}
			if tags != "" {
				tagsPtr = &tags
			}
			req := UpdateMemeRequest{
				Description: descPtr,
				Purpose:     purposePtr,
				Tags:        tagsPtr,
			}
			err = api.UpdateMeme(botID, groupID, memeID, req)
		case ResourceVocabularies:
			weight, _ := strconv.Atoi(m.form.GetString("weight"))
			req := VocabRequest{
				Word:    m.form.GetString("word"),
				Type:    m.form.GetString("type"),
				Meaning: m.form.GetString("meaning"),
				Example: m.form.GetString("example"),
				Weight:  weight,
			}
			if isCreate {
				err = api.CreateVocabulary(botID, groupID, req)
			} else {
				err = api.UpdateVocabulary(botID, groupID, vocabID, req)
			}
		case ResourceSummaries:
			pc, _ := strconv.Atoi(m.form.GetString("participantCount"))
			mc, _ := strconv.Atoi(m.form.GetString("messageCount"))
			tone := m.form.GetString("emotionalTone")
			var tonePtr *string
			if tone != "" {
				tonePtr = &tone
			}
			req := UpdateSummaryRequest{
				TimeRange:        m.form.GetString("timeRange"),
				Content:          m.form.GetString("content"),
				KeyPoints:        m.form.GetString("keyPoints"),
				EmotionalTone:    tonePtr,
				ParticipantCount: pc,
				MessageCount:     mc,
			}
			err = api.UpdateSummary(botID, groupID, summaryID, req)
		}
		if err != nil {
			return err
		}
		return PopMsg{}
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
	parts = append(parts, style.Muted("ESC cancel  \xc2\xb7  Enter submit"))
	return strings.Join(parts, "\n")
}
