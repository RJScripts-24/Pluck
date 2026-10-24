package dev.pluck.backend.service;

import dev.pluck.backend.model.ActivityEvent;
import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.model.WorkspaceMember;
import dev.pluck.backend.repository.ActivityEventRepository;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.repository.WorkspaceMemberRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TeamServiceTest {

    private WorkspaceRepository workspaceRepository;
    private WorkspaceMemberRepository memberRepository;
    private ActivityEventRepository activityEventRepository;
    private UserRepository userRepository;
    private TeamService teamService;

    private Workspace team;
    private User actor;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        memberRepository = mock(WorkspaceMemberRepository.class);
        activityEventRepository = mock(ActivityEventRepository.class);
        userRepository = mock(UserRepository.class);

        teamService = new TeamService(workspaceRepository, memberRepository, activityEventRepository, userRepository);

        team = new Workspace("ws_acme", "Acme Core Eng", "acme-core-eng", "team", "usr_alex");
        when(workspaceRepository.findByIdAndDeletedAtIsNull("acme-core-eng")).thenReturn(Optional.of(team));
        when(workspaceRepository.findBySlugAndDeletedAtIsNull("acme-core-eng")).thenReturn(Optional.of(team));
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(i -> i.getArgument(0));

        actor = new User("usr_alex", "Alex Developer", "alex@pluck.dev", "hash", "owner");
    }

    @Test
    @DisplayName("Stage 8: Team members can be invited and assigned roles with audit event recorded")
    void testInviteMember() {
        when(memberRepository.findByWorkspaceIdAndEmail("ws_acme", "developer@acme.corp"))
                .thenReturn(Optional.empty());
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

        WorkspaceMember invited = teamService.inviteMember("acme-core-eng", actor, "developer@acme.corp", "Editor");

        assertNotNull(invited);
        assertEquals("developer@acme.corp", invited.getEmail());
        assertEquals("editor", invited.getRole());
        assertEquals("pending", invited.getStatus());

        // Verify audit event recorded
        verify(activityEventRepository, atLeastOnce()).save(any(ActivityEvent.class));
    }

    @Test
    @DisplayName("Stage 8: Invariant - Last Admin cannot be demoted")
    void testCannotDemoteLastAdmin() {
        WorkspaceMember admin = new WorkspaceMember("ws_acme", "usr_alex", "admin");
        when(memberRepository.findByWorkspaceIdAndUserId("ws_acme", "usr_alex")).thenReturn(Optional.of(admin));
        when(memberRepository.countByWorkspaceIdAndRoleIgnoreCase("ws_acme", "admin")).thenReturn(1L);

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            teamService.updateMemberRole("acme-core-eng", actor, "usr_alex", "Viewer");
        });

        assertTrue(ex.getMessage().contains("LAST_ADMIN"));
    }

    @Test
    @DisplayName("Stage 8: Invariant - Last Admin cannot be removed")
    void testCannotRemoveLastAdmin() {
        WorkspaceMember admin = new WorkspaceMember("ws_acme", "usr_alex", "admin");
        when(memberRepository.findByWorkspaceIdAndUserId("ws_acme", "usr_alex")).thenReturn(Optional.of(admin));
        when(memberRepository.countByWorkspaceIdAndRoleIgnoreCase("ws_acme", "admin")).thenReturn(1L);

        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            teamService.removeMember("acme-core-eng", actor, "usr_alex");
        });

        assertTrue(ex.getMessage().contains("LAST_ADMIN"));
    }

    @Test
    @DisplayName("Stage 8: Demoting Admin succeeds when another Admin exists")
    void testDemoteAdminWhenMultipleExist() {
        WorkspaceMember admin1 = new WorkspaceMember("ws_acme", "usr_alex", "admin");
        when(memberRepository.findByWorkspaceIdAndUserId("ws_acme", "usr_alex")).thenReturn(Optional.of(admin1));
        when(memberRepository.countByWorkspaceIdAndRoleIgnoreCase("ws_acme", "admin")).thenReturn(2L);
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(i -> i.getArgument(0));

        WorkspaceMember updated = teamService.updateMemberRole("acme-core-eng", actor, "usr_alex", "Editor");
        assertEquals("editor", updated.getRole());
        verify(activityEventRepository, atLeastOnce()).save(any(ActivityEvent.class));
    }

    @Test
    @DisplayName("Stage 8: Audit log records team mutations and returns activity list")
    void testGetTeamActivity() {
        ActivityEvent event = new ActivityEvent("ws_acme", "usr_alex", "Alex", "member.invited", "m1", Map.of("email", "dev@acme.corp", "role", "Editor"));
        when(activityEventRepository.findByWorkspaceIdOrderByCreatedAtDesc("ws_acme")).thenReturn(List.of(event));

        List<Map<String, Object>> activities = teamService.getTeamActivity("acme-core-eng");
        assertFalse(activities.isEmpty());
        assertEquals("Alex", activities.get(0).get("user"));
    }
}
