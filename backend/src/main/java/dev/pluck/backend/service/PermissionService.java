package dev.pluck.backend.service;

import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.model.WorkspaceMember;
import dev.pluck.backend.repository.WorkspaceMemberRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PermissionService {

    public enum Role {
        ADMIN,
        EDITOR,
        VIEWER
    }

    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceRepository workspaceRepository;

    @Autowired
    public PermissionService(WorkspaceMemberRepository memberRepository, WorkspaceRepository workspaceRepository) {
        this.memberRepository = memberRepository;
        this.workspaceRepository = workspaceRepository;
    }

    public boolean canRead(User user, String workspaceId) {
        if (workspaceId == null || workspaceId.equals("ws_personal")) {
            return true;
        }
        if (user == null) {
            return false;
        }
        return getMemberRole(user, workspaceId).isPresent();
    }

    public boolean canEdit(User user, String workspaceId) {
        if (workspaceId == null || workspaceId.equals("ws_personal")) {
            return true;
        }
        if (user == null) {
            return false;
        }
        Optional<Role> role = getMemberRole(user, workspaceId);
        return role.isPresent() && (role.get() == Role.ADMIN || role.get() == Role.EDITOR);
    }

    public boolean canAdmin(User user, String workspaceId) {
        if (workspaceId == null || workspaceId.equals("ws_personal")) {
            return true;
        }
        if (user == null) {
            return false;
        }
        Optional<Role> role = getMemberRole(user, workspaceId);
        return role.isPresent() && role.get() == Role.ADMIN;
    }

    public Optional<Role> getMemberRole(User user, String workspaceId) {
        if (user == null || workspaceId == null) return Optional.empty();

        // Check if user is the direct owner of the workspace
        Optional<Workspace> wsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(workspaceId)
                .or(() -> workspaceRepository.findBySlugAndDeletedAtIsNull(workspaceId));
        if (wsOpt.isPresent() && user.getId().equals(wsOpt.get().getOwnerUserId())) {
            return Optional.of(Role.ADMIN);
        }

        // Check membership
        Optional<WorkspaceMember> memberOpt = memberRepository.findByWorkspaceIdAndUserId(workspaceId, user.getId());
        if (memberOpt.isPresent()) {
            String r = memberOpt.get().getRole().toUpperCase();
            try {
                return Optional.of(Role.valueOf(r));
            } catch (IllegalArgumentException e) {
                return Optional.of(Role.VIEWER);
            }
        }
        return Optional.empty();
    }
}
