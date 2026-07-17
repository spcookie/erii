package tree

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestMergeHOCONFile_NestedBlockMerge(t *testing.T) {
	// src adds pro/flash/lite under llm.capability
	src := `# LLM 配置
llm {
  choice-provider = "none"
  capability {
    completion = true
    lite {
      completion = true
      thinking = false
    }
    flash {
      completion = true
      thinking = true
    }
    pro {
      completion = true
      thinking = true
    }
  }
  providers {
    openai {
      api-key = ${?OPENAI_API_KEY}
    }
  }
}
`

	// dst has llm.capability but only with basic fields, no lite/flash/pro
	dst := `# LLM 配置
llm {
  choice-provider = "openai"
  capability {
    completion = true
    prompt-caching = true
  }
  providers {
    openai {
      api-key = ${?MY_KEY}
    }
  }
}
`
	dir := t.TempDir()
	srcPath := filepath.Join(dir, "src.conf")
	dstPath := filepath.Join(dir, "dst.conf")
	os.WriteFile(srcPath, []byte(src), 0644)
	os.WriteFile(dstPath, []byte(dst), 0644)

	result := mergeHOCONFile(srcPath, dstPath)

	if result.Action != "merged" {
		t.Fatalf("expected action 'merged', got '%s': %v", result.Action, result.Error)
	}

	merged, err := os.ReadFile(dstPath)
	if err != nil {
		t.Fatal(err)
	}

	mergedStr := string(merged)

	// Should contain nested blocks added from src
	if !strings.Contains(mergedStr, "pro {") {
		t.Error("merged config should contain 'pro' nested block")
	}
	if !strings.Contains(mergedStr, "flash {") {
		t.Error("merged config should contain 'flash' nested block")
	}
	if !strings.Contains(mergedStr, "lite {") {
		t.Error("merged config should contain 'lite' nested block")
	}
	// Should preserve user's custom value
	if !strings.Contains(mergedStr, `choice-provider = "openai"`) {
		t.Error("merged config should preserve user's choice-provider value")
	}
	// Should preserve user's custom env var
	if !strings.Contains(mergedStr, "${?MY_KEY}") {
		t.Error("merged config should preserve user's api-key value")
	}
	// Should preserve top-level comments
	if !strings.Contains(mergedStr, "# LLM 配置") {
		t.Error("merged config should preserve comments")
	}
}

func TestMergeHOCONFile_NewTopLevelBlock(t *testing.T) {
	src := `llm {
  choice-provider = "none"
}
vision {
  api-key = ${?VISION_API_KEY}
  provider = "minimax"
}
`
	dst := `llm {
  choice-provider = "openai"
}
`
	dir := t.TempDir()
	srcPath := filepath.Join(dir, "src.conf")
	dstPath := filepath.Join(dir, "dst.conf")
	os.WriteFile(srcPath, []byte(src), 0644)
	os.WriteFile(dstPath, []byte(dst), 0644)

	result := mergeHOCONFile(srcPath, dstPath)

	if result.Action != "merged" {
		t.Fatalf("expected 'merged', got '%s'", result.Action)
	}

	merged, _ := os.ReadFile(dstPath)
	mergedStr := string(merged)
	if !strings.Contains(mergedStr, "vision {") {
		t.Error("merged config should contain new top-level 'vision' block")
	}
}

func TestMergeHOCONFile_DeepNestedMerge(t *testing.T) {
	src := `llm {
  providers {
    openai {
      models {
        lite = "gpt-4.1-nano"
        flash = "gpt-4.1-mini"
        pro = "gpt-4.1"
      }
    }
  }
}
`
	dst := `llm {
  providers {
    openai {
      api-key = ${?OPENAI_API_KEY}
      models {
        lite = "gpt-4.1-mini"
      }
    }
  }
}
`
	dir := t.TempDir()
	srcPath := filepath.Join(dir, "src.conf")
	dstPath := filepath.Join(dir, "dst.conf")
	os.WriteFile(srcPath, []byte(src), 0644)
	os.WriteFile(dstPath, []byte(dst), 0644)

	result := mergeHOCONFile(srcPath, dstPath)
	if result.Action != "merged" {
		t.Fatalf("expected 'merged', got '%s': %v", result.Action, result.Error)
	}

	merged, _ := os.ReadFile(dstPath)
	mergedStr := string(merged)

	if !strings.Contains(mergedStr, `flash = "gpt-4.1-mini"`) {
		t.Error("should add new 'flash' field inside models block")
	}
	if !strings.Contains(mergedStr, `pro = "gpt-4.1"`) {
		t.Error("should add new 'pro' field inside models block")
	}
	if !strings.Contains(mergedStr, `api-key = ${?OPENAI_API_KEY}`) {
		t.Error("should preserve existing fields")
	}
	if !strings.Contains(mergedStr, `lite = "gpt-4.1-mini"`) {
		t.Error("should preserve user-modified leaf values")
	}
}

func TestMergeHOCONFile_SkipWhenUpToDate(t *testing.T) {
	src := `llm {
  choice-provider = "none"
}
`
	dst := `llm {
  choice-provider = "none"
}
`
	dir := t.TempDir()
	srcPath := filepath.Join(dir, "src.conf")
	dstPath := filepath.Join(dir, "dst.conf")
	os.WriteFile(srcPath, []byte(src), 0644)
	os.WriteFile(dstPath, []byte(dst), 0644)

	result := mergeHOCONFile(srcPath, dstPath)
	if result.Action != "skipped" {
		t.Errorf("expected 'skipped', got '%s'", result.Action)
	}
}

func TestMergeHOCONFile_CreateIfNotExists(t *testing.T) {
	src := `llm {
  choice-provider = "none"
}
`
	dir := t.TempDir()
	srcPath := filepath.Join(dir, "src.conf")
	dstPath := filepath.Join(dir, "dst.conf")
	os.WriteFile(srcPath, []byte(src), 0644)

	result := mergeHOCONFile(srcPath, dstPath)
	if result.Action != "created" {
		t.Errorf("expected 'created', got '%s'", result.Action)
	}
}

func TestMergeHOCONFile_SourceMissing(t *testing.T) {
	dir := t.TempDir()
	srcPath := filepath.Join(dir, "no-src.conf")
	dstPath := filepath.Join(dir, "dst.conf")
	os.WriteFile(dstPath, []byte("x = 1"), 0644)

	result := mergeHOCONFile(srcPath, dstPath)
	if result.Action != "source_missing" {
		t.Errorf("expected 'source_missing', got '%s'", result.Action)
	}
}

func TestExtractFieldsRecursive(t *testing.T) {
	content := `capability {
  choice-provider = "none"
  capability {
    completion = true
    prompt-caching = true
    pro {
      completion = true
      thinking = true
    }
    flash {
      completion = true
    }
  }
  providers {
    openai {
      api-key = ${?OPENAI_API_KEY}
    }
  }
}
`
	lines := strings.Split(content, "\n")
	fields := extractFieldsRecursive(lines)

	// depth-1 fields
	if _, ok := fields["choice-provider"]; !ok {
		t.Error("should have choice-provider")
	}
	if _, ok := fields["capability"]; !ok {
		t.Error("should have capability block")
	}
	if _, ok := fields["providers"]; !ok {
		t.Error("should have providers block")
	}

	// depth-2 fields
	if _, ok := fields["capability.completion"]; !ok {
		t.Error("should have capability.completion")
	}
	if _, ok := fields["capability.pro"]; !ok {
		t.Error("should have capability.pro block")
	}
	if _, ok := fields["capability.flash"]; !ok {
		t.Error("should have capability.flash block")
	}

	// depth-3 fields
	if _, ok := fields["capability.pro.completion"]; !ok {
		t.Error("should have capability.pro.completion")
	}
	if _, ok := fields["providers.openai"]; !ok {
		t.Error("should have providers.openai block")
	}

	// leaf field at depth-3
	if _, ok := fields["providers.openai.api-key"]; !ok {
		t.Error("should have providers.openai.api-key")
	}
}

func TestExtractFieldsRecursive_EmptyBlock(t *testing.T) {
	content := "block {\n  x = 1\n  y {}\n}\n"
	lines := strings.Split(content, "\n")
	fields := extractFieldsRecursive(lines)

	if _, ok := fields["x"]; !ok {
		t.Error("should have x")
	}
	if _, ok := fields["y"]; !ok {
		t.Error("should have y (empty block)")
	}
}

func TestIsBlock(t *testing.T) {
	tests := []struct {
		lines []string
		want  bool
	}{
		{[]string{`completion = true`}, false},
		{[]string{`pro {`, `  completion = true`, `}`}, true},
		{[]string{`y {}`}, false}, // empty object on one line is not a block
		{[]string{`api-key = ${?OPENAI_API_KEY}`}, false},
		{[]string{`models {`, `  lite = "x"`, `}`}, true},
	}
	for _, tc := range tests {
		got := isBlock(tc.lines)
		if got != tc.want {
			t.Errorf("isBlock(%q) = %v, want %v", tc.lines, got, tc.want)
		}
	}
}
