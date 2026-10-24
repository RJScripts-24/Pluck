package dev.pluck.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class RunnerService {

    public record RunRequest(
            String language,
            String code,
            String entryPoint,
            List<Object> args,
            Integer timeoutMs,
            Integer memoryLimitMb
    ) {}

    public record RunResponse(
            String status, // "SUCCESS", "TIMEOUT", "OOM", "ERROR"
            Object returnValue,
            List<String> stdout,
            List<String> stderr,
            double durationMs,
            double memoryMb,
            double memoryUsageMb,
            int exitCode,
            String runtime,
            boolean timedOut
    ) {}

    private final ObjectMapper objectMapper = new ObjectMapper();

    public RunResponse execute(RunRequest request) {
        String code = request.code() != null ? request.code() : "";
        String language = request.language() != null ? request.language() : "typescript";
        String entryPoint = request.entryPoint();
        List<Object> args = request.args() != null ? request.args() : Collections.emptyList();
        int timeoutMs = request.timeoutMs() != null ? Math.min(Math.max(request.timeoutMs(), 100), 10000) : 5000;
        int memoryLimitMb = request.memoryLimitMb() != null ? Math.min(Math.max(request.memoryLimitMb(), 16), 128) : 64;

        Path tempPayload = null;
        try {
            Path runnerScript = resolveSandboxRunnerScript();

            // Prepare payload
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("code", code);
            payloadMap.put("language", language);
            payloadMap.put("entryPoint", entryPoint);
            payloadMap.put("args", args);
            payloadMap.put("timeoutMs", timeoutMs);

            tempPayload = Files.createTempFile("pluck_payload_", ".json");
            Files.writeString(tempPayload, objectMapper.writeValueAsString(payloadMap), StandardCharsets.UTF_8);

            // Command: node --max-old-space-size=<memory> <runner.js> <payload.json>
            ProcessBuilder pb = new ProcessBuilder(
                    "node",
                    "--max-old-space-size=" + memoryLimitMb,
                    runnerScript.toString(),
                    tempPayload.toString()
            );

            // Isolate environment
            pb.environment().clear();
            pb.environment().put("NODE_ENV", "production");
            pb.environment().put("PATH", System.getenv("PATH") != null ? System.getenv("PATH") : "");
            pb.environment().put("SYSTEMROOT", System.getenv("SYSTEMROOT") != null ? System.getenv("SYSTEMROOT") : "C:\\Windows");

            long startTime = System.nanoTime();
            Process process = pb.start();

            // Hard timeout allowance (process-level limit + 1000ms buffer)
            boolean finished = process.waitFor(timeoutMs + 1000L, TimeUnit.MILLISECONDS);
            long durationNs = System.nanoTime() - startTime;
            double durationMs = Math.round((durationNs / 1_000_000.0) * 100.0) / 100.0;

            if (!finished) {
                process.destroyForcibly();
                return new RunResponse(
                        "TIMEOUT",
                        null,
                        Collections.emptyList(),
                        List.of("[TIMEOUT] Process forcibly killed after " + timeoutMs + "ms timeout."),
                        durationMs,
                        memoryLimitMb,
                        memoryLimitMb,
                        124,
                        "QuickJS / Node VM Sandbox (Terminated)",
                        true
                );
            }

            // Read stdout and stderr up to 64KB
            String stdoutStr = readStreamTruncated(process.getInputStream(), 65536);
            String stderrStr = readStreamTruncated(process.getErrorStream(), 65536);
            int exitCode = process.exitValue();

            // Check for OOM indicators
            if (stderrStr.contains("JavaScript heap out of memory") || stderrStr.contains("Allocation failed") || exitCode == 134) {
                return new RunResponse(
                        "OOM",
                        null,
                        Collections.emptyList(),
                        List.of("[OOM] Process exceeded memory boundary of " + memoryLimitMb + "MB."),
                        durationMs,
                        memoryLimitMb,
                        memoryLimitMb,
                        137,
                        "QuickJS / Node VM Sandbox (OOM)",
                        false
                );
            }

            // Look for delimiter __PLUCK_RESULT__ ... __PLUCK_RESULT_END__
            String delimiterStart = "__PLUCK_RESULT__";
            String delimiterEnd = "__PLUCK_RESULT_END__";
            if (stdoutStr.contains(delimiterStart) && stdoutStr.contains(delimiterEnd)) {
                int s = stdoutStr.indexOf(delimiterStart) + delimiterStart.length();
                int e = stdoutStr.indexOf(delimiterEnd, s);
                String jsonPart = stdoutStr.substring(s, e).trim();

                try {
                    JsonNode node = objectMapper.readTree(jsonPart);
                    String status = node.path("status").asText("SUCCESS");
                    int nodeExit = node.path("exitCode").asInt(exitCode);
                    boolean timedOut = node.path("timedOut").asBoolean(false);
                    double nodeDuration = node.path("durationMs").asDouble(durationMs);
                    double memory = node.path("memoryUsageMb").asDouble(1.25);
                    String runtime = node.path("runtime").asText("QuickJS / Node VM Sandbox");

                    List<String> stdoutList = new ArrayList<>();
                    if (node.has("stdout") && node.get("stdout").isArray()) {
                        for (JsonNode item : node.get("stdout")) {
                            stdoutList.add(item.asText());
                        }
                    }

                    List<String> stderrList = new ArrayList<>();
                    if (node.has("stderr") && node.get("stderr").isArray()) {
                        for (JsonNode item : node.get("stderr")) {
                            stderrList.add(item.asText());
                        }
                    }

                    Object returnValue = null;
                    if (node.has("returnValue") && !node.get("returnValue").isNull()) {
                        JsonNode val = node.get("returnValue");
                        if (val.isNumber()) {
                            returnValue = val.numberValue();
                        } else if (val.isBoolean()) {
                            returnValue = val.booleanValue();
                        } else if (val.isTextual()) {
                            returnValue = val.textValue();
                        } else {
                            returnValue = objectMapper.convertValue(val, Object.class);
                        }
                    }

                    return new RunResponse(
                            status,
                            returnValue,
                            stdoutList,
                            stderrList,
                            nodeDuration,
                            memory,
                            memory,
                            nodeExit,
                            runtime,
                            timedOut
                    );
                } catch (Exception parseEx) {
                    // Fall back to raw output
                }
            }

            // Fallback response if delimiter not found
            List<String> outLines = stdoutStr.isBlank() ? Collections.emptyList() : Arrays.asList(stdoutStr.split("\r?\n"));
            List<String> errLines = stderrStr.isBlank() ? Collections.emptyList() : Arrays.asList(stderrStr.split("\r?\n"));

            return new RunResponse(
                    exitCode == 0 ? "SUCCESS" : "ERROR",
                    null,
                    outLines,
                    errLines,
                    durationMs,
                    1.5,
                    1.5,
                    exitCode,
                    "QuickJS / Node VM Sandbox",
                    false
            );

        } catch (Exception ex) {
            return new RunResponse(
                    "ERROR",
                    null,
                    Collections.emptyList(),
                    List.of("[RUNNER FAULT] " + ex.getMessage()),
                    0.0,
                    0.0,
                    0.0,
                    1,
                    "QuickJS / Node VM Sandbox",
                    false
            );
        } finally {
            if (tempPayload != null) {
                try {
                    Files.deleteIfExists(tempPayload);
                } catch (IOException ignored) {}
            }
        }
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "READY");
        status.put("engine", "QuickJS / Node VM Subprocess Sandbox");
        status.put("limits", Map.of(
                "defaultTimeoutMs", 5000,
                "maxTimeoutMs", 10000,
                "defaultMemoryMb", 64,
                "maxMemoryMb", 128
        ));
        status.put("supportedLanguages", List.of("javascript", "typescript", "js", "ts"));
        return status;
    }

    private Path resolveSandboxRunnerScript() throws IOException {
        Path devPath = Path.of("src/main/resources/sandbox-runner.js");
        if (Files.exists(devPath)) {
            return devPath.toAbsolutePath();
        }
        Path targetPath = Path.of("target/classes/sandbox-runner.js");
        if (Files.exists(targetPath)) {
            return targetPath.toAbsolutePath();
        }

        Path tempRunner = Files.createTempFile("pluck_sandbox_runner_", ".js");
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sandbox-runner.js")) {
            if (is != null) {
                Files.copy(is, tempRunner, StandardCopyOption.REPLACE_EXISTING);
                tempRunner.toFile().deleteOnExit();
                return tempRunner;
            }
        }
        throw new FileNotFoundException("sandbox-runner.js script could not be located.");
    }

    private String readStreamTruncated(InputStream is, int maxBytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = is.read(buffer)) != -1) {
            int toWrite = Math.min(read, maxBytes - total);
            if (toWrite > 0) {
                baos.write(buffer, 0, toWrite);
                total += toWrite;
            }
            if (total >= maxBytes) break;
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
