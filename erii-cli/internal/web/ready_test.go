package web

import (
	"bytes"
	"net"
	"strconv"
	"strings"
	"testing"
)

func TestPrintReadyLocalOnly(t *testing.T) {
	var output bytes.Buffer
	PrintReady(&output, Config{
		Host:  "127.0.0.1",
		Port:  "9527",
		Token: "fixed-token",
	}, false)

	text := output.String()
	for _, want := range []string{"Erii Console", "READY", "Local", "http://localhost:9527/?token=fixed-token"} {
		if !strings.Contains(text, want) {
			t.Fatalf("ready output missing %q:\n%s", want, text)
		}
	}
	if strings.Contains(text, "Network") || strings.Contains(text, "Press Ctrl+C") {
		t.Fatalf("local detached output contains an unexpected row:\n%s", text)
	}
}

func TestPrintReadyNetworkAndForegroundHint(t *testing.T) {
	var output bytes.Buffer
	PrintReady(&output, Config{
		Host:  "192.0.2.25",
		Port:  "9527",
		Token: "fixed-token",
	}, true)

	text := output.String()
	for _, want := range []string{
		"Local",
		"http://localhost:9527/?token=fixed-token",
		"Network",
		"http://192.0.2.25:9527/?token=fixed-token",
		"Press Ctrl+C to stop",
	} {
		if !strings.Contains(text, want) {
			t.Fatalf("ready output missing %q:\n%s", want, text)
		}
	}
}

func TestShouldPrintNetworkURL(t *testing.T) {
	for _, host := range []string{"", "localhost", "127.0.0.1", "127.1.2.3", "::1", "[::1]"} {
		if shouldPrintNetworkURL(host) {
			t.Errorf("loopback host %q should not print Network", host)
		}
	}
	for _, host := range []string{"0.0.0.0", "::", "192.0.2.25", "example.test"} {
		if !shouldPrintNetworkURL(host) {
			t.Errorf("non-loopback host %q should print Network", host)
		}
	}
}

func TestStartDoesNotPrintReadyWhenPortIsOccupied(t *testing.T) {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()

	port := strconv.Itoa(listener.Addr().(*net.TCPAddr).Port)
	var output bytes.Buffer
	err = Start(Config{
		Host:    "127.0.0.1",
		Port:    port,
		Token:   "fixed-token",
		EriiDir: t.TempDir(),
		Output:  &output,
	})
	if err == nil {
		t.Fatal("expected occupied port to fail")
	}
	if strings.Contains(output.String(), "READY") {
		t.Fatalf("READY was printed before the listener was acquired:\n%s", output.String())
	}
}
