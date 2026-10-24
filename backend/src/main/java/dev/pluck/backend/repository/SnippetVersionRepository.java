package dev.pluck.backend.repository;

import dev.pluck.backend.model.SnippetVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SnippetVersionRepository extends MongoRepository<SnippetVersion, String> {
    List<SnippetVersion> findBySnippetIdOrderByVersionDesc(String snippetId);
    Optional<SnippetVersion> findBySnippetIdAndVersion(String snippetId, int version);
    void deleteBySnippetId(String snippetId);
}
