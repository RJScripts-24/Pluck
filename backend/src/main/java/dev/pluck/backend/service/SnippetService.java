package dev.pluck.backend.service;

import dev.pluck.backend.model.ActivityEvent;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.SnippetVersion;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.ActivityEventRepository;
import dev.pluck.backend.repository.SnippetRepository;
import dev.pluck.backend.repository.SnippetVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SnippetService {

    private final SnippetRepository snippetRepository;
    private final SnippetVersionRepository snippetVersionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final ASTService astService;
    private final SecureRandom secureRandom = new SecureRandom();

    public static class VersionConflictException extends RuntimeException {
        private final int currentVersion;

        public VersionConflictException(int currentVersion) {
            super("VERSION_CONFLICT: Base version does not match current version " + currentVersion);
            this.currentVersion = currentVersion;
        }

        public int getCurrentVersion() {
            return currentVersion;
        }
    }

    @Autowired
    public SnippetService(
            SnippetRepository snippetRepository,
            SnippetVersionRepository snippetVersionRepository,
            ActivityEventRepository activityEventRepository,
            ASTService astService
    ) {
        this.snippetRepository = snippetRepository;
        this.snippetVersionRepository = snippetVersionRepository;
        this.activityEventRepository = activityEventRepository;
        this.astService = astService;
    }

    private Map<String, Object> createMeta(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object val = (i + 1 < keyValues.length) ? keyValues[i + 1] : "";
            map.put(key, val != null ? val : "");
        }
        return map;
    }

    public String computeCodeHash(String code) {
        if (code == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    public String generateUniqueSlug(String workspaceId, String title) {
        String baseSlug = (title != null && !title.isBlank())
                ? title.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "")
                : "snippet";
        if (baseSlug.isBlank()) baseSlug = "snippet";

        String candidate = baseSlug;
        int suffix = 2;
        while (snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(workspaceId, candidate).isPresent()) {
            candidate = baseSlug + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    public String generateShareId() {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public Snippet createSnippet(
            String workspaceId,
            User author,
            String title,
            String description,
            String code,
            String language,
            String folderId,
            String visibility,
            List<String> tags,
            List<Object> variables
    ) {
        return createSnippet(workspaceId, author, title, description, code, language, folderId, visibility, tags, variables, "clean");
    }

    public Snippet createSnippet(
            String workspaceId,
            User author,
            String title,
            String description,
            String code,
            String language,
            String folderId,
            String visibility,
            List<String> tags,
            List<Object> variables,
            String secretScanStatus
    ) {
        if (code != null && code.length() > 100000) {
            throw new IllegalArgumentException("CODE_TOO_LARGE: Snippet code exceeds 100,000 characters limit.");
        }

        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";
        String authorId = (author != null) ? author.getId() : "usr_alex";
        String authorName = (author != null && author.getName() != null) ? author.getName() : "Alex Developer";

        String slug = generateUniqueSlug(wsId, title);
        String shareId = generateShareId();

        // Authoritative AST parsing
        ASTService.ASTAnalysisResult ast = astService.parseSnippet(language, code);

        Snippet snippet = new Snippet();
        snippet.setId("snp_" + UUID.randomUUID().toString().substring(0, 8));
        snippet.setWorkspaceId(wsId);
        snippet.setAuthorId(authorId);
        snippet.setFolderId((folderId != null && !folderId.isBlank()) ? folderId : "hooks");
        snippet.setFolderName((folderId != null && !folderId.isBlank()) ? folderId : "hooks");
        snippet.setTitle(title != null ? title.trim() : "Untitled Snippet");
        snippet.setDescription(description != null ? description.trim() : "");
        snippet.setSlug(slug);
        snippet.setCode(code != null ? code : "");
        snippet.setCodeHash(computeCodeHash(code));
        snippet.setLanguage(ast.detectedLanguage());
        snippet.setDependencies(ast.dependencies());
        snippet.setDependencyNames(ast.dependencyNames());
        snippet.setEnvKeys(ast.envKeys());
        snippet.setSymbols(ast.symbols());
        snippet.setRunnable(ast.isRunnable());
        snippet.setVisibility((visibility != null && !visibility.isBlank()) ? visibility : "private");
        snippet.setSecretScanStatus(secretScanStatus != null ? secretScanStatus : "clean");
        snippet.setShareId(shareId);
        snippet.setTags(tags != null ? tags : new ArrayList<>());
        snippet.setVariables(variables != null ? variables : new ArrayList<>());
        snippet.setVersion(1);
        snippet.setAiSummary("Indexed snippet: " + snippet.getTitle() + " in " + snippet.getLanguage() + " with " + ast.dependencies().size() + " dependencies.");

        Snippet saved = snippetRepository.save(snippet);

        // Snapshot initial version 1
        SnippetVersion v1 = new SnippetVersion(
                saved.getId(),
                1,
                saved.getTitle(),
                saved.getDescription(),
                saved.getLanguage(),
                saved.getCode()
        );
        v1.setDependencies(saved.getDependencies());
        v1.setEnvKeys(saved.getEnvKeys());
        v1.setVariables(saved.getVariables());
        v1.setAuthorId(authorId);
        snippetVersionRepository.save(v1);

        // Record activity event
        ActivityEvent event = new ActivityEvent(
                wsId,
                authorId,
                authorName,
                "SNIPPET_CREATED",
                saved.getId(),
                createMeta("title", saved.getTitle(), "slug", saved.getSlug(), "language", saved.getLanguage())
        );
        activityEventRepository.save(event);

        return saved;
    }

    public Snippet updateSnippet(
            String id,
            String workspaceId,
            User actor,
            Integer baseVersion,
            Map<String, Object> payload
    ) {
        Optional<Snippet> opt = getSnippetByIdOrSlug(id, workspaceId);
        if (opt.isEmpty()) {
            return null;
        }

        Snippet snippet = opt.get();

        // Optimistic Concurrency Control (OCC) Check
        if (baseVersion != null && baseVersion != snippet.getVersion()) {
            throw new VersionConflictException(snippet.getVersion());
        }

        boolean codeChanged = false;
        String newCode = snippet.getCode();

        if (payload.containsKey("title")) {
            snippet.setTitle((String) payload.get("title"));
        }
        if (payload.containsKey("description")) {
            snippet.setDescription((String) payload.get("description"));
        }
        if (payload.containsKey("code")) {
            newCode = (String) payload.get("code");
            if (newCode != null && newCode.length() > 100000) {
                throw new IllegalArgumentException("CODE_TOO_LARGE: Snippet code exceeds 100,000 characters limit.");
            }
            if (!Objects.equals(newCode, snippet.getCode())) {
                codeChanged = true;
                snippet.setCode(newCode);
                snippet.setCodeHash(computeCodeHash(newCode));
            }
        }
        if (payload.containsKey("language")) {
            snippet.setLanguage((String) payload.get("language"));
            codeChanged = true;
        }
        if (payload.containsKey("folderId")) {
            String fId = (String) payload.get("folderId");
            snippet.setFolderId(fId);
            snippet.setFolderName(fId);
        }
        if (payload.containsKey("visibility")) {
            snippet.setVisibility((String) payload.get("visibility"));
        }
        if (payload.containsKey("tags") && payload.get("tags") instanceof List) {
            snippet.setTags((List<String>) payload.get("tags"));
        }
        if (payload.containsKey("variables") && payload.get("variables") instanceof List) {
            snippet.setVariables((List<Object>) payload.get("variables"));
        }
        if (payload.containsKey("secretScanStatus") && payload.get("secretScanStatus") != null) {
            snippet.setSecretScanStatus((String) payload.get("secretScanStatus"));
        }

        // Re-run AST parser if code or language changed
        if (codeChanged) {
            ASTService.ASTAnalysisResult ast = astService.parseSnippet(snippet.getLanguage(), snippet.getCode());
            snippet.setLanguage(ast.detectedLanguage());
            snippet.setDependencies(ast.dependencies());
            snippet.setDependencyNames(ast.dependencyNames());
            snippet.setEnvKeys(ast.envKeys());
            snippet.setSymbols(ast.symbols());
            snippet.setRunnable(ast.isRunnable());
        }

        int nextVersion = snippet.getVersion() + 1;
        snippet.setVersion(nextVersion);
        snippet.setUpdatedAt(Instant.now().toString());

        Snippet saved = snippetRepository.save(snippet);

        // Snapshot new version
        SnippetVersion snapshot = new SnippetVersion(
                saved.getId(),
                nextVersion,
                saved.getTitle(),
                saved.getDescription(),
                saved.getLanguage(),
                saved.getCode()
        );
        snapshot.setDependencies(saved.getDependencies());
        snapshot.setEnvKeys(saved.getEnvKeys());
        snapshot.setVariables(saved.getVariables());
        snapshot.setAuthorId((actor != null) ? actor.getId() : saved.getAuthorId());
        snippetVersionRepository.save(snapshot);

        // Record activity event
        String actorId = (actor != null) ? actor.getId() : "usr_user";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "User";
        ActivityEvent event = new ActivityEvent(
                saved.getWorkspaceId(),
                actorId,
                actorName,
                "SNIPPET_UPDATED",
                saved.getId(),
                createMeta("title", saved.getTitle(), "version", nextVersion)
        );
        activityEventRepository.save(event);

        return saved;
    }

    public Optional<Snippet> getSnippetByIdOrSlug(String idOrSlug, String workspaceId) {
        if (idOrSlug == null || idOrSlug.isBlank()) return Optional.empty();

        // 1. Look up by ID
        Optional<Snippet> opt = snippetRepository.findByIdAndDeletedAtIsNull(idOrSlug);
        if (opt.isPresent()) return opt;

        // 2. Look up by Workspace & Slug
        if (workspaceId != null && !workspaceId.isBlank()) {
            opt = snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(workspaceId, idOrSlug);
            if (opt.isPresent()) return opt;
        }

        // 3. Global slug lookup
        opt = snippetRepository.findBySlug(idOrSlug);
        if (opt.isPresent() && opt.get().getDeletedAt() == null) {
            return opt;
        }

        return Optional.empty();
    }

    public boolean softDeleteSnippet(String id, String workspaceId, User actor) {
        Optional<Snippet> opt = getSnippetByIdOrSlug(id, workspaceId);
        if (opt.isEmpty()) return false;

        Snippet snippet = opt.get();
        snippet.setDeletedAt(Instant.now().toString());
        snippetRepository.save(snippet);

        String actorId = (actor != null) ? actor.getId() : "usr_user";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "User";
        ActivityEvent event = new ActivityEvent(
                snippet.getWorkspaceId(),
                actorId,
                actorName,
                "SNIPPET_DELETED",
                snippet.getId(),
                createMeta("title", snippet.getTitle(), "slug", snippet.getSlug())
        );
        activityEventRepository.save(event);

        return true;
    }

    public int incrementCopyCount(String id) {
        Optional<Snippet> opt = snippetRepository.findById(id);
        if (opt.isEmpty()) opt = snippetRepository.findBySlug(id);
        if (opt.isEmpty()) return 0;

        Snippet snippet = opt.get();
        snippet.setCopyCount(snippet.getCopyCount() + 1);
        snippetRepository.save(snippet);

        ActivityEvent event = new ActivityEvent(
                snippet.getWorkspaceId(),
                "anonymous",
                "Developer",
                "SNIPPET_COPIED",
                snippet.getId(),
                createMeta("title", snippet.getTitle(), "copyCount", snippet.getCopyCount())
        );
        activityEventRepository.save(event);

        return snippet.getCopyCount();
    }

    public Snippet forkSnippet(String id, String targetWorkspaceId, User actor) {
        Optional<Snippet> opt = snippetRepository.findById(id);
        if (opt.isEmpty()) opt = snippetRepository.findBySlug(id);
        if (opt.isEmpty()) return null;

        Snippet source = opt.get();
        source.setForkCount(source.getForkCount() + 1);
        snippetRepository.save(source);

        String wsId = (targetWorkspaceId != null && !targetWorkspaceId.isBlank()) ? targetWorkspaceId : "ws_personal";
        String forkedSlug = generateUniqueSlug(wsId, source.getTitle() + "-fork");

        Snippet forked = new Snippet();
        forked.setId("snp_" + UUID.randomUUID().toString().substring(0, 8));
        forked.setWorkspaceId(wsId);
        forked.setAuthorId((actor != null) ? actor.getId() : "usr_user");
        forked.setTitle(source.getTitle() + " (Fork)");
        forked.setDescription(source.getDescription());
        forked.setSlug(forkedSlug);
        forked.setCode(source.getCode());
        forked.setCodeHash(source.getCodeHash());
        forked.setLanguage(source.getLanguage());
        forked.setFolderId(source.getFolderId());
        forked.setFolderName(source.getFolderName());
        forked.setVisibility("private");
        forked.setTags(new ArrayList<>(source.getTags()));
        forked.setDependencies(new ArrayList<>(source.getDependencies()));
        forked.setDependencyNames(new ArrayList<>(source.getDependencyNames()));
        forked.setEnvKeys(new ArrayList<>(source.getEnvKeys()));
        forked.setSymbols(new ArrayList<>(source.getSymbols()));
        forked.setVariables(new ArrayList<>(source.getVariables()));
        forked.setForkedFromId(source.getId());
        forked.setShareId(generateShareId());
        forked.setVersion(1);

        Snippet savedFork = snippetRepository.save(forked);

        // Snapshot v1
        SnippetVersion v1 = new SnippetVersion(
                savedFork.getId(),
                1,
                savedFork.getTitle(),
                savedFork.getDescription(),
                savedFork.getLanguage(),
                savedFork.getCode()
        );
        snippetVersionRepository.save(v1);

        // Activity event
        String actorId = (actor != null) ? actor.getId() : "usr_user";
        String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "User";
        ActivityEvent event = new ActivityEvent(
                wsId,
                actorId,
                actorName,
                "SNIPPET_FORKED",
                savedFork.getId(),
                createMeta("sourceId", source.getId(), "title", savedFork.getTitle())
        );
        activityEventRepository.save(event);

        return savedFork;
    }

    public List<Snippet> listSnippets(
            String workspaceId,
            String folder,
            String lang,
            String tag,
            String pkg,
            String sort,
            int limit
    ) {
        List<Snippet> list;
        if (workspaceId != null && !workspaceId.isBlank()) {
            list = snippetRepository.findByWorkspaceIdAndDeletedAtIsNull(workspaceId);
        } else {
            list = snippetRepository.findAll().stream()
                    .filter(s -> s.getDeletedAt() == null)
                    .toList();
        }

        // Apply folder filter
        if (folder != null && !folder.isBlank() && !folder.equalsIgnoreCase("all")) {
            list = list.stream().filter(s -> folder.equalsIgnoreCase(s.getFolderId())).toList();
        }

        // Apply language filter
        if (lang != null && !lang.isBlank() && !lang.equalsIgnoreCase("all")) {
            list = list.stream().filter(s -> lang.equalsIgnoreCase(s.getLanguage())).toList();
        }

        // Apply tag filter
        if (tag != null && !tag.isBlank()) {
            list = list.stream().filter(s -> s.getTags() != null && s.getTags().contains(tag)).toList();
        }

        // Apply package filter
        if (pkg != null && !pkg.isBlank()) {
            list = list.stream().filter(s -> s.getDependencyNames() != null && s.getDependencyNames().contains(pkg)).toList();
        }

        // Apply sorting
        List<Snippet> sorted = new ArrayList<>(list);
        if ("title".equalsIgnoreCase(sort)) {
            sorted.sort(Comparator.comparing(Snippet::getTitle, String.CASE_INSENSITIVE_ORDER));
        } else if ("created".equalsIgnoreCase(sort)) {
            sorted.sort(Comparator.comparing(Snippet::getCreatedAt).reversed());
        } else {
            // Default: updated
            sorted.sort((a, b) -> {
                String ta = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                String tb = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                return tb.compareTo(ta);
            });
        }

        // Apply limit (default 50, max 100)
        int max = Math.min(Math.max(limit > 0 ? limit : 50, 1), 100);
        if (sorted.size() > max) {
            sorted = sorted.subList(0, max);
        }

        return sorted;
    }
}
