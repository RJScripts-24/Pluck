package dev.pluck.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final MongoTemplate mongoTemplate;

    @Autowired(required = false)
    public HealthController(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "healthy");
        res.put("framework", "Spring Boot 3.4.3");
        res.put("database", "MongoDB");
        res.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> live() {
        Map<String, Object> res = new HashMap<>();
        res.put("status", "UP");
        res.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean dbOk = false;
        try {
            if (mongoTemplate != null) {
                mongoTemplate.executeCommand("{ ping: 1 }");
                dbOk = true;
            }
        } catch (Exception e) {
            dbOk = false;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("status", dbOk ? "READY" : "DEGRADED");
        res.put("databaseConnected", dbOk);
        res.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(res);
    }
}
