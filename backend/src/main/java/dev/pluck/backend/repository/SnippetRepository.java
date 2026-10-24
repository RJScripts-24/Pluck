package dev.pluck.backend.repository;

import dev.pluck.backend.model.Snippet;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnippetRepository extends MongoRepository<Snippet, String> {
    Optional<Snippet> findBySlug(String slug);
    Optional<Snippet> findByShareId(String shareId);
    Optional<Snippet> findByWorkspaceIdAndSlugAndDeletedAtIsNull(String workspaceId, String slug);
    Optional<Snippet> findByIdAndDeletedAtIsNull(String id);
    Optional<Snippet> findByWorkspaceIdAndIdAndDeletedAtIsNull(String workspaceId, String id);

    List<Snippet> findByWorkspaceIdAndDeletedAtIsNull(String workspaceId);
    List<Snippet> findByWorkspaceIdAndFolderIdAndDeletedAtIsNull(String workspaceId, String folderId);
    long countByWorkspaceIdAndFolderIdAndDeletedAtIsNull(String workspaceId, String folderId);
    long countByWorkspaceIdAndFolderNameAndDeletedAtIsNull(String workspaceId, String folderName);
    List<Snippet> findByWorkspaceIdAndLanguageIgnoreCaseAndDeletedAtIsNull(String workspaceId, String language);
    List<Snippet> findByWorkspaceIdAndTagsContainingAndDeletedAtIsNull(String workspaceId, String tag);
    List<Snippet> findByWorkspaceIdAndDependencyNamesContainingAndDeletedAtIsNull(String workspaceId, String depName);

    // Legacy and search support
    List<Snippet> findByFolderId(String folderId);
    List<Snippet> findByLanguageIgnoreCase(String language);
    List<Snippet> findByTagsContaining(String tag);
    List<Snippet> findByTitleContainingIgnoreCaseOrCodeContainingIgnoreCase(String title, String code);
}
