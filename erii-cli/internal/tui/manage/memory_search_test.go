package manage

import (
	"strings"
	"testing"

	"erii-cli/internal/api"
	tea "github.com/charmbracelet/bubbletea"
)

func TestRenderGraphTextShowsNodesEdgesAndSources(t *testing.T) {
	response := &api.MemoryGraphSearchResponse{
		Nodes: []api.MemoryGraphNode{
			{ID: "fact:1", Type: "fact", Label: "#1 seed", Source: "seed"},
			{ID: "fact:2", Type: "fact", Label: "#2 expanded", Source: "expanded"},
			{ID: "entity:杭州", Type: "entity", Label: "杭州"},
		},
		Edges: []api.MemoryGraphEdge{
			{From: "fact:1", To: "entity:杭州", Label: "involves"},
		},
	}

	rendered := renderGraphText(response, 80)

	for _, want := range []string{
		"Node index",
		"FACT",
		"SEED",
		"EXPANDED",
		"ENTITY",
		"EDGE",
		"fact:1",
		"#1 seed",
		"entity:杭州",
		"杭州",
		"fact:1 -> entity:杭州",
	} {
		if !strings.Contains(rendered, want) {
			t.Fatalf("rendered graph missing %q:\n%s", want, rendered)
		}
	}
}

func TestRenderGraphSearchTextShowsStyledSections(t *testing.T) {
	score := 0.9
	response := &api.MemoryGraphSearchResponse{
		Query: "杭州",
		SeedResults: []api.MemoryFactSearchResult{
			{
				Fact: api.FactRecord{
					ID:          1,
					Keyword:     "seed fact",
					Description: "seed description",
					ScopeType:   "group",
				},
				Score:  &score,
				Source: "seed",
			},
		},
		ExpandedResults: []api.MemoryFactSearchResult{
			{
				Fact: api.FactRecord{
					ID:          2,
					Keyword:     "expanded fact",
					Description: "expanded description",
					ScopeType:   "group",
				},
				Source: "expanded",
			},
		},
		Nodes: []api.MemoryGraphNode{
			{ID: "fact:1", Type: "fact", Label: "#1 seed fact", Source: "seed"},
			{ID: "entity:杭州", Type: "entity", Label: "杭州"},
		},
		Edges: []api.MemoryGraphEdge{
			{From: "fact:1", To: "entity:杭州", Label: "involves"},
		},
	}

	rendered := renderGraphSearchText(response, 80)

	for _, want := range []string{"One-hop graph", "SEED", "EXPANDED", "FACT", "ENTITY", "EDGE"} {
		if !strings.Contains(rendered, want) {
			t.Fatalf("rendered graph search missing %q:\n%s", want, rendered)
		}
	}
}

func TestRenderGraphSearchTextShowsSeedEntityRelatedPaths(t *testing.T) {
	score := 0.9
	response := &api.MemoryGraphSearchResponse{
		Query: "杭州",
		SeedResults: []api.MemoryFactSearchResult{
			{
				Fact: api.FactRecord{
					ID:          1,
					Keyword:     "seed fact",
					Description: "seed description",
					Entities:    []string{"杭州", "机器人"},
					ScopeType:   "group",
				},
				Score:  &score,
				Source: "seed",
			},
		},
		ExpandedResults: []api.MemoryFactSearchResult{
			{
				Fact: api.FactRecord{
					ID:          2,
					Keyword:     "expanded fact",
					Description: "expanded description",
					Entities:    []string{"杭州"},
					ScopeType:   "group",
				},
				Source: "expanded",
			},
		},
		Nodes: []api.MemoryGraphNode{
			{ID: "fact:1", Type: "fact", Label: "#1 seed fact", Source: "seed"},
			{ID: "fact:2", Type: "fact", Label: "#2 expanded fact", Source: "expanded"},
			{ID: "entity:杭州", Type: "entity", Label: "杭州"},
			{ID: "entity:机器人", Type: "entity", Label: "机器人"},
		},
		Edges: []api.MemoryGraphEdge{
			{From: "fact:1", To: "entity:杭州", Label: "involves"},
			{From: "fact:2", To: "entity:杭州", Label: "involves"},
		},
	}

	rendered := renderGraphSearchText(response, 80)

	for _, want := range []string{
		"Paths",
		"SEED",
		"#1 seed fact",
		"├─",
		"ENTITY",
		"杭州",
		"RELATED",
		"#2 expanded fact",
		"Node index",
	} {
		if !strings.Contains(rendered, want) {
			t.Fatalf("rendered graph paths missing %q:\n%s", want, rendered)
		}
	}
}

func TestRenderVectorTextShowsStyledResultLabels(t *testing.T) {
	score := 0.8
	response := &api.MemoryVectorSearchResponse{
		Query: "coffee",
		Results: []api.MemoryFactSearchResult{
			{
				Fact: api.FactRecord{
					ID:          1,
					Keyword:     "coffee",
					Description: "likes light roast coffee",
					ScopeType:   "group",
				},
				Score:  &score,
				Source: "bm25",
			},
		},
	}

	rendered := renderVectorText(response, 80)

	for _, want := range []string{"Hybrid rank", "BM25", "score 0.8000", "#1", "group"} {
		if !strings.Contains(rendered, want) {
			t.Fatalf("rendered vector result missing %q:\n%s", want, rendered)
		}
	}
}

func TestRenderVectorTextUsesSingleLineTags(t *testing.T) {
	score := 0.4233
	response := &api.MemoryVectorSearchResponse{
		Query: "杭州",
		Results: []api.MemoryFactSearchResult{
			{
				Fact: api.FactRecord{
					ID:          418,
					Keyword:     "图片识别能力",
					Description: "用户2012653413具备查看和处理图片的能力",
					ScopeType:   "user",
				},
				Score:  &score,
				Source: "vector",
			},
		},
	}

	rendered := renderVectorText(response, 80)

	for _, unwanted := range []string{"╭", "╮", "╰", "╯"} {
		if strings.Contains(rendered, unwanted) {
			t.Fatalf("vector result tags should stay on one line, found border %q:\n%s", unwanted, rendered)
		}
	}
}

func TestParseCommaListReturnsEmptySliceForBlankInput(t *testing.T) {
	values := parseCommaList("  ")
	if values == nil {
		t.Fatalf("blank comma list should return an empty slice, not nil")
	}
	if len(values) != 0 {
		t.Fatalf("blank comma list length = %d", len(values))
	}
}

func TestMemorySearchBackspaceEditsInputInsteadOfLeavingScreen(t *testing.T) {
	model := NewMemorySearchModel(nil, MemorySearchVector, api.BotInfo{}, api.GroupInfo{})
	model.input.SetValue("abc")
	model.input.SetCursor(len("abc"))

	updated, cmd := model.Update(tea.KeyMsg{Type: tea.KeyBackspace})
	if cmd != nil {
		if msg := cmd(); msg != nil {
			if _, ok := msg.(PopMsg); ok {
				t.Fatalf("backspace should edit the query, not leave the search screen")
			}
		}
	}

	searchModel := updated.(*MemorySearchModel)
	if got := searchModel.input.Value(); got != "ab" {
		t.Fatalf("input after backspace = %q, want %q", got, "ab")
	}
}

func TestMemorySearchEscLeavesScreen(t *testing.T) {
	model := NewMemorySearchModel(nil, MemorySearchVector, api.BotInfo{}, api.GroupInfo{})

	_, cmd := model.Update(tea.KeyMsg{Type: tea.KeyEsc})
	if cmd == nil {
		t.Fatalf("esc should return a PopMsg command")
	}
	if _, ok := cmd().(PopMsg); !ok {
		t.Fatalf("esc should leave the search screen")
	}
}

func TestMemorySearchViewUsesHelpComponentFooter(t *testing.T) {
	model := NewMemorySearchModel(nil, MemorySearchVector, api.BotInfo{}, api.GroupInfo{})
	updated, _ := model.Update(tea.WindowSizeMsg{Width: 100, Height: 30})
	view := updated.(*MemorySearchModel).View()

	if strings.Contains(view, "enter search | r retry") {
		t.Fatalf("search view should use help component footer instead of manual status text:\n%s", view)
	}
	for _, want := range []string{"enter", "search", "esc", "back", "ctrl+c", "quit"} {
		if !strings.Contains(view, want) {
			t.Fatalf("search view missing help text %q:\n%s", want, view)
		}
	}
}
