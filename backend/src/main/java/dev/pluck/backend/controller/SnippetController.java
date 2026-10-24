package dev.pluck.backend.controller;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.SnippetVersion;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.SnippetRepository;
import dev.pluck.backend.repository.SnippetVersionRepository;
import dev.pluck.backend.service.ASTService;
import dev.pluck.backend.service.SnippetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

import dev.pluck.backend.service.PermissionService;
import dev.pluck.backend.service.SecretScannerService;
import dev.pluck.backend.service.TemplateService;

@RestController
@RequestMapping("/api/snippets")
public class SnippetController {

    private final SnippetService snippetService;
    private final SnippetRepository snippetRepository;
    private final SnippetVersionRepository snippetVersionRepository;
    private final ASTService astService;
    private final SecretScannerService secretScannerService;
    private final TemplateService templateService;
    private final PermissionService permissionService;
    private final dev.pluck.backend.service.AuthService authService;
    private final dev.pluck.backend.repository.WorkspaceRepository workspaceRepository;

    @Autowired
    public SnippetController(
            SnippetService snippetService,
            SnippetRepository snippetRepository,
            SnippetVersionRepository snippetVersionRepository,
            ASTService astService,
            SecretScannerService secretScannerService,
            TemplateService templateService,
            PermissionService permissionService,
            dev.pluck.backend.service.AuthService authService,
            dev.pluck.backend.repository.WorkspaceRepository workspaceRepository
    ) {
        this.snippetService = snippetService;
        this.snippetRepository = snippetRepository;
        this.snippetVersionRepository = snippetVersionRepository;
        this.astService = astService;
        this.secretScannerService = secretScannerService;
        this.templateService = templateService;
        this.permissionService = permissionService;
        this.authService = authService;
        this.workspaceRepository = workspaceRepository;
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof PluckPrincipal principal) {
            return principal.getUser();
        } else if (auth != null && auth.getPrincipal() instanceof User u) {
            return u;
        }
        return null;
    }

    private String resolveWorkspaceId(String headerWorkspace) {
        User u = getAuthenticatedUser();
        if (u != null) {
            dev.pluck.backend.model.Workspace personalWs = authService.ensurePersonalWorkspace(u);
            String personalWsId = personalWs != null ? personalWs.getId() : ("ws_" + u.getId());

            // 1. If explicit header provided, verify the user owns it
            if (headerWorkspace != null && !headerWorkspace.isBlank() && !"ws_personal".equals(headerWorkspace.trim())) {
                String candidate = headerWorkspace.trim();
                Optional<dev.pluck.backend.model.Workspace> wsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(candidate);
                if (wsOpt.isPresent()) {
                    dev.pluck.backend.model.Workspace ws = wsOpt.get();
                    if (u.getId().equals(ws.getOwnerUserId())) {
                        return candidate;
                    }
                }
            }

            // 2. Fall back to principal's active workspace if valid
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof PluckPrincipal principal && principal.getActiveWorkspaceId() != null) {
                String activeWs = principal.getActiveWorkspaceId();
                if (!"ws_personal".equals(activeWs)) {
                    Optional<dev.pluck.backend.model.Workspace> wsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(activeWs);
                    if (wsOpt.isPresent() && u.getId().equals(wsOpt.get().getOwnerUserId())) {
                        return activeWs;
                    }
                }
            }

            // 3. Guaranteed personal vault belonging exclusively to authenticated user
            return personalWsId;
        }

        // Unauthenticated fallback
        if (headerWorkspace != null && !headerWorkspace.isBlank() && !"ws_personal".equals(headerWorkspace.trim())) {
            return headerWorkspace.trim();
        }
        return "ws_personal";
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllSnippets(
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs,
            @RequestParam(required = false) String folder,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false, name = "package") String pkg,
            @RequestParam(required = false, defaultValue = "updated") String sort,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) String cursor
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        List<Snippet> snippets = snippetService.listSnippets(wsId, folder, lang, tag, pkg, sort, limit);

        // Enrich snippets with install commands for instant UI display
        List<Map<String, Object>> enriched = snippets.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("slug", s.getSlug());
            map.put("title", s.getTitle());
            map.put("description", s.getDescription());
            map.put("language", s.getLanguage());
            map.put("code", s.getCode());
            map.put("folderId", s.getFolderId());
            map.put("folderName", s.getFolderName());
            map.put("visibility", s.getVisibility());
            map.put("tags", s.getTags());
            map.put("dependencies", s.getDependencies());
            map.put("dependencyNames", s.getDependencyNames());
            map.put("envKeys", s.getEnvKeys());
            map.put("symbols", s.getSymbols());
            map.put("isRunnable", s.isRunnable());
            map.put("variables", s.getVariables());
            map.put("shareId", s.getShareId());
            map.put("copyCount", s.getCopyCount());
            map.put("forkCount", s.getForkCount());
            map.put("version", s.getVersion());
            map.put("createdAt", s.getCreatedAt());
            map.put("updatedAt", s.getUpdatedAt());
            map.put("installCommands", astService.generateInstallCommands(s.getLanguage(), s.getDependencyNames()));
            return map;
        }).toList();

        Map<String, Object> response = new HashMap<>();
        response.put("snippets", enriched);
        response.put("total", enriched.size());
        response.put("workspaceId", wsId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSnippetById(
            @PathVariable String id,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        Optional<Snippet> opt = snippetService.getSnippetByIdOrSlug(id, wsId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "SNIPPET_NOT_FOUND",
                    "message", "Snippet '" + id + "' does not exist or has been deleted."
            ));
        }

        Snippet snippet = opt.get();
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("snippet", snippet);
        res.put("installCommands", astService.generateInstallCommands(snippet.getLanguage(), snippet.getDependencyNames()));
        return ResponseEntity.ok(res);
    }

    @PostMapping
    public ResponseEntity<?> createSnippet(
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs,
            @RequestBody Map<String, Object> payload
    ) {
        String title = (String) payload.getOrDefault("title", "");
        String code = (String) payload.getOrDefault("code", "");
        String language = (String) payload.getOrDefault("language", "typescript");
        String folderId = (String) payload.getOrDefault("folderId", "hooks");
        String visibility = (String) payload.getOrDefault("visibility", "private");
        String description = (String) payload.getOrDefault("description", "");

        if (title.trim().isEmpty() || code.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "TITLE_AND_CODE_REQUIRED",
                    "message", "Title and code are required."
            ));
        }

        List<String> tags = (payload.containsKey("tags") && payload.get("tags") instanceof List)
                ? (List<String>) payload.get("tags")
                : new ArrayList<>();

        List<Object> variables = (payload.containsKey("variables") && payload.get("variables") instanceof List)
                ? (List<Object>) payload.get("variables")
                : new ArrayList<>();

        User author = getAuthenticatedUser();
        String wsId = resolveWorkspaceId(headerWs);

        // RBAC check: Viewers cannot create or edit snippets
        if (!permissionService.canEdit(author, wsId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "Viewer role cannot create or edit snippets in this workspace."
            ));
        }

        // Server Secret Scanner Gating Guardrail
        SecretScannerService.ScanReport scanReport = secretScannerService.scan(code);
        String secretScanStatus = "clean";
        if (scanReport.hasCritical() || scanReport.hasHigh()) {
            List<Map<String, Object>> safeFindings = new ArrayList<>();
            for (SecretScannerService.SecretFinding f : scanReport.findings()) {
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

            if (!"private".equalsIgnoreCase(visibility)) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "error", "SECRETS_DETECTED",
                        "message", "Snippet contains sensitive credentials and cannot be saved with visibility '" + visibility + "'. Please redact before saving.",
                        "findings", safeFindings,
                        "gating", "BLOCKED"
                ));
            }

            boolean ack = Boolean.TRUE.equals(payload.get("acknowledgeSecrets")) ||
                    "true".equalsIgnoreCase(String.valueOf(payload.get("acknowledgeSecrets")));
            if (ack) {
                secretScanStatus = "acknowledged";
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                        "error", "SECRETS_DETECTED",
                        "message", "High entropy secret or API key detected in snippet. Please redact or acknowledge to save as private.",
                        "findings", safeFindings,
                        "gating", "REQUIRES_ACK"
                ));
            }
        }

        try {
            Snippet saved = snippetService.createSnippet(
                    wsId,
                    author,
                    title,
                    description,
                    code,
                    language,
                    folderId,
                    visibility,
                    tags,
                    variables,
                    secretScanStatus
            );

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("snippet", saved);
            res.put("installCommands", astService.generateInstallCommands(saved.getLanguage(), saved.getDependencyNames()));
            res.put("message", "Snippet created and saved successfully");
            return ResponseEntity.status(HttpStatus.CREATED).body(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "VALIDATION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateSnippet(
            @PathVariable String id,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs,
            @RequestBody Map<String, Object> payload
    ) {
        User actor = getAuthenticatedUser();
        String wsId = resolveWorkspaceId(headerWs);

        // RBAC check: Viewers cannot create or edit snippets
        if (!permissionService.canEdit(actor, wsId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "Viewer role cannot create or edit snippets in this workspace."
            ));
        }

        // Secret Scanner Gating Guardrail on updates to code or visibility
        if (payload.containsKey("code") || payload.containsKey("visibility")) {
            Optional<Snippet> opt = snippetService.getSnippetByIdOrSlug(id, wsId);
            if (opt.isPresent()) {
                Snippet existing = opt.get();
                String targetCode = payload.containsKey("code") ? (String) payload.get("code") : existing.getCode();
                String targetVis = payload.containsKey("visibility") ? (String) payload.get("visibility") : existing.getVisibility();
                SecretScannerService.ScanReport scanReport = secretScannerService.scan(targetCode);
                if (scanReport.hasCritical() || scanReport.hasHigh()) {
                    List<Map<String, Object>> safeFindings = new ArrayList<>();
                    for (SecretScannerService.SecretFinding f : scanReport.findings()) {
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

                    if (!"private".equalsIgnoreCase(targetVis)) {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                                "error", "SECRETS_DETECTED",
                                "message", "Snippet contains sensitive credentials and cannot be saved with visibility '" + targetVis + "'. Please redact before saving.",
                                "findings", safeFindings,
                                "gating", "BLOCKED"
                        ));
                    }

                    boolean ack = Boolean.TRUE.equals(payload.get("acknowledgeSecrets")) ||
                            "true".equalsIgnoreCase(String.valueOf(payload.get("acknowledgeSecrets")));
                    if (ack) {
                        payload.put("secretScanStatus", "acknowledged");
                    } else {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                                "error", "SECRETS_DETECTED",
                                "message", "High entropy secret or API key detected in snippet. Please redact or acknowledge to save as private.",
                                "findings", safeFindings,
                                "gating", "REQUIRES_ACK"
                        ));
                    }
                } else {
                    payload.put("secretScanStatus", "clean");
                }
            }
        }

        Integer baseVersion = null;
        if (payload.containsKey("baseVersion") && payload.get("baseVersion") instanceof Number num) {
            baseVersion = num.intValue();
        }

        try {
            Snippet updated = snippetService.updateSnippet(id, wsId, actor, baseVersion, payload);
            if (updated == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "SNIPPET_NOT_FOUND",
                        "message", "Snippet '" + id + "' does not exist."
                ));
            }

            Map<String, Object> res = new HashMap<>();
            res.put("success", true);
            res.put("snippet", updated);
            res.put("installCommands", astService.generateInstallCommands(updated.getLanguage(), updated.getDependencyNames()));
            return ResponseEntity.ok(res);
        } catch (SnippetService.VersionConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "VERSION_CONFLICT",
                    "currentVersion", e.getCurrentVersion(),
                    "message", "Snippet was updated concurrently. Please refresh before saving."
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "VALIDATION_FAILED",
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSnippet(
            @PathVariable String id,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        User actor = getAuthenticatedUser();
        String wsId = resolveWorkspaceId(headerWs);

        // RBAC check: Viewers cannot delete snippets
        if (!permissionService.canEdit(actor, wsId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "FORBIDDEN",
                    "message", "Viewer role cannot delete snippets in this workspace."
            ));
        }

        boolean deleted = snippetService.softDeleteSnippet(id, wsId, actor);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "SNIPPET_NOT_FOUND",
                    "message", "Snippet '" + id + "' not found."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Snippet deleted successfully"
        ));
    }

    @PostMapping("/{id}/events/copy")
    public ResponseEntity<?> recordCopyEvent(@PathVariable String id) {
        int copyCount = snippetService.incrementCopyCount(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "copyCount", copyCount,
                "message", "Copy count incremented"
        ));
    }

    @PostMapping("/{id}/fork")
    public ResponseEntity<?> forkSnippet(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        User actor = getAuthenticatedUser();
        String targetWs = (body != null && body.containsKey("targetWorkspace")) ? body.get("targetWorkspace") : "ws_personal";

        Snippet forked = snippetService.forkSnippet(id, targetWs, actor);
        if (forked == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "SNIPPET_NOT_FOUND",
                    "message", "Snippet '" + id + "' not found to fork."
            ));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "snippet", forked,
                "message", "Snippet forked to personal vault"
        ));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<?> getSnippetVersions(
            @PathVariable String id,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        Optional<Snippet> opt = snippetService.getSnippetByIdOrSlug(id, wsId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SNIPPET_NOT_FOUND"));
        }

        Snippet s = opt.get();
        List<SnippetVersion> snapshots = snippetVersionRepository.findBySnippetIdOrderByVersionDesc(s.getId());

        List<Map<String, Object>> versions = new ArrayList<>();
        for (SnippetVersion v : snapshots) {
            Map<String, Object> item = new HashMap<>();
            item.put("version", "v" + v.getVersion() + (v.getVersion() == s.getVersion() ? " (Current)" : ""));
            item.put("versionNumber", v.getVersion());
            item.put("code", v.getCode());
            item.put("title", v.getTitle());
            item.put("createdAt", v.getCreatedAt());
            item.put("isCurrent", v.getVersion() == s.getVersion());
            versions.add(item);
        }

        return ResponseEntity.ok(Map.of(
                "snippetId", s.getId(),
                "versions", versions,
                "currentVersion", s.getVersion()
        ));
    }

    @PostMapping("/{id}/versions/{version}/restore")
    public ResponseEntity<?> restoreSnippetVersion(
            @PathVariable String id,
            @PathVariable int version,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        Optional<Snippet> opt = snippetService.getSnippetByIdOrSlug(id, wsId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "SNIPPET_NOT_FOUND"));
        }

        Snippet s = opt.get();
        Optional<SnippetVersion> target = snippetVersionRepository.findBySnippetIdAndVersion(s.getId(), version);
        if (target.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "VERSION_NOT_FOUND",
                    "message", "Version " + version + " does not exist."
            ));
        }

        User actor = getAuthenticatedUser();
        SnippetVersion targetVersion = target.get();
        Snippet restored = snippetService.updateSnippet(s.getId(), wsId, actor, null, Map.of(
                "code", targetVersion.getCode(),
                "title", targetVersion.getTitle()
        ));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "restoredVersion", version,
                "newVersion", restored.getVersion(),
                "snippet", restored
        ));
    }

    // Backward-compatible route for restore
    @PostMapping("/{id}/restore/{version}")
    public ResponseEntity<?> legacyRestoreSnippetVersion(
            @PathVariable String id,
            @PathVariable String version,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs
    ) {
        int verNum = 1;
        try {
            verNum = Integer.parseInt(version.replace("v", "").replace(" (Current)", "").trim());
        } catch (NumberFormatException ignored) {}
        return restoreSnippetVersion(id, verNum, headerWs);
    }

    @PostMapping("/parse")
    public ResponseEntity<?> parseSnippetLive(@RequestBody Map<String, String> body) {
        String language = body.getOrDefault("language", "typescript");
        String code = body.getOrDefault("code", "");

        ASTService.ASTAnalysisResult ast = astService.parseSnippet(language, code);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("detectedLanguage", ast.detectedLanguage());
        res.put("dependencies", ast.dependencies());
        res.put("dependencyNames", ast.dependencyNames());
        res.put("installCommands", ast.installCommands());
        res.put("envKeys", ast.envKeys());
        res.put("symbols", ast.symbols());
        res.put("isRunnable", ast.isRunnable());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{ref}/render")
    public ResponseEntity<?> renderSnippet(
            @PathVariable String ref,
            @RequestHeader(value = "X-Workspace-Id", required = false) String headerWs,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String wsId = resolveWorkspaceId(headerWs);
        Map<String, String> values = new HashMap<>();
        boolean strict = false;
        String rawCode = null;
        String language = "typescript";

        if (body != null) {
            if (body.containsKey("values") && body.get("values") instanceof Map map) {
                map.forEach((k, v) -> values.put(String.valueOf(k), v != null ? String.valueOf(v) : ""));
            }
            if (body.containsKey("strict") && body.get("strict") instanceof Boolean b) {
                strict = b;
            }
            if (body.containsKey("code") && body.get("code") instanceof String c) {
                rawCode = c;
            }
            if (body.containsKey("language") && body.get("language") instanceof String l) {
                language = l;
            }
        }

        // If raw code wasn't passed in body, lookup snippet by ref (id or slug)
        if (rawCode == null || rawCode.isBlank()) {
            Optional<Snippet> opt = snippetService.getSnippetByIdOrSlug(ref, wsId);
            if (opt.isEmpty()) {
                opt = snippetRepository.findBySlug(ref).or(() -> snippetRepository.findById(ref));
            }
            if (opt.isPresent()) {
                Snippet s = opt.get();
                rawCode = s.getCode();
                language = s.getLanguage();
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "SNIPPET_NOT_FOUND",
                        "message", "Snippet '" + ref + "' not found."
                ));
            }
        }

        TemplateService.RenderResult render = templateService.renderTemplate(rawCode, values, strict);
        ASTService.ASTAnalysisResult ast = astService.parseSnippet(language, render.renderedCode());

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("code", render.renderedCode());
        res.put("missingVariables", render.missingVariables());
        res.put("variables", render.variables());
        res.put("envKeys", ast.envKeys());
        res.put("dependencies", ast.dependencies());
        res.put("installCommands", ast.installCommands());
        res.put("install", astService.generateInstallCommands(language, ast.dependencyNames()));
        return ResponseEntity.ok(res);
    }
}
