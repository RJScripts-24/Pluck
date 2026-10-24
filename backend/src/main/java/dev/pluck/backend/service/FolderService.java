package dev.pluck.backend.service;

import dev.pluck.backend.model.Folder;
import dev.pluck.backend.model.Snippet;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.SnippetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final SnippetRepository snippetRepository;

    private static final List<FolderDefault> DEFAULT_FOLDERS = List.of(
            new FolderDefault("hooks", "hooks", "🪝"),
            new FolderDefault("auth", "auth", "🔐"),
            new FolderDefault("aws-infra", "aws-infra", "☁️"),
            new FolderDefault("database", "database", "💾"),
            new FolderDefault("utilities", "utilities", "🛠️"),
            new FolderDefault("api", "api", "🌐")
    );

    private record FolderDefault(String id, String name, String emoji) {}

    @Autowired
    public FolderService(FolderRepository folderRepository, SnippetRepository snippetRepository) {
        this.folderRepository = folderRepository;
        this.snippetRepository = snippetRepository;
    }

    public List<Folder> listFolders(String workspaceId) {
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";
        List<Folder> folders = folderRepository.findByWorkspaceId(wsId);

        if (folders.isEmpty()) {
            folders = seedDefaultFolders(wsId);
        }

        // Calculate live snippet counts
        for (Folder f : folders) {
            long countById = snippetRepository.countByWorkspaceIdAndFolderIdAndDeletedAtIsNull(wsId, f.getId());
            long countByName = !f.getId().equalsIgnoreCase(f.getName())
                    ? snippetRepository.countByWorkspaceIdAndFolderNameAndDeletedAtIsNull(wsId, f.getName())
                    : 0;
            f.setCount((int) (countById + countByName));
        }

        return folders;
    }

    public List<Folder> seedDefaultFolders(String workspaceId) {
        List<Folder> created = new ArrayList<>();
        int pos = 0;
        for (FolderDefault def : DEFAULT_FOLDERS) {
            Folder f = new Folder();
            f.setId(def.id());
            f.setName(def.name());
            f.setEmoji(def.emoji());
            f.setWorkspaceId(workspaceId);
            f.setPosition(pos++);
            f.setCount(0);
            f.setCreatedAt(Instant.now().toString());
            folderRepository.save(f);
            created.add(f);
        }
        return created;
    }

    public Folder createFolder(String workspaceId, String name, String emoji, String parentId) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder name cannot be blank.");
        }

        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";
        String cleanName = name.trim();

        // Check max depth <= 3
        if (parentId != null && !parentId.isBlank()) {
            int depth = calculateHierarchyDepth(wsId, parentId);
            if (depth >= 3) {
                throw new IllegalArgumentException("MAX_DEPTH_EXCEEDED: Maximum folder hierarchy depth is 3 levels.");
            }
        }

        String folderId = "fld_" + UUID.randomUUID().toString().substring(0, 8);
        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setName(cleanName);
        folder.setEmoji((emoji != null && !emoji.isBlank()) ? emoji.trim() : "📁");
        folder.setWorkspaceId(wsId);
        folder.setParentId(parentId);
        folder.setCount(0);
        folder.setCreatedAt(Instant.now().toString());

        return folderRepository.save(folder);
    }

    public Folder updateFolder(String workspaceId, String id, Map<String, Object> updates) {
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";
        Folder folder = folderRepository.findByWorkspaceIdAndId(wsId, id)
                .orElseGet(() -> folderRepository.findById(id).orElse(null));

        if (folder == null) {
            return null;
        }

        String oldName = folder.getName();

        if (updates.containsKey("name") && updates.get("name") instanceof String newName && !newName.trim().isEmpty()) {
            folder.setName(newName.trim());

            // Cascade name update to snippets in this folder
            List<Snippet> snippets = snippetRepository.findByWorkspaceIdAndFolderIdAndDeletedAtIsNull(wsId, folder.getId());
            for (Snippet s : snippets) {
                s.setFolderName(folder.getName());
                snippetRepository.save(s);
            }
        }

        if (updates.containsKey("emoji") && updates.get("emoji") instanceof String newEmoji) {
            folder.setEmoji(newEmoji.trim());
        }

        if (updates.containsKey("parentId")) {
            String newParentId = (String) updates.get("parentId");
            if (newParentId != null && !newParentId.isBlank()) {
                if (newParentId.equals(id)) {
                    throw new IllegalArgumentException("A folder cannot be its own parent.");
                }
                int depth = calculateHierarchyDepth(wsId, newParentId);
                if (depth >= 3) {
                    throw new IllegalArgumentException("MAX_DEPTH_EXCEEDED: Maximum folder hierarchy depth is 3 levels.");
                }
            }
            folder.setParentId(newParentId);
        }

        return folderRepository.save(folder);
    }

    public boolean deleteFolder(String workspaceId, String id) {
        String wsId = (workspaceId != null && !workspaceId.isBlank()) ? workspaceId : "ws_personal";
        Optional<Folder> opt = folderRepository.findByWorkspaceIdAndId(wsId, id)
                .or(() -> folderRepository.findById(id));

        if (opt.isEmpty()) {
            return false;
        }

        Folder folder = opt.get();

        // Unlink snippets from this folder
        List<Snippet> snippets = snippetRepository.findByWorkspaceIdAndFolderIdAndDeletedAtIsNull(wsId, folder.getId());
        for (Snippet s : snippets) {
            s.setFolderId("uncategorized");
            s.setFolderName("Uncategorized");
            snippetRepository.save(s);
        }

        folderRepository.delete(folder);
        return true;
    }

    private int calculateHierarchyDepth(String workspaceId, String parentId) {
        int depth = 1;
        String currentId = parentId;
        Set<String> visited = new HashSet<>();

        while (currentId != null && !currentId.isBlank()) {
            if (!visited.add(currentId)) {
                // Prevent cycle loop
                break;
            }
            final String lookupId = currentId;
            Optional<Folder> parentOpt = folderRepository.findByWorkspaceIdAndId(workspaceId, lookupId)
                    .or(() -> folderRepository.findById(lookupId));
            if (parentOpt.isPresent() && parentOpt.get().getParentId() != null) {
                depth++;
                currentId = parentOpt.get().getParentId();
            } else {
                break;
            }
        }
        return depth;
    }
}
