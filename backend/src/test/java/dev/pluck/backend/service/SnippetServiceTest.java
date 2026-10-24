package dev.pluck.backend.service;

import dev.pluck.backend.model.ActivityEvent;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.model.SnippetVersion;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.ActivityEventRepository;
import dev.pluck.backend.repository.SnippetRepository;
import dev.pluck.backend.repository.SnippetVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnippetServiceTest {

    private SnippetRepository snippetRepository;
    private SnippetVersionRepository snippetVersionRepository;
    private ActivityEventRepository activityEventRepository;
    private ASTService astService;
    private SnippetService snippetService;

    @BeforeEach
    void setUp() {
        snippetRepository = Mockito.mock(SnippetRepository.class);
        snippetVersionRepository = Mockito.mock(SnippetVersionRepository.class);
        activityEventRepository = Mockito.mock(ActivityEventRepository.class);
        astService = new ASTService();

        snippetService = new SnippetService(
                snippetRepository,
                snippetVersionRepository,
                activityEventRepository,
                astService
        );
    }

    @Test
    @DisplayName("Stage 3: Snippet Creation with Unique Slug and Version 1 Snapshot")
    void testCreateSnippet() {
        User author = new User("usr_alex", "Alex Dev", "alex@pluck.dev", "A", "owner");
        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(i -> i.getArgument(0));

        String code = "import { useState } from 'react'; export function useCounter() { return useState(0); }";
        Snippet created = snippetService.createSnippet(
                "ws_personal",
                author,
                "Use Counter Hook",
                "Simple state counter",
                code,
                "typescript",
                "hooks",
                "private",
                List.of("react", "state"),
                List.of()
        );

        assertNotNull(created);
        assertEquals("use-counter-hook", created.getSlug());
        assertEquals(1, created.getVersion());
        assertTrue(created.getDependencyNames().contains("react"));
        assertNotNull(created.getCodeHash());

        // Verify version 1 snapshot was saved
        verify(snippetVersionRepository, times(1)).save(any(SnippetVersion.class));
        // Verify activity event was logged
        verify(activityEventRepository, times(1)).save(any(ActivityEvent.class));
    }

    @Test
    @DisplayName("Stage 3: Unique Slug Conflict Resolution (-2, -3)")
    void testUniqueSlugGeneration() {
        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull("ws_personal", "my-snippet"))
                .thenReturn(Optional.of(new Snippet()));
        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull("ws_personal", "my-snippet-2"))
                .thenReturn(Optional.of(new Snippet()));
        when(snippetRepository.findByWorkspaceIdAndSlugAndDeletedAtIsNull("ws_personal", "my-snippet-3"))
                .thenReturn(Optional.empty());

        String slug = snippetService.generateUniqueSlug("ws_personal", "My Snippet");
        assertEquals("my-snippet-3", slug);
    }

    @Test
    @DisplayName("Stage 3: Optimistic Concurrency Control (OCC) Version Conflict")
    void testOptimisticConcurrencyControl() {
        Snippet existing = new Snippet();
        existing.setId("snp_123");
        existing.setWorkspaceId("ws_personal");
        existing.setVersion(2);
        existing.setCode("console.log('v2');");

        when(snippetRepository.findByIdAndDeletedAtIsNull("snp_123")).thenReturn(Optional.of(existing));

        // Base version mismatch (client thinks snippet is v1, but it is v2)
        assertThrows(SnippetService.VersionConflictException.class, () -> {
            snippetService.updateSnippet(
                    "snp_123",
                    "ws_personal",
                    null,
                    1, // Mismatched base version
                    Map.of("code", "console.log('v3');")
            );
        });

        // Matching base version succeeds and increments to v3
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(i -> i.getArgument(0));
        Snippet updated = snippetService.updateSnippet(
                "snp_123",
                "ws_personal",
                null,
                2, // Matching base version
                Map.of("code", "console.log('v3');")
        );

        assertNotNull(updated);
        assertEquals(3, updated.getVersion());
        verify(snippetVersionRepository, times(1)).save(any(SnippetVersion.class));
    }

    @Test
    @DisplayName("Stage 3: Soft Delete and Atomic Copy Counter")
    void testSoftDeleteAndCopyCount() {
        Snippet snippet = new Snippet();
        snippet.setId("snp_test");
        snippet.setTitle("Test Snippet");
        snippet.setWorkspaceId("ws_personal");
        snippet.setCopyCount(5);

        when(snippetRepository.findByIdAndDeletedAtIsNull("snp_test")).thenReturn(Optional.of(snippet));
        when(snippetRepository.findById("snp_test")).thenReturn(Optional.of(snippet));
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(i -> i.getArgument(0));

        // Increment copy count
        int newCount = snippetService.incrementCopyCount("snp_test");
        assertEquals(6, newCount);

        // Soft delete
        boolean deleted = snippetService.softDeleteSnippet("snp_test", "ws_personal", null);
        assertTrue(deleted);
        assertNotNull(snippet.getDeletedAt());
    }

    @Test
    @DisplayName("Stage 5: Restoring version 1 increments version to 3 with identical snapshot code")
    void testRestoreVersion() {
        Snippet snippet = new Snippet();
        snippet.setId("snp_restore");
        snippet.setWorkspaceId("ws_personal");
        snippet.setVersion(2);
        snippet.setCode("console.log('version 2 code');");

        when(snippetRepository.findByIdAndDeletedAtIsNull("snp_restore")).thenReturn(Optional.of(snippet));
        when(snippetRepository.save(any(Snippet.class))).thenAnswer(i -> i.getArgument(0));

        SnippetVersion v1 = new SnippetVersion("snp_restore", 1, "Initial Title", "Desc", "typescript", "console.log('version 1 code');");

        Snippet restored = snippetService.updateSnippet(
                "snp_restore",
                "ws_personal",
                null,
                null,
                Map.of("code", v1.getCode(), "title", v1.getTitle())
        );

        assertEquals(3, restored.getVersion());
        assertEquals("console.log('version 1 code');", restored.getCode());
        verify(snippetVersionRepository, times(1)).save(any(SnippetVersion.class));
    }
}
