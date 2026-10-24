package dev.pluck.backend.repository;

import dev.pluck.backend.model.Workspace;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceRepository extends MongoRepository<Workspace, String> {
    Optional<Workspace> findBySlug(String slug);
    Optional<Workspace> findBySlugAndDeletedAtIsNull(String slug);
    Optional<Workspace> findByIdAndDeletedAtIsNull(String id);
    List<Workspace> findByOwnerUserId(String ownerUserId);
}
