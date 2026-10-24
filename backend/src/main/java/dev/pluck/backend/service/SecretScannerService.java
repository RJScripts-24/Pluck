package dev.pluck.backend.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SecretScannerService {

    public record SecretFinding(
            String ruleId,
            String severity, // "CRITICAL", "HIGH", "MEDIUM"
            int start,
            int end,
            int line,
            String preview,
            String suggestedEnvKey,
            String rawMatch
    ) {}

    public record ScanReport(
            boolean hasCritical,
            boolean hasHigh,
            List<SecretFinding> findings
    ) {}

    private record SecretRule(
            String ruleId,
            String severity,
            Pattern pattern,
            String suggestedEnvKey,
            int captureGroup
    ) {}

    private final List<SecretRule> rules = List.of(
            // Stripe Live Keys
            new SecretRule("STRIPE_LIVE_KEY", "CRITICAL",
                    Pattern.compile("\\b(sk_live_[0-9a-zA-Z]{24,})\\b"),
                    "STRIPE_SECRET_KEY", 1),

            // Stripe Restricted Keys
            new SecretRule("STRIPE_RESTRICTED_KEY", "CRITICAL",
                    Pattern.compile("\\b(rk_live_[0-9a-zA-Z]{24,})\\b"),
                    "STRIPE_RESTRICTED_KEY", 1),

            // GitHub Classic Personal Access Tokens
            new SecretRule("GITHUB_PAT", "CRITICAL",
                    Pattern.compile("\\b(ghp_[0-9a-zA-Z]{36,})\\b"),
                    "GITHUB_PAT_TOKEN", 1),

            // GitHub Fine-grained PATs
            new SecretRule("GITHUB_FINE_GRAINED_PAT", "CRITICAL",
                    Pattern.compile("\\b(github_pat_[0-9a-zA-Z_]{22,})\\b"),
                    "GITHUB_TOKEN", 1),

            // AWS Access Key IDs
            new SecretRule("AWS_ACCESS_KEY_ID", "HIGH",
                    Pattern.compile("\\b(AKIA[0-9A-Z]{16})\\b"),
                    "AWS_ACCESS_KEY_ID", 1),

            // AWS Secret Keys
            new SecretRule("AWS_SECRET_KEY", "CRITICAL",
                    Pattern.compile("(?i)aws[_-]?secret[_-]?access[_-]?key\\s*[:=]\\s*['\"]([A-Za-z0-9/+=]{40})['\"]"),
                    "AWS_SECRET_ACCESS_KEY", 1),

            // OpenAI API Keys
            new SecretRule("OPENAI_API_KEY", "CRITICAL",
                    Pattern.compile("\\b(sk-[a-zA-Z0-9]{48,})\\b"),
                    "OPENAI_API_KEY", 1),

            // Slack Tokens
            new SecretRule("SLACK_TOKEN", "HIGH",
                    Pattern.compile("\\b(xox[baprs]-[0-9a-zA-Z]{10,}-[0-9a-zA-Z]{10,}-[0-9a-zA-Z]{10,})\\b"),
                    "SLACK_TOKEN", 1),

            // Generic Bearer / API Keys assigned in code
            new SecretRule("GENERIC_API_KEY", "HIGH",
                    Pattern.compile("(?i)(?:api[_-]?key|secret[_-]?key|access[_-]?token|auth[_-]?token)\\s*[:=]\\s*['\"]([a-zA-Z0-9_\\-]{20,})['\"]"),
                    "API_KEY", 1),

            // JWT Tokens
            new SecretRule("JWT_TOKEN", "HIGH",
                    Pattern.compile("\\b(ey[A-Za-z0-9_-]{20,}\\.ey[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{20,})\\b"),
                    "JWT_TOKEN", 1),

            // Private Keys
            new SecretRule("PRIVATE_KEY", "CRITICAL",
                    Pattern.compile("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
                    "PRIVATE_KEY", 0)
    );

    public ScanReport scan(String code) {
        if (code == null || code.isBlank()) {
            return new ScanReport(false, false, List.of());
        }

        List<SecretFinding> findings = new ArrayList<>();
        Set<String> matchedSpans = new HashSet<>();

        // 1. Regex rule execution
        for (SecretRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(code);
            while (matcher.find()) {
                int start = matcher.start(rule.captureGroup());
                int end = matcher.end(rule.captureGroup());
                String raw = matcher.group(rule.captureGroup());

                String spanKey = start + ":" + end;
                if (matchedSpans.add(spanKey)) {
                    int line = calculateLineNumber(code, start);
                    String preview = maskSecret(raw);
                    findings.add(new SecretFinding(
                            rule.ruleId(),
                            rule.severity(),
                            start,
                            end,
                            line,
                            preview,
                            rule.suggestedEnvKey(),
                            raw
                    ));
                }
            }
        }

        // 2. Shannon Entropy Filter on String Literals
        scanHighEntropyStrings(code, findings, matchedSpans);

        // Sort by start index
        findings.sort(Comparator.comparingInt(SecretFinding::start));

        boolean hasCritical = findings.stream().anyMatch(f -> "CRITICAL".equalsIgnoreCase(f.severity()));
        boolean hasHigh = findings.stream().anyMatch(f -> "HIGH".equalsIgnoreCase(f.severity()));

        return new ScanReport(hasCritical, hasHigh, findings);
    }

    private void scanHighEntropyStrings(String code, List<SecretFinding> findings, Set<String> matchedSpans) {
        // Find string literals enclosed in quotes with length > 24
        Pattern strPattern = Pattern.compile("(['\"])([a-zA-Z0-9_\\-\\+/=]{25,})\\1");
        Matcher m = strPattern.matcher(code);
        while (m.find()) {
            int start = m.start(2);
            int end = m.end(2);
            String candidate = m.group(2);

            String spanKey = start + ":" + end;
            if (matchedSpans.contains(spanKey)) continue;

            // Calculate Shannon entropy
            double entropy = calculateShannonEntropy(candidate);
            // Threshold: length > 24 and entropy > 4.5
            if (entropy > 4.5) {
                // Ignore standard UUIDs or hex git hashes if entropy isn't random base64
                if (isUuidOrHex(candidate)) continue;

                matchedSpans.add(spanKey);
                int line = calculateLineNumber(code, start);
                findings.add(new SecretFinding(
                        "HIGH_ENTROPY_STRING",
                        "HIGH",
                        start,
                        end,
                        line,
                        maskSecret(candidate),
                        "SECRET_TOKEN",
                        candidate
                ));
            }
        }
    }

    public double calculateShannonEntropy(String text) {
        if (text == null || text.isEmpty()) return 0.0;
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : text.toCharArray()) {
            freq.put(c, freq.getOrDefault(c, 0) + 1);
        }

        double entropy = 0.0;
        int len = text.length();
        for (int count : freq.values()) {
            double p = (double) count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private boolean isUuidOrHex(String s) {
        // Pure lowercase hex 32 or 64 chars
        if (s.matches("^[0-9a-f]{32,64}$")) return true;
        // UUID with hyphens
        if (s.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) return true;
        return false;
    }

    private int calculateLineNumber(String text, int index) {
        int line = 1;
        for (int i = 0; i < Math.min(index, text.length()); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    public String maskSecret(String secret) {
        if (secret == null || secret.length() <= 8) return "********";
        int prefixLen = Math.min(6, secret.length() / 4);
        int suffixLen = Math.min(3, secret.length() / 6);
        return secret.substring(0, prefixLen) + "..." + secret.substring(secret.length() - suffixLen);
    }

    public Map<String, Object> redactCode(String code, String language) {
        if (code == null) {
            return Map.of("redactedCode", "", "findings", List.of(), "count", 0);
        }

        ScanReport report = scan(code);
        List<SecretFinding> findings = report.findings();
        if (findings.isEmpty()) {
            return Map.of("redactedCode", code, "findings", List.of(), "count", 0);
        }

        // Replace from right to left to keep start/end indices accurate
        List<SecretFinding> sortedReverse = new ArrayList<>(findings);
        sortedReverse.sort((a, b) -> Integer.compare(b.start(), a.start()));

        StringBuilder sb = new StringBuilder(code);
        for (SecretFinding f : sortedReverse) {
            String replacement;
            String envKey = f.suggestedEnvKey() != null ? f.suggestedEnvKey() : "API_KEY";

            if ("python".equalsIgnoreCase(language)) {
                replacement = "os.environ.get(\"" + envKey + "\")";
            } else {
                replacement = "process.env." + envKey;
            }

            // Check if match was already enclosed in quotes, e.g. "sk_live_..."
            int s = f.start();
            int e = f.end();
            if (s > 0 && e < sb.length()) {
                char before = sb.charAt(s - 1);
                char after = sb.charAt(e);
                if ((before == '\'' && after == '\'') || (before == '"' && after == '"')) {
                    // Replace quotes as well so it becomes process.env.KEY instead of "process.env.KEY"
                    s = s - 1;
                    e = e + 1;
                }
            }
            sb.replace(s, e, replacement);
        }

        // Strip raw matches before returning report findings to prevent leakage
        List<Map<String, Object>> safeFindings = new ArrayList<>();
        for (SecretFinding f : findings) {
            Map<String, Object> map = new HashMap<>();
            map.put("ruleId", f.ruleId());
            map.put("severity", f.severity());
            map.put("line", f.line());
            map.put("preview", f.preview());
            map.put("suggestedEnvKey", f.suggestedEnvKey());
            safeFindings.add(map);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("redactedCode", sb.toString());
        res.put("findings", safeFindings);
        res.put("count", safeFindings.size());
        return res;
    }
}
