package web

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"
)

// GenerateToken creates a random 12-character hex token.
func GenerateToken() string {
	b := make([]byte, 6)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// TokenAuth returns HTTP middleware that validates ?token= query parameter.
func TokenAuth(token string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.URL.Query().Get("token") != token {
				http.Error(w, "Unauthorized", http.StatusUnauthorized)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}
