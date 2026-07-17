package theme

import (
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"
)

func TestGoBusinessFilesDoNotHardcodeColors(t *testing.T) {
	root := filepath.Join("..", "..", "..")
	hexColor := regexp.MustCompile(`#[0-9A-Fa-f]{6}`)
	err := filepath.WalkDir(root, func(path string, entry fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		clean := filepath.ToSlash(path)
		if entry.IsDir() || !strings.HasSuffix(path, ".go") || strings.HasSuffix(path, "_test.go") {
			return nil
		}
		if strings.Contains(clean, "/internal/ui/theme/") || strings.HasSuffix(clean, "/internal/web/server.go") {
			return nil
		}
		data, readErr := os.ReadFile(path)
		if readErr != nil {
			return readErr
		}
		if match := hexColor.Find(data); match != nil {
			t.Errorf("hardcoded color %s in %s", match, clean)
		}
		return nil
	})
	if err != nil {
		t.Fatal(err)
	}
}
