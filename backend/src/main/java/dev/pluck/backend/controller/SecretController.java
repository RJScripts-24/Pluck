package dev.pluck.backend.controller;

import dev.pluck.backend.service.SecretScannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/secrets")
public class SecretController {

    private final SecretScannerService secretScannerService;

    @Autowired
    public SecretController(SecretScannerService secretScannerService) {
        this.secretScannerService = secretScannerService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody Map<String, String> body) {
        String code = body != null ? body.getOrDefault("code", "") : "";
        SecretScannerService.ScanReport report = secretScannerService.scan(code);

        List<Map<String, Object>> safeFindings = new ArrayList<>();
        for (SecretScannerService.SecretFinding f : report.findings()) {
            Map<String, Object> map = new HashMap<>();
            map.put("ruleId", f.ruleId());
            map.put("severity", f.severity());
            map.put("line", f.line());
            map.put("start", f.start());
            map.put("end", f.end());
            map.put("preview", f.preview());
            map.put("suggestedEnvKey", f.suggestedEnvKey());
            safeFindings.add(map);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("hasCritical", report.hasCritical());
        res.put("hasHigh", report.hasHigh());
        res.put("findings", safeFindings);
        res.put("count", safeFindings.size());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/redact")
    public ResponseEntity<?> redact(@RequestBody Map<String, Object> body) {
        String code = (body != null && body.containsKey("code")) ? (String) body.get("code") : "";
        String language = (body != null && body.containsKey("language")) ? (String) body.get("language") : "typescript";

        Map<String, Object> result = secretScannerService.redactCode(code, language);

        Map<String, Object> res = new HashMap<>(result);
        res.put("success", true);
        res.put("message", "All detected secrets transformed to process.env references");
        return ResponseEntity.ok(res);
    }
}
