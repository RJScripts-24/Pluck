package dev.pluck.backend.service;

import dev.pluck.backend.model.Folder;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.SnippetRepository;
import dev.pluck.backend.repository.SnippetVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CliServiceTest {

    private SnippetRepository snippetRepository;
    private SnippetVersionRepository snippetVersionRepository;
    private SnippetService snippetService;
    private FolderRepository folderRepository;
    private ASTService astService;
    private TemplateService templateService;
    private SecretScannerService secretScannerService;
    private CliService cliService;

    private User actor;

    @BeforeEach
    void setUp() {
        snippetRepository = mock(SnippetRepository.class);
        snippetVersionRepository = mock(SnippetVersionRepository.class);
        snippetService = mock(SnippetService.class);
        folderRepository = mock(FolderRepository.class);
        astService = new ASTService();
        templateService = new TemplateService();
        secretScannerService = new SecretScannerService();

        cliService = new CliService(
                snippetRepository,
                snippetVersionRepository,
                snippetService,
                folderRepository,
                astService,
                templateService,
                secretScannerService
        );

        actor = new User("usr_alex", "Alex Developer", "alex@pluck.dev", "hash", "owner");
    }

    @Test
    @DisplayName("Stage 9: CLI sync returns snippet manifest and folders")
    void testCliSync() {
        Snippet snp = new Snippet();
        snp.setId("snp_1");
        snp.setSlug("use-debounce");
        snp.setTitle("useDebounce Hook");
        snp.setLanguage("typescript");
        snp.setVersion(1);
        snp.setCodeHash("hash123");
        snp.setUpdatedAt("2026-10-15T12:00:00Z");

        when(snippetRepository.findByWorkspaceIdAndDeletedAtIsNull("ws_personal"))
                .thenReturn(List.of(snp));

        Folder folder = new Folder("fld_hooks", "React Hooks", "⚡", 1, "ws_personal");
        when(folderRepository.findByWorkspaceId("ws_personal")).thenReturn(List.of(folder));

        Map<String, Object> sync = cliService.sync("ws_personal", null);
        assertNotNull(sync);
        assertEquals("ws_personal", sync.get("workspace"));
        assertEquals(1, sync.get("count"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snippets = (List<Map<String, Object>>) sync.get("snippets");
        assertEquals("use-debounce", snippets.get(0).get("slug"));
    }

    @Test
    @DisplayName("Stage 9: CLI push rejects raw secrets with SecurityException")
    void testCliPushRejectsSecret() {
        Map<String, Object> body = Map.of(
                "title", "AWS S3 Config",
                "language", "typescript",
                "code", "const aws = 'AKIAIOSFODNN7EXAMPLE';"
        );

        SecurityException ex = assertThrows(SecurityException.class, () -> {
            cliService.push("ws_personal", actor, body);
        });

        assertTrue(ex.getMessage().contains("SECRET_DETECTED"));
    }

    @Test
    @DisplayName("Stage 9: CLI push creates new snippet successfully")
    void testCliPushCreatesNewSnippet() {
        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull("ws_personal", "redis-ratelimit"))
                .thenReturn(Optional.empty());

        Snippet created = new Snippet();
        created.setId("snp_redis");
        created.setSlug("redis-ratelimit");
        created.setTitle("Redis Rate Limiter");
        created.setVersion(1);

        when(snippetService.createSnippet(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        Map<String, Object> body = Map.of(
                "slug", "redis-ratelimit",
                "title", "Redis Rate Limiter",
                "language", "typescript",
                "code", "export function rateLimit() { return true; }"
        );

        Map<String, Object> result = cliService.push("ws_personal", actor, body);
        assertEquals(true, result.get("success"));
        assertEquals("created", result.get("action"));
        assertEquals("redis-ratelimit", result.get("slug"));
    }

    @Test
    @DisplayName("Stage 9: CLI pull interpolates variables and returns file payload")
    void testCliPull() {
        Snippet snp = new Snippet();
        snp.setId("snp_debounce");
        snp.setSlug("use-debounce");
        snp.setTitle("useDebounce Hook");
        snp.setLanguage("typescript");
        snp.setCode("export const delay = {{delay|300}};");
        snp.setDependencies(List.of(Map.of("name", "react", "type", "regular")));
        snp.setDependencyNames(List.of("react"));

        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull("ws_personal", "use-debounce"))
                .thenReturn(Optional.of(snp));

        Map<String, Object> body = Map.of(
                "ref", "use-debounce",
                "variables", Map.of("delay", "1000"),
                "pm", "bun"
        );

        Map<String, Object> pull = cliService.pull("ws_personal", body);
        assertEquals(true, pull.get("success"));
        assertEquals("use-debounce.ts", pull.get("filename"));
        assertEquals("export const delay = 1000;", pull.get("code"));
        assertEquals("bun add react", pull.get("installCommand"));
    }
}
