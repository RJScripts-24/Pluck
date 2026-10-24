package dev.pluck.backend.service;

import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.repository.SnippetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    public record SearchResultItem(
            String id,
            String title,
            String language,
            String code,
            String folderId,
            List<Map<String, Object>> dependencies,
            String aiSummary,
            List<String> aiKeywords,
            String designPattern,
            List<String> matchedBy,
            int relevanceScore,
            double rrfScore,
            int copyCount
    ) {}

    public record SearchResponse(
            List<SearchResultItem> results,
            String query,
            int count,
            long latencyMs
    ) {}

    private final SnippetRepository snippetRepository;
    private final AIService aiService;

    @Autowired
    public SearchService(SnippetRepository snippetRepository, AIService aiService) {
        this.snippetRepository = snippetRepository;
        this.aiService = aiService;
    }

    public SearchResponse search(String query, String workspaceId, int limit) {
        long start = System.currentTimeMillis();
        String q = (query != null) ? query.trim() : "";
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";

        List<Snippet> candidates = snippetRepository.findByWorkspaceIdAndDeletedAtIsNull(wsId);
        if (candidates.isEmpty()) {
            candidates = snippetRepository.findAll();
        }

        // Return top snippets if query is empty
        if (q.isEmpty()) {
            List<SearchResultItem> top = candidates.stream()
                    .sorted((a, b) -> Integer.compare(b.getCopyCount(), a.getCopyCount()))
                    .limit(limit > 0 ? limit : 20)
                    .map(s -> new SearchResultItem(
                            s.getId(),
                            s.getTitle(),
                            s.getLanguage(),
                            s.getCode(),
                            s.getFolderId(),
                            s.getDependencies(),
                            s.getAiSummary(),
                            s.getAiKeywords(),
                            s.getDesignPattern(),
                            List.of("recent"),
                            100,
                            1.0,
                            s.getCopyCount()
                    ))
                    .collect(Collectors.toList());

            long latency = System.currentTimeMillis() - start;
            return new SearchResponse(top, "", top.size(), Math.max(1, latency));
        }

        // ── Arm 1: Vector Cosine Similarity (Weight: 1.0) ──
        List<Double> queryVec = aiService.generateEmbedding(q);
        Map<String, Double> vectorScores = new HashMap<>();

        for (Snippet s : candidates) {
            List<Double> docVec = s.getEmbedding();
            if (docVec == null || docVec.isEmpty()) {
                String fullText = (s.getTitle() != null ? s.getTitle() : "") + " " +
                        (s.getAiSummary() != null ? s.getAiSummary() : "") + " " +
                        (s.getAiKeywords() != null ? String.join(" ", s.getAiKeywords()) : "");
                docVec = aiService.generateEmbedding(fullText);
            }
            double sim = aiService.cosineSimilarity(queryVec, docVec);
            vectorScores.put(s.getId(), sim);
        }

        // ── Arm 2: Keyword & Token Match (Field Weights) ──
        Map<String, Double> keywordScores = new HashMap<>();
        String[] qTokens = q.toLowerCase().split("\\s+");

        for (Snippet s : candidates) {
            double kwScore = 0.0;
            String titleLower = s.getTitle() != null ? s.getTitle().toLowerCase() : "";
            String codeLower = s.getCode() != null ? s.getCode().toLowerCase() : "";
            String summaryLower = s.getAiSummary() != null ? s.getAiSummary().toLowerCase() : "";
            List<String> tags = s.getTags() != null ? s.getTags() : Collections.emptyList();
            List<String> aiKw = s.getAiKeywords() != null ? s.getAiKeywords() : Collections.emptyList();

            for (String token : qTokens) {
                if (token.length() < 2) continue;
                // Title match: weight 10
                if (titleLower.contains(token)) kwScore += 10.0;
                // AI Keywords match: weight 8
                for (String k : aiKw) {
                    if (k.toLowerCase().contains(token) || token.contains(k.toLowerCase())) {
                        kwScore += 8.0;
                    }
                }
                // AI Summary / description match: weight 6
                if (summaryLower.contains(token)) kwScore += 6.0;
                // Tags match: weight 4
                for (String t : tags) {
                    if (t.toLowerCase().contains(token)) kwScore += 4.0;
                }
                // Code match: weight 2
                if (codeLower.contains(token)) kwScore += 2.0;
            }

            // Semantic synonyms & intent expansion
            if (isDebounceIntent(q) && (titleLower.contains("debounce") || summaryLower.contains("debounce") || containsToken(aiKw, "debounce"))) {
                kwScore += 18.0;
            }
            if (isThrottleIntent(q) && (titleLower.contains("throttle") || summaryLower.contains("throttle") || containsToken(aiKw, "throttle"))) {
                kwScore += 18.0;
            }

            keywordScores.put(s.getId(), kwScore);
        }

        // ── Arm 3: Trigram Match (Typo-tolerant Substring Matching, Weight: 0.5) ──
        Map<String, Double> trigramScores = new HashMap<>();
        Set<String> queryTrigrams = extractTrigrams(q);

        for (Snippet s : candidates) {
            String target = (s.getTitle() != null ? s.getTitle() : "") + " " + (s.getSlug() != null ? s.getSlug() : "");
            Set<String> docTrigrams = extractTrigrams(target);
            double trigramSim = calculateJaccardSimilarity(queryTrigrams, docTrigrams);
            trigramScores.put(s.getId(), trigramSim);
        }

        // Rank each arm: 1-indexed ranks
        Map<String, Integer> vectorRanks = computeRanks(vectorScores, candidates);
        Map<String, Integer> keywordRanks = computeRanks(keywordScores, candidates);
        Map<String, Integer> trigramRanks = computeRanks(trigramScores, candidates);

        // ── Reciprocal Rank Fusion (RRF) ──
        // Score = (1.0 / (60 + rank_vec)) + (1.0 / (60 + rank_kw)) + (0.5 / (60 + rank_tri)) + 0.003 * ln(1 + copyCount)
        List<SearchResultItem> scoredResults = new ArrayList<>();

        for (Snippet s : candidates) {
            double vScore = vectorScores.getOrDefault(s.getId(), 0.0);
            double kScore = keywordScores.getOrDefault(s.getId(), 0.0);
            double tScore = trigramScores.getOrDefault(s.getId(), 0.0);

            // Filter out snippets with zero relevance across all arms
            if (vScore < 0.08 && kScore < 1.0 && tScore < 0.08) {
                continue;
            }

            int rankV = vectorRanks.getOrDefault(s.getId(), 999);
            int rankK = keywordRanks.getOrDefault(s.getId(), 999);
            int rankT = trigramRanks.getOrDefault(s.getId(), 999);

            double copyCountBoost = 0.003 * Math.log(1.0 + Math.max(0, s.getCopyCount()));
            double rrf = (1.0 / (60.0 + rankV)) +
                         (1.0 / (60.0 + rankK)) +
                         (0.5 / (60.0 + rankT)) +
                         copyCountBoost;

            List<String> matchedBy = new ArrayList<>();
            if (vScore > 0.15 || rankV <= 5) matchedBy.add("semantic");
            if (kScore > 2.0 || rankK <= 5) matchedBy.add("keyword");
            if (tScore > 0.15 || rankT <= 5) matchedBy.add("fuzzy");
            if (matchedBy.isEmpty()) matchedBy.add("keyword");

            int relevanceScore = Math.min(99, Math.max(15, (int) Math.round(rrf * 3600.0)));

            scoredResults.add(new SearchResultItem(
                    s.getId(),
                    s.getTitle(),
                    s.getLanguage(),
                    s.getCode(),
                    s.getFolderId(),
                    s.getDependencies(),
                    s.getAiSummary(),
                    s.getAiKeywords(),
                    s.getDesignPattern(),
                    matchedBy,
                    relevanceScore,
                    rrf,
                    s.getCopyCount()
            ));
        }

        // Sort by RRF score descending
        scoredResults.sort((a, b) -> Double.compare(b.rrfScore(), a.rrfScore()));

        int targetLimit = limit > 0 ? Math.min(limit, scoredResults.size()) : scoredResults.size();
        List<SearchResultItem> paged = scoredResults.subList(0, targetLimit);

        long latency = System.currentTimeMillis() - start;
        return new SearchResponse(paged, q, scoredResults.size(), Math.max(1, latency));
    }

    private boolean isDebounceIntent(String q) {
        String lower = q.toLowerCase();
        return lower.contains("stop repeated") ||
               lower.contains("delay typing") ||
               lower.contains("typeahead") ||
               lower.contains("too many calls") ||
               lower.contains("avoid flooding") ||
               lower.contains("wait typing");
    }

    private boolean isThrottleIntent(String q) {
        String lower = q.toLowerCase();
        return lower.contains("scroll") ||
               lower.contains("rate limit") ||
               lower.contains("limit frequency") ||
               lower.contains("drag") ||
               lower.contains("fps");
    }

    private boolean containsToken(List<String> list, String token) {
        if (list == null) return false;
        for (String s : list) {
            if (s.toLowerCase().contains(token)) return true;
        }
        return false;
    }

    private Set<String> extractTrigrams(String text) {
        if (text == null || text.length() < 3) return Collections.emptySet();
        String clean = text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        Set<String> trigrams = new HashSet<>();
        for (int i = 0; i <= clean.length() - 3; i++) {
            trigrams.add(clean.substring(i, i + 3));
        }
        return trigrams;
    }

    private double calculateJaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Map<String, Integer> computeRanks(Map<String, Double> scores, List<Snippet> items) {
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        Map<String, Integer> ranks = new HashMap<>();
        int rank = 1;
        for (Map.Entry<String, Double> entry : sorted) {
            ranks.put(entry.getKey(), rank++);
        }
        return ranks;
    }
}
