package dev.pluck.backend.service;

import dev.pluck.backend.model.Folder;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.SnippetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FolderServiceTest {

    private FolderRepository folderRepository;
    private SnippetRepository snippetRepository;
    private FolderService folderService;

    @BeforeEach
    void setUp() {
        folderRepository = mock(FolderRepository.class);
        snippetRepository = mock(SnippetRepository.class);
        folderService = new FolderService(folderRepository, snippetRepository);
    }

    @Test
    @DisplayName("listFolders seeds defaults if repository returns empty")
    void testListFoldersSeedsDefaults() {
        when(folderRepository.findByWorkspaceId("ws_1")).thenReturn(new ArrayList<>());
        when(snippetRepository.countByWorkspaceIdAndFolderIdAndDeletedAtIsNull(eq("ws_1"), anyString())).thenReturn(2L);

        List<Folder> folders = folderService.listFolders("ws_1");

        assertFalse(folders.isEmpty());
        assertEquals(6, folders.size());
        assertEquals("hooks", folders.get(0).getName());
        assertEquals(2, folders.get(0).getCount());
        verify(folderRepository, atLeast(6)).save(any(Folder.class));
    }

    @Test
    @DisplayName("createFolder enforces max hierarchy depth of 3")
    void testHierarchyDepthLimit() {
        Folder root = new Folder("root", "📁");
        root.setId("fld_root");

        Folder level2 = new Folder("level2", "📁");
        level2.setId("fld_lvl2");
        level2.setParentId("fld_root");

        Folder level3 = new Folder("level3", "📁");
        level3.setId("fld_lvl3");
        level3.setParentId("fld_lvl2");

        when(folderRepository.findByWorkspaceIdAndId("ws_1", "fld_lvl3")).thenReturn(Optional.of(level3));
        when(folderRepository.findByWorkspaceIdAndId("ws_1", "fld_lvl2")).thenReturn(Optional.of(level2));
        when(folderRepository.findByWorkspaceIdAndId("ws_1", "fld_root")).thenReturn(Optional.of(root));

        // Creating level 4 should fail
        assertThrows(IllegalArgumentException.class, () -> {
            folderService.createFolder("ws_1", "level4", "📁", "fld_lvl3");
        });
    }

    @Test
    @DisplayName("deleteFolder unlinks snippets and removes folder")
    void testDeleteFolderUnlinksSnippets() {
        Folder folder = new Folder("hooks", "🪝");
        folder.setId("fld_hooks");
        when(folderRepository.findByWorkspaceIdAndId("ws_1", "fld_hooks")).thenReturn(Optional.of(folder));

        Snippet snippet = new Snippet();
        snippet.setId("snp_1");
        snippet.setFolderId("fld_hooks");
        snippet.setFolderName("hooks");
        when(snippetRepository.findByWorkspaceIdAndFolderIdAndDeletedAtIsNull("ws_1", "fld_hooks"))
                .thenReturn(List.of(snippet));

        boolean deleted = folderService.deleteFolder("ws_1", "fld_hooks");

        assertTrue(deleted);
        assertEquals("uncategorized", snippet.getFolderId());
        verify(snippetRepository).save(snippet);
        verify(folderRepository).delete(folder);
    }
}
