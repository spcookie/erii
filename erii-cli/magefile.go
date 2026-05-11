//go:build mage
// +build mage

package main

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/magefile/mage/mg"
	"github.com/magefile/mage/sh"
)

var Default = All

// Build matrix
var platforms = []struct {
	OS   string
	Arch []string
}{
	{"darwin", []string{"amd64", "arm64"}},
	{"linux", []string{"386", "amd64", "arm64"}},
	{"windows", []string{"386", "amd64", "arm64"}},
}

const (
	appName  = "erii-cli"
	buildDir = "build"
)

// Clean removes all build artifacts.
func Clean() error {
	entries, err := os.ReadDir(buildDir)
	if err != nil {
		return nil
	}
	for _, e := range entries {
		if e.IsDir() {
			os.RemoveAll(filepath.Join(buildDir, e.Name()))
		}
	}
	return nil
}

// Build builds for a single platform/arch.
func Build(osName, arch string) error {
	env := map[string]string{
		"GOOS":        osName,
		"GOARCH":      arch,
		"CGO_ENABLED": "0",
	}

	outName := appName
	if osName == "windows" {
		outName += ".exe"
	}

	outDir := filepath.Join(buildDir, fmt.Sprintf("erii-%s", osName), arch)
	if err := os.MkdirAll(outDir, 0o755); err != nil {
		return err
	}

	outPath := filepath.Join(outDir, outName)

	fmt.Printf("Building %s/%s → %s\n", osName, arch, outPath)
	return sh.RunWith(env, "go", "build", "-o", outPath, ".")
}

// All builds for all platforms and architectures.
func All() error {
	mg.Deps(Clean)

	for _, p := range platforms {
		for _, arch := range p.Arch {
			if err := Build(p.OS, arch); err != nil {
				return err
			}
		}
	}
	return nil
}

// Darwin builds only darwin binaries.
func Darwin() error {
	for _, arch := range platforms[0].Arch {
		if err := Build("darwin", arch); err != nil {
			return err
		}
	}
	return nil
}

// Linux builds only linux binaries.
func Linux() error {
	for _, arch := range platforms[1].Arch {
		if err := Build("linux", arch); err != nil {
			return err
		}
	}
	return nil
}

// Windows builds only windows binaries.
func Windows() error {
	for _, arch := range platforms[2].Arch {
		if err := Build("windows", arch); err != nil {
			return err
		}
	}
	return nil
}
