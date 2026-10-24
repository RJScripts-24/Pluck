package dev.pluck.backend.service;

import dev.pluck.backend.model.Folder;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.SnippetVersion;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.SnippetRepository;
import dev.pluck.backend.repository.SnippetVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CliService {

    private final SnippetRepository snippetRepository;
    private final SnippetVersionRepository snippetVersionRepository;
    private final SnippetService snippetService;
    private final FolderRepository folderRepository;
    private final ASTService astService;
    private final TemplateService templateService;
    private final SecretScannerService secretScannerService;

    @Autowired
    public CliService(
            SnippetRepository snippetRepository,
            SnippetVersionRepository snippetVersionRepository,
            SnippetService snippetService,
            FolderRepository folderRepository,
            ASTService astService,
            TemplateService templateService,
            SecretScannerService secretScannerService
    ) {
        this.snippetRepository = snippetRepository;
        this.snippetVersionRepository = snippetVersionRepository;
        this.snippetService = snippetService;
        this.folderRepository = folderRepository;
        this.astService = astService;
        this.templateService = templateService;
        this.secretScannerService = secretScannerService;
    }

    public Map<String, Object> sync(String workspaceId, String since) {
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";

        List<Snippet> allSnippets = snippetRepository.findByWorkspaceIdAndDeletedAtIsNull(wsId);
        if (allSnippets.isEmpty()) {
            allSnippets = snippetRepository.findAll();
        }

        List<Snippet> filtered = allSnippets;
        if (since != null && !since.isBlank()) {
            filtered = allSnippets.stream()
                    .filter(s -> s.getUpdatedAt() != null && s.getUpdatedAt().compareTo(since) >= 0)
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> snippetDtos = filtered.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("slug", s.getSlug());
            map.put("title", s.getTitle());
            map.put("language", s.getLanguage());
            map.put("version", s.getVersion());
            map.put("hash", s.getCodeHash());
            map.put("folderId", s.getFolderId());
            map.put("visibility", s.getVisibility());
            map.put("copyCount", s.getCopyCount());
            map.put("updatedAt", s.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());

        List<Folder> folders = folderRepository.findByWorkspaceId(wsId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("workspace", wsId);
        resp.put("syncedAt", Instant.now().toString());
        resp.put("count", snippetDtos.size());
        resp.put("snippets", snippetDtos);
        resp.put("folders", folders);

        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> push(String workspaceId, User actor, Map<String, Object> body) {
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";

        String title = (String) body.get("title");
        String code = (String) body.get("code");
        String language = (String) body.get("language");
        String slug = (String) body.get("slug");
        String description = (String) body.getOrDefault("description", "");
        String folderId = (String) body.get("folderId");
        List<String> tags = (body.get("tags") instanceof List t) ? (List<String>) t : new ArrayList<>();
        int baseVersion = body.containsKey("version") && body.get("version") instanceof Number n ? n.intValue() : 0;

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Snippet code is required");
        }
        if (title == null || title.isBlank()) {
            title = (slug != null && !slug.isBlank()) ? slug : "CLI Snippet";
        }
        if (language == null || language.isBlank()) {
            language = astService.normalizeLanguage("", code);
        }

        // Secret scan check
        SecretScannerService.ScanReport scan = secretScannerService.scan(code);
        if (scan.hasCritical() || scan.hasHigh()) {
            String ruleId = scan.findings().isEmpty() ? "SUSPICIOUS_SECRET" : scan.findings().get(0).ruleId();
            throw new SecurityException("SECRET_DETECTED: Critical or high entropy secret detected (" + ruleId + ")");
        }

        // Check if exists
        Optional<Snippet> existingOpt = Optional.empty();
        if (slug != null && !slug.isBlank()) {
            existingOpt = snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(wsId, slug);
        }

        if (existingOpt.isPresent()) {
            Snippet snippet = existingOpt.get();

            // Optimistic concurrency check
            if (baseVersion > 0 && snippet.getVersion() != baseVersion) {
                throw new SnippetService.VersionConflictException(snippet.getVersion());
            }

            // Snapshot old version
            SnippetVersion versionSnapshot = new SnippetVersion(
                    snippet.getId(),
                    snippet.getVersion(),
                    snippet.getTitle(),
                    snippet.getDescription(),
                    snippet.getLanguage(),
                    snippet.getCode()
            );
            versionSnapshot.setDependencies(snippet.getDependencies());
            versionSnapshot.setVariables(snippet.getVariables());
            versionSnapshot.setAuthorId((actor != null) ? actor.getId() : "usr_alex");
            snippetVersionRepository.save(versionSnapshot);

            // Update snippet
            snippet.setTitle(title);
            snippet.setCode(code);
            snippet.setLanguage(language);
            snippet.setDescription(description);
            snippet.setCodeHash(snippetService.computeCodeHash(code));
            snippet.setSecretScanStatus(scan.findings().isEmpty() ? "clean" : "acknowledged");
            snippet.setVersion(snippet.getVersion() + 1);
            snippet.setUpdatedAt(Instant.now().toString());

            ASTService.ASTAnalysisResult ast = astService.parseSnippet(language, code);
            snippet.setDependencies(ast.dependencies());
            snippet.setDependencyNames(ast.dependencies().stream()
                    .map(d -> (String) d.get("name"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
            snippet.setEnvKeys(ast.envKeys());

            snippetRepository.save(snippet);

            return Map.of(
                    "success", true,
                    "action", "updated",
                    "slug", snippet.getSlug(),
                    "version", snippet.getVersion(),
                    "message", "Snippet updated successfully from CLI"
            );
        } else {
            Snippet created = snippetService.createSnippet(
                    wsId,
                    actor,
                    title,
                    description,
                    code,
                    language,
                    folderId,
                    "private",
                    tags,
                    null,
                    scan.findings().isEmpty() ? "clean" : "acknowledged"
            );

            return Map.of(
                    "success", true,
                    "action", "created",
                    "slug", created.getSlug(),
                    "version", created.getVersion(),
                    "message", "Snippet created successfully from CLI"
            );
        }
    }

    public Map<String, Object> resolve(String workspaceId, String ref, String pm) {
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";
        String packageManager = (pm != null && !pm.isBlank()) ? pm.toLowerCase() : "npm";

        Optional<Snippet> opt = snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(wsId, ref);
        if (opt.isEmpty()) {
            opt = snippetRepository.findBySlug(ref);
        }
        if (opt.isEmpty()) {
            opt = snippetRepository.findByIdAndDeletedAtIsNull(ref);
        }

        if (opt.isEmpty()) {
            throw new NoSuchElementException("Snippet not found for ref: " + ref);
        }

        Snippet snippet = opt.get();
        Map<String, String> installCmds = astService.generateInstallCommands(snippet.getLanguage(), snippet.getDependencyNames());
        String installCmd = installCmds.getOrDefault(packageManager, installCmds.getOrDefault("npm", ""));

        List<TemplateService.VariableDefinition> vars = templateService.extractVariables(snippet.getCode());

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", snippet.getId());
        resp.put("slug", snippet.getSlug());
        resp.put("title", snippet.getTitle());
        resp.put("language", snippet.getLanguage());
        resp.put("code", snippet.getCode());
        resp.put("installCommand", installCmd);
        resp.put("dependencies", snippet.getDependencies());
        resp.put("variables", vars);
        resp.put("env", snippet.getEnvKeys());
        resp.put("version", snippet.getVersion());

        return resp;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> pull(String workspaceId, Map<String, Object> body) {
        String ref = (String) body.get("ref");
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("Argument 'ref' is required");
        }

        String pm = (String) body.getOrDefault("pm", "npm");
        String customFilename = (String) body.get("filename");

        Map<String, String> vars = new HashMap<>();
        if (body.get("variables") instanceof Map vMap) {
            vMap.forEach((k, v) -> vars.put(String.valueOf(k), v != null ? String.valueOf(v) : ""));
        }

        Map<String, Object> resolved = resolve(workspaceId, ref, pm);
        String rawCode = (String) resolved.get("code");
        String language = (String) resolved.get("language");
        String slug = (String) resolved.get("slug");

        TemplateService.RenderResult renderResult = templateService.renderTemplate(rawCode, vars, false);

        String filename = customFilename;
        if (filename == null || filename.isBlank()) {
            String ext = getFileExtension(language);
            filename = slug + "." + ext;
        }

        snippetService.incrementCopyCount(slug);

        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("slug", slug);
        resp.put("filename", filename);
        resp.put("code", renderResult.renderedCode());
        resp.put("installCommand", resolved.get("installCommand"));
        resp.put("dependencies", resolved.get("dependencies"));
        resp.put("env", resolved.get("env"));

        return resp;
    }

    private String getFileExtension(String language) {
        if (language == null) return "txt";
        return switch (language.toLowerCase()) {
            case "typescript", "ts" -> "ts";
            case "javascript", "js" -> "js";
            case "python", "py" -> "py";
            case "go", "golang" -> "go";
            case "rust", "rs" -> "rs";
            case "java" -> "java";
            case "json" -> "json";
            case "html" -> "html";
            case "css" -> "css";
            case "bash", "sh" -> "sh";
            case "sql" -> "sql";
            default -> "txt";
        };
    }
}
