package dev.pluck.backend.service;

import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.repository.SnippetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class SearchServiceTest {

    private SnippetRepository snippetRepository;
    private AIService aiService;
    private SearchService searchService;

    private Snippet sDebounce;
    private Snippet sThrottle;
    private Snippet sAuth;

    @BeforeEach
    void setUp() {
        snippetRepository = Mockito.mock(SnippetRepository.class);
        aiService = new AIService();
        searchService = new SearchService(snippetRepository, aiService);

        sDebounce = new Snippet();
        sDebounce.setId("use-debounce");
        sDebounce.setSlug("use-debounce");
        sDebounce.setTitle("useDebounce Hook");
        sDebounce.setLanguage("typescript");
        sDebounce.setCode("export function useDebounce(val, delay) { ... }");
        sDebounce.setAiSummary("Debounces rapidly changing values with a configurable timeout in React 18.");
        sDebounce.setAiKeywords(List.of("debounce", "delay", "timing", "typeahead", "input-delay", "stop-repeated-calls"));
        sDebounce.setTags(List.of("react", "hooks"));
        sDebounce.setCopyCount(120);
        sDebounce.setEmbedding(aiService.generateEmbedding("useDebounce React delay typing"));

        sThrottle = new Snippet();
        sThrottle.setId("use-throttle");
        sThrottle.setSlug("use-throttle");
        sThrottle.setTitle("useThrottle Hook");
        sThrottle.setLanguage("typescript");
        sThrottle.setCode("export function useThrottle(fn, limit) { ... }");
        sThrottle.setAiSummary("Enforces strict execution rate limit over high-frequency function invocations.");
        sThrottle.setAiKeywords(List.of("throttle", "scroll", "rate-limit"));
        sThrottle.setTags(List.of("react", "hooks"));
        sThrottle.setCopyCount(25);
        sThrottle.setEmbedding(aiService.generateEmbedding("useThrottle React scroll rate limit"));

        sAuth = new Snippet();
        sAuth.setId("jwt-auth-guard");
        sAuth.setSlug("jwt-auth-guard");
        sAuth.setTitle("JWT Auth Guard");
        sAuth.setLanguage("typescript");
        sAuth.setCode("export function verifyToken(token) { ... }");
        sAuth.setAiSummary("Validates and decodes cryptographic bearer tokens for secure identity verification.");
        sAuth.setAiKeywords(List.of("auth", "jwt", "token", "security"));
        sAuth.setTags(List.of("security", "backend"));
        sAuth.setCopyCount(5);
        sAuth.setEmbedding(aiService.generateEmbedding("JWT token authentication security guard"));

        when(snippetRepository.findByWorkspaceIdAndDeletedAtIsNull(anyString()))
                .thenReturn(List.of(sDebounce, sThrottle, sAuth));
    }

    @Test
    @DisplayName("Stage 7: Global ⌘K palette queries return relevant snippets in under 200ms")
    void testPaletteQuerySpeedAndRelevance() {
        SearchService.SearchResponse response = searchService.search("debounce", "ws_personal", 10);

        assertNotNull(response);
        assertFalse(response.results().isEmpty());
        assertEquals("use-debounce", response.results().get(0).id());
        assertTrue(response.latencyMs() < 200, "Search latency must be well under 200ms");
    }

    @Test
    @DisplayName("Stage 7: Intent search (e.g. 'stop repeated api calls') finds use-debounce via semantic keywords")
    void testIntentSearch() {
        SearchService.SearchResponse response = searchService.search("stop repeated api calls", "ws_personal", 10);

        assertNotNull(response);
        assertFalse(response.results().isEmpty());
        SearchService.SearchResultItem topResult = response.results().get(0);

        assertEquals("use-debounce", topResult.id());
        assertTrue(topResult.matchedBy().contains("semantic") || topResult.matchedBy().contains("keyword"));
        assertTrue(topResult.relevanceScore() >= 50);
    }

    @Test
    @DisplayName("Stage 7: Boost factor prioritizes snippets with higher copyCount")
    void testCopyCountBoost() {
        Snippet popular = new Snippet();
        popular.setId("cache-popular");
        popular.setTitle("Cache Helper");
        popular.setAiKeywords(List.of("cache", "memory"));
        popular.setCopyCount(1000);
        popular.setEmbedding(aiService.generateEmbedding("Cache Helper memory"));

        Snippet fresh = new Snippet();
        fresh.setId("cache-fresh");
        fresh.setTitle("Cache Helper");
        fresh.setAiKeywords(List.of("cache", "memory"));
        fresh.setCopyCount(0);
        fresh.setEmbedding(aiService.generateEmbedding("Cache Helper memory"));

        when(snippetRepository.findByWorkspaceIdAndDeletedAtIsNull("ws_boost"))
                .thenReturn(List.of(fresh, popular));

        SearchService.SearchResponse response = searchService.search("cache", "ws_boost", 10);

        assertNotNull(response);
        assertEquals(2, response.results().size());
        assertEquals("cache-popular", response.results().get(0).id(), "Popular snippet must rank #1 due to RRF copyCount boost");
    }
}
