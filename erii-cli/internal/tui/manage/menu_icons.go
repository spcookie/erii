package manage

import "os"

func webMenuIconsEnabled() bool {
	return os.Getenv("ERII_WEB") == "1"
}

func menuTitle(web bool, nerdIcon, nativeIcon, label string) string {
	icon := nativeIcon
	if web {
		icon = nerdIcon
	}
	return icon + "  " + label
}
