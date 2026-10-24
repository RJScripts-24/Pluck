package dev.pluck.backend.service;

import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.SnippetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class McpServiceTest {

    private SnippetRepository snippetRepository;
    private SnippetService snippetService;
    private SearchService searchService;
    private TemplateService templateService;
    private ASTService astService;
    private SecretScannerService secretScannerService;
    private FolderRepository folderRepository;
    private McpService mcpService;

    private User actor;

    @BeforeEach
    void setUp() {
        snippetRepository = mock(SnippetRepository.class);
        snippetService = mock(SnippetService.class);
        searchService = mock(SearchService.class);
        templateService = new TemplateService();
        astService = new ASTService();
        secretScannerService = new SecretScannerService();
        folderRepository = mock(FolderRepository.class);

        mcpService = new McpService(
                snippetRepository,
                snippetService,
                searchService,
                templateService,
                astService,
                secretScannerService,
                folderRepository
        );

        actor = new User("usr_alex", "Alex Developer", "alex@pluck.dev", "hash", "owner");
    }

    @Test
    @DisplayName("Stage 9: MCP initialize returns protocol version and server capabilities")
    void testInitialize() {
        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Collections.emptyMap()
        );

        Map<String, Object> resp = mcpService.handleJsonRpc(req, actor, "ws_personal");
        assertNotNull(resp);
        assertEquals("2.0", resp.get("jsonrpc"));
        assertEquals(1, resp.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertNotNull(result);
        assertEquals("2024-11-05", result.get("protocolVersion"));

        @SuppressWarnings("unchecked")
        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertEquals("pluck-mcp-server", serverInfo.get("name"));
    }

    @Test
    @DisplayName("Stage 9: MCP tools/list returns all 4 Core Tools")
    void testToolsList() {
        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", "req-list",
                "method", "tools/list",
                "params", Collections.emptyMap()
        );

        Map<String, Object> resp = mcpService.handleJsonRpc(req, actor, "ws_personal");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(4, tools.size());

        Set<String> toolNames = Set.of(
                "search_snippets",
                "get_snippet",
                "list_snippets",
                "save_snippet"
        );
        for (Map<String, Object> tool : tools) {
            assertTrue(toolNames.contains(tool.get("name")));
            assertNotNull(tool.get("inputSchema"));
        }
    }

    @Test
    @DisplayName("Stage 9: MCP tools/call get_snippet interpolates variables and generates PM install command")
    void testToolCallGetSnippet() {
        Snippet snp = new Snippet();
        snp.setId("snp_debounce");
        snp.setSlug("use-debounce");
        snp.setTitle("useDebounce Hook");
        snp.setLanguage("typescript");
        snp.setCode("import { useState, useEffect } from 'react';\nconst delay = {{delay|300}};");
        snp.setDependencies(List.of(Map.of("name", "react", "type", "regular")));
        snp.setDependencyNames(List.of("react"));

        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(anyString(), eq("use-debounce")))
                .thenReturn(Optional.of(snp));

        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/call",
                "params", Map.of(
                        "name", "get_snippet",
                        "arguments", Map.of(
                                "ref", "use-debounce",
                                "variables", Map.of("delay", "500"),
                                "packageManager", "pnpm"
                        )
                )
        );

        Map<String, Object> resp = mcpService.handleJsonRpc(req, actor, "ws_personal");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertEquals(1, content.size());
        String text = (String) content.get(0).get("text");

        assertTrue(text.contains("const delay = 500;"));
        assertTrue(text.contains("pnpm add react"));
        assertTrue(text.contains("useDebounce Hook"));
    }

    @Test
    @DisplayName("Stage 9: MCP tools/call save_snippet rejects raw secrets")
    void testToolCallSaveSnippetRejectsSecret() {
        Map<String, Object> req = Map.of(
                "jsonrpc", "2.0",
                "id", 3,
                "method", "tools/call",
                "params", Map.of(
                        "name", "save_snippet",
                        "arguments", Map.of(
                                "title", "Stripe Checkout",
                                "language", "typescript",
                                "code", "const stripe = new Stripe('" + String.join("_", "sk", "live", "51Abcdefghijklmnopqrstuvwxy1234567890") + "');"
                        )
                )
        );

        Map<String, Object> resp = mcpService.handleJsonRpc(req, actor, "ws_personal");
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertNotNull(result);
        assertEquals(true, result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("Secret detected in snippet"));
    }
}
