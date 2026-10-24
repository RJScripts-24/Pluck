package dev.pluck.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SecretScannerServiceTest {

    private SecretScannerService scanner;

    @BeforeEach
    void setUp() {
        scanner = new SecretScannerService();
    }

    @Test
    @DisplayName("Detects Stripe Live Secret Key and classifies as CRITICAL")
    void testDetectStripeKey() {
        String dummyStripeKey = String.join("_", "sk", "live", "51M0abcdef1234567890abcdef12345");
        String code = "const stripe = require('stripe')('" + dummyStripeKey + "');";
        SecretScannerService.ScanReport report = scanner.scan(code);

        assertTrue(report.hasCritical());
        assertFalse(report.findings().isEmpty());

        SecretScannerService.SecretFinding finding = report.findings().stream()
                .filter(f -> "STRIPE_LIVE_KEY".equals(f.ruleId()))
                .findFirst()
                .orElse(null);

        assertNotNull(finding);
        assertEquals("CRITICAL", finding.severity());
        assertEquals("STRIPE_SECRET_KEY", finding.suggestedEnvKey());
        assertTrue(finding.preview().startsWith("sk_liv"));
        assertTrue(finding.preview().contains("..."));
        assertFalse(finding.preview().contains("51M0abcdef1234567890abcdef"));
    }

    @Test
    @DisplayName("Detects GitHub PAT and AWS Access Key")
    void testDetectGithubAndAws() {
        String dummyGh = String.join("_", "ghp", "1234567890abcdefghijklmnopqrstuvwxyzAB");
        String code = "const ghToken = \"" + dummyGh + "\";\nconst awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n";
        SecretScannerService.ScanReport report = scanner.scan(code);

        assertTrue(report.hasCritical()); // GitHub PAT is CRITICAL
        assertTrue(report.hasHigh());     // AWS Key is HIGH
        assertEquals(2, report.findings().size());
    }

    @Test
    @DisplayName("Shannon entropy filter catches high entropy random tokens but ignores standard UUIDs")
    void testShannonEntropyAndUuidIgnore() {
        // High entropy pseudo-random secret string
        String highEntropyCode = "const token = \"aB9kL2vN8xZ0qW3mY7pT1oR4uI6eA8dC\";";
        SecretScannerService.ScanReport highEntropyReport = scanner.scan(highEntropyCode);
        assertFalse(highEntropyReport.findings().isEmpty());

        // Standard UUID format
        String uuidCode = "const id = \"123e4567-e89b-12d3-a456-426614174000\";";
        SecretScannerService.ScanReport uuidReport = scanner.scan(uuidCode);
        assertTrue(uuidReport.findings().isEmpty(), "Standard UUID should be ignored as non-secret");
    }

    @Test
    @DisplayName("Redaction engine replaces detected secret with process.env for TypeScript")
    void testRedactionTypeScript() {
        String dummyStripeKey = String.join("_", "sk", "live", "51M0abcdef1234567890abcdef12345");
        String code = "const apiKey = \"" + dummyStripeKey + "\";";
        Map<String, Object> result = scanner.redactCode(code, "typescript");

        assertEquals(1, result.get("count"));
        String redacted = (String) result.get("redactedCode");
        assertEquals("const apiKey = process.env.STRIPE_SECRET_KEY;", redacted);
    }

    @Test
    @DisplayName("Redaction engine replaces detected secret with os.environ.get for Python")
    void testRedactionPython() {
        String dummyOpenAi = "sk-" + "abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMN";
        String code = "client = OpenAI(api_key=\"" + dummyOpenAi + "\")";
        Map<String, Object> result = scanner.redactCode(code, "python");

        assertEquals(1, result.get("count"));
        String redacted = (String) result.get("redactedCode");
        assertTrue(redacted.contains("os.environ.get(\"OPENAI_API_KEY\")"));
    }

    @Test
    @DisplayName("Clean code returns zero findings and false flags")
    void testCleanCode() {
        String code = """
            import React, { useState } from 'react';
            export function Counter() {
                const [count, setCount] = useState(0);
                return <button onClick={() => setCount(c => c + 1)}>{count}</button>;
            }
            """;
        SecretScannerService.ScanReport report = scanner.scan(code);
        assertFalse(report.hasCritical());
        assertFalse(report.hasHigh());
        assertTrue(report.findings().isEmpty());
    }
}
