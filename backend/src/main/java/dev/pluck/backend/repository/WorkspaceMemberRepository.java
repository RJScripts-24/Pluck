package dev.pluck.backend.repository;

import dev.pluck.backend.model.WorkspaceMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepository extends MongoRepository<WorkspaceMember, String> {
    List<WorkspaceMember> findByWorkspaceId(String workspaceId);
    List<WorkspaceMember> findByUserId(String userId);
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, String userId);
    Optional<WorkspaceMember> findByWorkspaceIdAndEmail(String workspaceId, String email);
    long countByWorkspaceIdAndRoleIgnoreCase(String workspaceId, String role);
    void deleteByWorkspaceIdAndUserId(String workspaceId, String userId);
}
