package dev.pluck.backend.service;

import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.model.WorkspaceMember;
import dev.pluck.backend.repository.WorkspaceMemberRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class PermissionServiceTest {

    private WorkspaceMemberRepository memberRepository;
    private WorkspaceRepository workspaceRepository;
    private PermissionService permissionService;

    private User userAdmin;
    private User userEditor;
    private User userViewer;

    @BeforeEach
    void setUp() {
        memberRepository = Mockito.mock(WorkspaceMemberRepository.class);
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        permissionService = new PermissionService(memberRepository, workspaceRepository);

        userAdmin = new User("usr_alex", "Alex", "alex@pluck.dev", "hash", "owner");
        userEditor = new User("usr_sarah", "Sarah", "sarah@acme.corp", "hash", "member");
        userViewer = new User("usr_leo", "Leo", "leo@acme.corp", "hash", "member");

        Workspace ws = new Workspace("ws_team_1", "Team Workspace", "team-1", "team", "usr_alex");
        when(workspaceRepository.findByIdAndDeletedAtIsNull("ws_team_1")).thenReturn(Optional.of(ws));

        when(memberRepository.findByWorkspaceIdAndUserId("ws_team_1", "usr_alex"))
                .thenReturn(Optional.of(new WorkspaceMember("ws_team_1", "usr_alex", "admin")));
        when(memberRepository.findByWorkspaceIdAndUserId("ws_team_1", "usr_sarah"))
                .thenReturn(Optional.of(new WorkspaceMember("ws_team_1", "usr_sarah", "editor")));
        when(memberRepository.findByWorkspaceIdAndUserId("ws_team_1", "usr_leo"))
                .thenReturn(Optional.of(new WorkspaceMember("ws_team_1", "usr_leo", "viewer")));
    }

    @Test
    @DisplayName("Stage 8: Admin and Editor can edit snippets, but Viewer cannot edit or save snippets")
    void testCanEditRBAC() {
        assertTrue(permissionService.canEdit(userAdmin, "ws_team_1"), "Admin should have edit permission");
        assertTrue(permissionService.canEdit(userEditor, "ws_team_1"), "Editor should have edit permission");
        assertFalse(permissionService.canEdit(userViewer, "ws_team_1"), "Viewer must NOT have edit permission (403 Forbidden)");
    }

    @Test
    @DisplayName("Stage 8: Personal workspace allows edit by default")
    void testPersonalWorkspacePermissions() {
        assertTrue(permissionService.canEdit(userViewer, "ws_personal"), "Personal vault allows owner to edit");
    }

    @Test
    @DisplayName("Stage 8: Only Admin can perform team administration")
    void testCanAdmin() {
        assertTrue(permissionService.canAdmin(userAdmin, "ws_team_1"));
        assertFalse(permissionService.canAdmin(userEditor, "ws_team_1"));
        assertFalse(permissionService.canAdmin(userViewer, "ws_team_1"));
    }
}
