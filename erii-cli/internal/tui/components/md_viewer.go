package components

import (
	"os"
	"os/exec"
)

func viewMd(path string) {
	exec.Command("glow", path).Run()
}

func editMd(path string) {
	editor := os.Getenv("EDITOR")
	if editor == "" {
		editor = "vim"
	}
	cmd := exec.Command(editor, path)
	cmd.Stdin = os.Stdin
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Run()
}
