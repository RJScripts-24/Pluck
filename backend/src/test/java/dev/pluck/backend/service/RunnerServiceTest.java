package dev.pluck.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RunnerServiceTest {

    private RunnerService runnerService;

    @BeforeEach
    void setUp() {
        runnerService = new RunnerService();
    }

    @Test
    @DisplayName("Stage 6: Executes TypeScript function and returns correct returnValue")
    void testExecuteTypeScriptFunction() {
        String code = """
            export function add(a: number, b: number): number {
                return a + b;
            }
            """;

        RunnerService.RunRequest request = new RunnerService.RunRequest(
                "typescript",
                code,
                "add",
                List.of(2, 3),
                5000,
                64
        );

        RunnerService.RunResponse response = runnerService.execute(request);

        assertEquals("SUCCESS", response.status());
        assertEquals(5, ((Number) response.returnValue()).intValue());
        assertFalse(response.timedOut());
        assertEquals(0, response.exitCode());
    }

    @Test
    @DisplayName("Stage 6: Captures console.log in stdout array")
    void testCaptureConsoleLogs() {
        String code = """
            console.log("Processing item", 101);
            console.log("Finished computation");
            return { processed: true, count: 101 };
            """;

        RunnerService.RunRequest request = new RunnerService.RunRequest(
                "javascript",
                code,
                null,
                List.of(),
                5000,
                64
        );

        RunnerService.RunResponse response = runnerService.execute(request);

        assertEquals("SUCCESS", response.status());
        assertFalse(response.stdout().isEmpty());
        assertTrue(response.stdout().stream().anyMatch(s -> s.contains("Processing item 101")));
        assertTrue(response.stdout().stream().anyMatch(s -> s.contains("Finished computation")));
        assertNotNull(response.returnValue());
    }

    @Test
    @DisplayName("Stage 6: Infinite loop triggers timeout and safely terminates process")
    void testInfiniteLoopTimeout() {
        String code = "while (true) {}";

        // Use 1000ms timeout for rapid test execution
        RunnerService.RunRequest request = new RunnerService.RunRequest(
                "javascript",
                code,
                null,
                List.of(),
                1000,
                64
        );

        long start = System.currentTimeMillis();
        RunnerService.RunResponse response = runnerService.execute(request);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("TIMEOUT", response.status());
        assertTrue(response.timedOut());
        assertTrue(elapsed >= 900 && elapsed <= 3000, "Should terminate promptly after timeout");
    }

    @Test
    @DisplayName("Stage 6: Syntax error in code returns status ERROR with stderr details")
    void testSyntaxErrorHandling() {
        String code = "function unclosed() {";

        RunnerService.RunRequest request = new RunnerService.RunRequest(
                "javascript",
                code,
                null,
                List.of(),
                3000,
                64
        );

        RunnerService.RunResponse response = runnerService.execute(request);

        assertEquals("ERROR", response.status());
        assertFalse(response.stderr().isEmpty());
    }

    @Test
    @DisplayName("Stage 6: Status endpoint returns runner readiness and supported languages")
    void testRunnerStatus() {
        var status = runnerService.getStatus();
        assertEquals("READY", status.get("status"));
        assertTrue(status.containsKey("limits"));
        assertTrue(status.containsKey("supportedLanguages"));
    }
}
