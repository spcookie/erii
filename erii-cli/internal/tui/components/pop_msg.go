package components

// PopScreenMsg is sent by a screen to request the root navigator to pop it.
type PopScreenMsg struct{}

// RefreshMsg is sent by the root navigator after popping a screen,
// so the new top screen can reload its data if needed.
type RefreshMsg struct{}
