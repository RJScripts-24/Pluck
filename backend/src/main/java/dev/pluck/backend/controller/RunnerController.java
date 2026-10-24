package dev.pluck.backend.controller;

import dev.pluck.backend.service.RunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runner")
public class RunnerController {

    private final RunnerService runnerService;

    @Autowired
    public RunnerController(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @PostMapping("/execute")
    public ResponseEntity<?> executeSnippet(@RequestBody Map<String, Object> body) {
        String code = (String) body.getOrDefault("code", "");
        String language = (String) body.getOrDefault("language", "typescript");

        // Parse entry point (flat or nested under entry)
        String entryPoint = null;
        List<Object> args = new ArrayList<>();
        if (body.containsKey("entryPoint")) {
            entryPoint = (String) body.get("entryPoint");
        } else if (body.containsKey("entry") && body.get("entry") instanceof Map entryMap) {
            entryPoint = (String) entryMap.get("name");
            if (entryMap.containsKey("args") && entryMap.get("args") instanceof List l) {
                args = l;
            }
        }
        if (body.containsKey("args") && body.get("args") instanceof List l) {
            args = l;
        }

        // Parse limits (flat or nested under limits)
        Integer timeoutMs = 5000;
        Integer memoryLimitMb = 64;
        if (body.containsKey("timeoutMs") && body.get("timeoutMs") instanceof Number num) {
            timeoutMs = num.intValue();
        }
        if (body.containsKey("memoryLimitMb") && body.get("memoryLimitMb") instanceof Number num) {
            memoryLimitMb = num.intValue();
        }
        if (body.containsKey("limits") && body.get("limits") instanceof Map limitsMap) {
            if (limitsMap.containsKey("timeoutMs") && limitsMap.get("timeoutMs") instanceof Number num) {
                timeoutMs = num.intValue();
            }
            if (limitsMap.containsKey("memoryLimitMb") && limitsMap.get("memoryLimitMb") instanceof Number num) {
                memoryLimitMb = num.intValue();
            }
        }

        RunnerService.RunRequest request = new RunnerService.RunRequest(
                language,
                code,
                entryPoint,
                args,
                timeoutMs,
                memoryLimitMb
        );

        RunnerService.RunResponse response = runnerService.execute(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getRunnerStatus() {
        return ResponseEntity.ok(runnerService.getStatus());
    }
}
