package dev.pluck.backend.repository;

import dev.pluck.backend.model.Folder;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FolderRepository extends MongoRepository<Folder, String> {
    List<Folder> findByWorkspaceId(String workspaceId);
    Optional<Folder> findByWorkspaceIdAndId(String workspaceId, String id);
    Optional<Folder> findByWorkspaceIdAndName(String workspaceId, String name);
    void deleteByWorkspaceId(String workspaceId);
}
