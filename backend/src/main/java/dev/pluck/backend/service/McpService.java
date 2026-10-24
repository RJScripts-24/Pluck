package dev.pluck.backend.service;

import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.SnippetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class McpService {

    private final SnippetRepository snippetRepository;
    private final SnippetService snippetService;
    private final SearchService searchService;
    private final TemplateService templateService;
    private final ASTService astService;
    private final SecretScannerService secretScannerService;
    private final FolderRepository folderRepository;

    @Autowired
    public McpService(
            SnippetRepository snippetRepository,
            SnippetService snippetService,
            SearchService searchService,
            TemplateService templateService,
            ASTService astService,
            SecretScannerService secretScannerService,
            FolderRepository folderRepository
    ) {
        this.snippetRepository = snippetRepository;
        this.snippetService = snippetService;
        this.searchService = searchService;
        this.templateService = templateService;
        this.astService = astService;
        this.secretScannerService = secretScannerService;
        this.folderRepository = folderRepository;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> handleJsonRpc(Map<String, Object> request, User actor, String workspaceId) {
        Object id = request != null ? request.get("id") : 1;
        String method = request != null && request.get("method") != null ? String.valueOf(request.get("method")) : "";
        Map<String, Object> params = (request != null && request.get("params") instanceof Map)
                ? (Map<String, Object>) request.get("params")
                : Collections.emptyMap();

        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";

        switch (method) {
            case "initialize":
                return buildResponse(id, Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of(
                                "name", "pluck-mcp-server",
                                "version", "1.0.0"
                        )
                ));

            case "notifications/initialized":
            case "ping":
                return buildResponse(id, Map.of("status", "ok"));

            case "tools/list":
                return buildResponse(id, Map.of("tools", getDeclaredTools()));

            case "tools/call":
                return handleToolCall(id, params, actor, wsId);

            default:
                return buildError(id, -32601, "Method not found: " + method);
        }
    }

    public List<Map<String, Object>> getDeclaredTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // 1. search_snippets
        tools.add(Map.of(
                "name", "search_snippets",
                "description", "Search verified snippets across personal and team workspaces using hybrid semantic and keyword search.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Search query or task description (e.g. 'debounce hook' or 'rate limiter redis')"),
                                "language", Map.of("type", "string", "description", "Programming language filter (e.g. typescript, python, go)"),
                                "package", Map.of("type", "string", "description", "Package dependency filter (e.g. react, redis, boto3)"),
                                "limit", Map.of("type", "integer", "description", "Maximum snippets to return (default: 5)")
                        ),
                        "required", List.of("query")
                )
        ));

        // 2. get_snippet
        tools.add(Map.of(
                "name", "get_snippet",
                "description", "Fetch snippet by slug or ID with variable interpolation and package manager commands.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "ref", Map.of("type", "string", "description", "Snippet slug (e.g. 'use-debounce') or database ID"),
                                "variables", Map.of("type", "object", "description", "Optional key-value map of template variable overrides (e.g. {\"delay\": \"500\"})"),
                                "packageManager", Map.of("type", "string", "description", "Package manager for install command (npm, pnpm, yarn, bun, pip, uv, go, cargo)", "default", "npm")
                        ),
                        "required", List.of("ref")
                )
        ));

        // 3. list_snippets
        tools.add(Map.of(
                "name", "list_snippets",
                "description", "List accessible snippets filtered by folder, tag, or language.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "folder", Map.of("type", "string", "description", "Folder ID or slug"),
                                "tag", Map.of("type", "string", "description", "Tag filter"),
                                "language", Map.of("type", "string", "description", "Language filter"),
                                "limit", Map.of("type", "integer", "description", "Maximum snippets to return (default: 20)")
                        )
                )
        ));

        // 4. save_snippet
        tools.add(Map.of(
                "name", "save_snippet",
                "description", "Create a new snippet with secret scanning, AST analysis, and auto-generated package commands.",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string", "description", "Title of the snippet"),
                                "code", Map.of("type", "string", "description", "Source code content"),
                                "language", Map.of("type", "string", "description", "Programming language (typescript, python, go, rust, bash, etc.)"),
                                "description", Map.of("type", "string", "description", "Summary or purpose of snippet"),
                                "tags", Map.of("type", "array", "items", Map.of("type", "string"), "description", "List of tags")
                        ),
                        "required", List.of("title", "code", "language")
                )
        ));

        return tools;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolCall(Object id, Map<String, Object> params, User actor, String wsId) {
        String toolName = (String) params.get("name");
        Map<String, Object> args = (params.get("arguments") instanceof Map)
                ? (Map<String, Object>) params.get("arguments")
                : Collections.emptyMap();

        if (toolName == null || toolName.isBlank()) {
            return buildError(id, -32602, "Missing tool name in tools/call params");
        }

        switch (toolName) {
            case "search_snippets": {
                String query = (String) args.get("query");
                if (query == null || query.isBlank()) {
                    return buildError(id, -32602, "Argument 'query' is required for search_snippets");
                }
                int limit = args.containsKey("limit") && args.get("limit") instanceof Number n ? n.intValue() : 5;
                SearchService.SearchResponse searchResp = searchService.search(query, wsId, limit);

                StringBuilder sb = new StringBuilder();
                sb.append("Found ").append(searchResp.count()).append(" snippet(s) matching '").append(query).append("':\n\n");
                for (SearchService.SearchResultItem item : searchResp.results()) {
                    sb.append("- **").append(item.title()).append("** (slug: `").append(item.id()).append("`)\n");
                    sb.append("  Language: ").append(item.language()).append("\n");
                    if (item.aiSummary() != null && !item.aiSummary().isBlank()) {
                        sb.append("  Summary: ").append(item.aiSummary()).append("\n");
                    }
                    if (item.dependencies() != null && !item.dependencies().isEmpty()) {
                        List<String> depNames = item.dependencies().stream()
                                .map(d -> String.valueOf(d.get("name")))
                                .filter(n -> n != null && !n.isBlank())
                                .collect(Collectors.toList());
                        if (!depNames.isEmpty()) {
                            sb.append("  Install: `npm i ").append(String.join(" ", depNames)).append("`\n");
                        }
                    }
                    sb.append("\n");
                }

                return buildToolResult(id, sb.toString().trim(), false);
            }

            case "get_snippet": {
                String ref = (String) args.get("ref");
                if (ref == null || ref.isBlank()) {
                    return buildError(id, -32602, "Argument 'ref' is required for get_snippet");
                }

                Optional<Snippet> opt = snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(wsId, ref);
                if (opt.isEmpty()) {
                    opt = snippetRepository.findBySlug(ref);
                }
                if (opt.isEmpty()) {
                    opt = snippetRepository.findByIdAndDeletedAtIsNull(ref);
                }

                if (opt.isEmpty()) {
                    return buildToolResult(id, "Snippet not found for ref: " + ref, true);
                }

                Snippet snippet = opt.get();

                // Variables map
                Map<String, String> variables = new HashMap<>();
                if (args.get("variables") instanceof Map varMap) {
                    varMap.forEach((k, v) -> variables.put(String.valueOf(k), v != null ? String.valueOf(v) : ""));
                }

                // Package Manager
                String pm = args.containsKey("packageManager") ? String.valueOf(args.get("packageManager")) : "npm";

                // Interpolate
                TemplateService.RenderResult renderResult = templateService.renderTemplate(snippet.getCode(), variables, false);
                String renderedCode = renderResult.renderedCode();

                // Package install command
                Map<String, String> installCmds = astService.generateInstallCommands(snippet.getLanguage(), snippet.getDependencyNames());
                String installCmd = installCmds.getOrDefault(pm.toLowerCase(), installCmds.getOrDefault("npm", ""));

                // Record copy metric
                snippetService.incrementCopyCount(snippet.getSlug());

                StringBuilder sb = new StringBuilder();
                sb.append("## ").append(snippet.getTitle()).append(" (`").append(snippet.getSlug()).append("`)\n\n");
                if (snippet.getDescription() != null && !snippet.getDescription().isBlank()) {
                    sb.append(snippet.getDescription()).append("\n\n");
                }
                if (!installCmd.isBlank() && !installCmd.startsWith("#")) {
                    sb.append("**Install Dependencies:**\n```bash\n").append(installCmd).append("\n```\n\n");
                }
                if (snippet.getEnvKeys() != null && !snippet.getEnvKeys().isEmpty()) {
                    sb.append("**Required Environment Variables:** `")
                            .append(String.join("`, `", snippet.getEnvKeys()))
                            .append("`\n\n");
                }
                sb.append("**Source Code (").append(snippet.getLanguage()).append("):**\n");
                sb.append("```").append(snippet.getLanguage()).append("\n");
                sb.append(renderedCode).append("\n```");

                return buildToolResult(id, sb.toString(), false);
            }

            case "list_snippets": {
                int limit = args.containsKey("limit") && args.get("limit") instanceof Number n ? n.intValue() : 20;
                List<Snippet> list = snippetRepository.findByWorkspaceIdAndDeletedAtIsNull(wsId);
                if (list.isEmpty()) {
                    list = snippetRepository.findAll();
                }

                String languageFilter = (String) args.get("language");
                if (languageFilter != null && !languageFilter.isBlank()) {
                    list = list.stream()
                            .filter(s -> languageFilter.equalsIgnoreCase(s.getLanguage()))
                            .collect(Collectors.toList());
                }

                String tagFilter = (String) args.get("tag");
                if (tagFilter != null && !tagFilter.isBlank()) {
                    list = list.stream()
                            .filter(s -> s.getTags() != null && s.getTags().stream().anyMatch(t -> t.equalsIgnoreCase(tagFilter)))
                            .collect(Collectors.toList());
                }

                List<Snippet> paged = list.stream().limit(limit > 0 ? limit : 20).collect(Collectors.toList());

                StringBuilder sb = new StringBuilder();
                sb.append("Pluck Snippet Library (").append(paged.size()).append(" snippets):\n\n");
                for (Snippet s : paged) {
                    sb.append("- **").append(s.getTitle()).append("** (`").append(s.getSlug()).append("`)\n");
                    sb.append("  Language: ").append(s.getLanguage()).append(" | Copies: ").append(s.getCopyCount()).append("\n");
                    if (s.getTags() != null && !s.getTags().isEmpty()) {
                        sb.append("  Tags: ").append(String.join(", ", s.getTags())).append("\n");
                    }
                }

                return buildToolResult(id, sb.toString().trim(), false);
            }

            case "save_snippet": {
                String title = (String) args.get("title");
                String code = (String) args.get("code");
                String language = (String) args.get("language");
                String description = (String) args.get("description");
                List<String> tags = (args.get("tags") instanceof List tList)
                        ? (List<String>) tList
                        : new ArrayList<>();

                if (title == null || title.isBlank() || code == null || code.isBlank() || language == null || language.isBlank()) {
                    return buildError(id, -32602, "Arguments 'title', 'code', and 'language' are required for save_snippet");
                }

                // Check secrets
                SecretScannerService.ScanReport scanReport = secretScannerService.scan(code);
                if (scanReport.hasCritical() || scanReport.hasHigh()) {
                    String ruleId = scanReport.findings().isEmpty() ? "SUSPICIOUS_SECRET" : scanReport.findings().get(0).ruleId();
                    return buildToolResult(id, "Secret detected in snippet: " + ruleId + ". Save operation blocked for security.", true);
                }

                Snippet created = snippetService.createSnippet(
                        wsId,
                        actor,
                        title,
                        description != null ? description : "",
                        code,
                        language,
                        null,
                        "private",
                        tags,
                        null,
                        scanReport.findings().isEmpty() ? "clean" : "acknowledged"
                );

                String msg = "Snippet saved successfully!\n"
                        + "- Title: " + created.getTitle() + "\n"
                        + "- Slug: " + created.getSlug() + "\n"
                        + "- Language: " + created.getLanguage() + "\n"
                        + "- Version: v" + created.getVersion() + "\n"
                        + "- Visibility: private";

                return buildToolResult(id, msg, false);
            }

            default:
                return buildError(id, -32601, "Unknown tool: " + toolName);
        }
    }

    private Map<String, Object> buildToolResult(Object id, String text, boolean isError) {
        Map<String, Object> contentItem = new HashMap<>();
        contentItem.put("type", "text");
        contentItem.put("text", text);

        Map<String, Object> result = new HashMap<>();
        result.put("content", List.of(contentItem));
        result.put("isError", isError);

        return buildResponse(id, result);
    }

    private Map<String, Object> buildResponse(Object id, Object result) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("result", result);
        return resp;
    }

    private Map<String, Object> buildError(Object id, int code, String message) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("jsonrpc", "2.0");
        resp.put("id", id);
        resp.put("error", Map.of(
                "code", code,
                "message", message
        ));
        return resp;
    }
}
