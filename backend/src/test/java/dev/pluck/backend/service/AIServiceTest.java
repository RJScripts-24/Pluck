package dev.pluck.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AIServiceTest {

    private AIService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AIService();
    }

    @Test
    @DisplayName("Stage 7: Generates 1536-dimensional unit vector embedding")
    void testGenerateEmbeddingDimensions() {
        String text = "Debounces rapidly changing values with a configurable timeout in React 18";
        List<Double> embedding = aiService.generateEmbedding(text);

        assertNotNull(embedding);
        assertEquals(1536, embedding.size());

        // Verify L2 norm is approximately 1.0
        double sumSq = 0.0;
        for (double val : embedding) {
            sumSq += val * val;
        }
        double norm = Math.sqrt(sumSq);
        assertEquals(1.0, norm, 0.05, "Embedding vector must be L2-normalized");
    }

    @Test
    @DisplayName("Stage 7: Cosine similarity produces higher score for semantically related text")
    void testCosineSimilarity() {
        List<Double> vecDebounce = aiService.generateEmbedding("React debounce hook delay typing");
        List<Double> vecTypeahead = aiService.generateEmbedding("Input delay typeahead wait keystroke");
        List<Double> vecDatabase = aiService.generateEmbedding("PostgreSQL SQL connection pool transaction");

        double simRelated = aiService.cosineSimilarity(vecDebounce, vecTypeahead);
        double simUnrelated = aiService.cosineSimilarity(vecDebounce, vecDatabase);

        assertTrue(simRelated > simUnrelated, "Related concepts should have higher cosine similarity than unrelated concepts");
        assertEquals(1.0, aiService.cosineSimilarity(vecDebounce, vecDebounce), 0.01, "Self-similarity must be ~1.0");
    }

    @Test
    @DisplayName("Stage 7: Analyzes snippet design pattern, keywords, and use-cases")
    void testAnalyzeSnippet() {
        String title = "useDebounce";
        String code = "export function useDebounce(value, delay) { const [val, setVal] = useState(value); }";

        Map<String, Object> analysis = aiService.analyzeSnippet(title, code, "typescript", "");

        assertNotNull(analysis);
        assertTrue(analysis.containsKey("embedding"));
        assertEquals(1536, ((List<?>) analysis.get("embedding")).size());
        assertEquals("Debounce Decorator / Rate-Limiting Hook", analysis.get("designPattern"));

        List<?> keywords = (List<?>) analysis.get("keywords");
        assertFalse(keywords.isEmpty());
        assertTrue(keywords.contains("debounce"));
    }
}
