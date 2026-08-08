package main

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

type TranscribeResponse struct {
	Text       string `json:"text"`
	Language   string `json:"language,omitempty"`
	DurationMs int64  `json:"duration_ms"`
	Segments   []Segment `json:"segments"`
}

type Segment struct {
	StartMs int64  `json:"start_ms"`
	EndMs   int64  `json:"end_ms"`
	Text    string `json:"text"`
}

type MetadataRequest struct {
	Transcript string `json:"transcript"`
	DurationMs int64  `json:"duration_ms,omitempty"`
}

type MetadataResponse struct {
	Summary string   `json:"summary"`
	Tags    []string `json:"tags"`
	Notes   string   `json:"notes"`
}

var (
	serverAddr   = getEnv("SERVER_ADDR", ":8080")
	modelsDir    = getEnv("MODELS_DIR", "./models")
	llmURL       = getEnv("LLM_URL", "")
	maxDuration  = getEnvAsInt("MAX_DURATION", 3600)
	supportedLangs = map[string]bool{
		"en": true, "sv": true, "de": true, "fr": true, "es": true,
	}
)

func main() {
	http.HandleFunc("/health", healthHandler)
	http.HandleFunc("/v1/transcribe", transcribeHandler)
	http.HandleFunc("/v1/metadata", metadataHandler)

	log.Printf("Starting transcription server on %s", serverAddr)
	log.Printf("Models directory: %s", modelsDir)
	if llmURL != "" {
		log.Printf("LLM endpoint: %s", llmURL)
	} else {
		log.Println("LLM endpoint: disabled")
	}
	log.Fatal(http.ListenAndServe(serverAddr, nil))
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "ok"})
}

func transcribeHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	if err := r.ParseMultipartForm(32 << 20); err != nil { // 32MB max memory
		http.Error(w, "Invalid request: "+err.Error(), http.StatusBadRequest)
		return
	}

	model := r.FormValue("model")
	if model == "" {
		model = "default"
	}
	language := r.FormValue("language")

	file, _, err := r.FormFile("audio")
	if err != nil {
		http.Error(w, "Missing audio file", http.StatusBadRequest)
		return
	}
	defer file.Close()

	audioData, err := io.ReadAll(file)
	if err != nil {
		http.Error(w, "Invalid audio file: "+err.Error(), http.StatusBadRequest)
		return
	}

	log.Printf("Transcribe request: model=%s, lang=%s, size=%d", model, language, len(audioData))

	segments := generateMockSegments(language)
	response := TranscribeResponse{
		Text:       strings.Join(extractTexts(segments), " "),
		Language:   language,
		DurationMs: int64(len(audioData) / 32000 * 1000),
		Segments:   segments,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func metadataHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var req MetadataRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request: "+err.Error(), http.StatusBadRequest)
		return
	}

	log.Printf("Metadata request: transcript_length=%d", len(req.Transcript))

	var summary, notes string
	var tags []string

	if llmURL != "" {
		summary, tags, notes = generateMetadataWithLLM(req.Transcript)
	} else {
		summary = truncate(req.Transcript, 200)
		tags = extractTags(req.Transcript)
		notes = ""
	}

	response := MetadataResponse{
		Summary: summary,
		Tags:    tags,
		Notes:   notes,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func generateMockSegments(lang string) []Segment {
	now := time.Now()
	var segments []Segment
	for i := 0; i < 5; i++ {
		start := now.Add(time.Duration(i*10) * time.Second)
		end := start.Add(8 * time.Second)
		segments = append(segments, Segment{
			StartMs: start.UnixMilli(),
			EndMs:   end.UnixMilli(),
			Text:    "[transcribed segment " + string(rune('0'+i)) + "]",
		})
	}
	return segments
}

func extractTexts(segments []Segment) []string {
	texts := make([]string, len(segments))
	for i, s := range segments {
		texts[i] = s.Text
	}
	return texts
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}

func extractTags(text string) []string {
	words := strings.Fields(text)
	tagSet := make(map[string]bool)
	for _, w := range words {
		cleaned := strings.ToLower(strings.Trim(w, ".,!?;:\"'()[]{}"))
		if len(cleaned) > 3 && len(cleaned) < 20 {
			tagSet[cleaned] = true
		}
	}
	tags := make([]string, 0, len(tagSet))
	for t := range tagSet {
		tags = append(tags, t)
	}
	if len(tags) > 5 {
		tags = tags[:5]
	}
	return tags
}

func generateMetadataWithLLM(transcript string) (string, []string, string) {
	client := &http.Client{Timeout: 30 * time.Second}
	payload, _ := json.Marshal(map[string]string{
		"prompt": "Summarize this transcript in one sentence and extract 5 tags. Transcript: " + truncate(transcript, 1000),
		"model":  "default",
	})

	req, err := http.NewRequest("POST", llmURL+"/v1/chat/completions", strings.NewReader(string(payload)))
	if err != nil {
		return truncate(transcript, 200), extractTags(transcript), ""
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return truncate(transcript, 200), extractTags(transcript), ""
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	var result map[string]interface{}
	if err := json.Unmarshal(body, &result); err != nil {
		return truncate(transcript, 200), extractTags(transcript), ""
	}

	choices, ok := result["choices"].([]interface{})
	if !ok || len(choices) == 0 {
		return truncate(transcript, 200), extractTags(transcript), ""
	}
	choice := choices[0].(map[string]interface{})
	message := choice["message"].(map[string]interface{})
	content := message["content"].(string)

	return truncate(content, 200), extractTags(content), ""
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvAsInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		var n int
		if _, err := fmt.Sscanf(v, "%d", &n); err == nil {
			return n
		}
	}
	return fallback
}
