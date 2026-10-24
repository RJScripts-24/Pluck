package dev.pluck.backend.service;

import dev.pluck.backend.model.Folder;
import dev.pluck.backend.model.Token;
import dev.pluck.backend.model.User;
import dev.pluck.backend.model.Workspace;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.TokenRepository;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final TokenRepository tokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    private final SecretKey jwtSecretKey;
    private final long jwtExpirationSeconds;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AuthService(
            UserRepository userRepository,
            WorkspaceRepository workspaceRepository,
            FolderRepository folderRepository,
            TokenRepository tokenRepository,
            @Value("${pluck.jwt.secret:pluck_super_secure_jwt_secret_key_at_least_256_bits_long_1234567890}") String jwtSecret,
            @Value("${pluck.jwt.expiration-seconds:2592000}") long jwtExpirationSeconds
    ) {
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.folderRepository = folderRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.jwtSecretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationSeconds = jwtExpirationSeconds;
    }

    public BCryptPasswordEncoder getPasswordEncoder() {
        return passwordEncoder;
    }

    public String encodePassword(String rawPassword) {
        if (rawPassword == null) return null;
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matchesPassword(String rawPassword, String passwordHash, String plainFallback) {
        if (rawPassword == null) return false;
        if (passwordHash != null && !passwordHash.isEmpty()) {
            return passwordEncoder.matches(rawPassword, passwordHash);
        }
        if (plainFallback != null && !plainFallback.isEmpty()) {
            return plainFallback.equals(rawPassword);
        }
        return false;
    }

    public String createSessionToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtExpirationSeconds, ChronoUnit.SECONDS);

        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(jwtSecretKey)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(jwtSecretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public String sha256Hex(String input) {
        if (input == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public Map<String, Object> generatePat(User user, String name, List<String> scopes, List<String> workspaceIds, String expires) {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        String hex = sb.toString();
        String rawToken = "pluck_pat_" + hex;
        String prefix = "pluck_pat_" + hex.substring(0, 8);
        String tokenHash = sha256Hex(rawToken);

        Token token = new Token();
        token.setId("tok_" + UUID.randomUUID().toString().substring(0, 8));
        token.setUserId(user.getId());
        token.setName(name != null ? name.trim() : "PAT Token");
        token.setTokenPrefix(prefix);
        token.setTokenHash(tokenHash);
        token.setScopes(scopes != null && !scopes.isEmpty() ? scopes : List.of("snippets:read", "snippets:write"));
        token.setWorkspaceIds(workspaceIds != null ? workspaceIds : List.of());
        token.setLastUsed("Never");
        token.setExpires(expires != null ? expires : "in 90 days");

        Token saved = tokenRepository.save(token);

        Map<String, Object> result = new HashMap<>();
        result.put("token", saved);
        result.put("rawToken", rawToken);
        return result;
    }

    public Token authenticatePat(String rawToken) {
        if (rawToken == null || !rawToken.startsWith("pluck_pat_")) {
            return null;
        }
        if (rawToken.length() < 18) {
            return null;
        }
        String prefix = "pluck_pat_" + rawToken.substring(10, 18);
        Optional<Token> tokenOpt = tokenRepository.findByTokenPrefix(prefix);
        if (tokenOpt.isEmpty()) {
            return null;
        }

        Token token = tokenOpt.get();
        if (token.getRevokedAt() != null) {
            return null;
        }

        String computedHash = sha256Hex(rawToken);
        if (!computedHash.equalsIgnoreCase(token.getTokenHash())) {
            return null;
        }

        // Update last used
        token.setLastUsed("Just now");
        token.setLastUsedAt(Instant.now().toString());
        tokenRepository.save(token);

        return token;
    }

    public Workspace ensurePersonalWorkspace(User user) {
        List<Workspace> workspaces = workspaceRepository.findByOwnerUserId(user.getId());
        if (!workspaces.isEmpty()) {
            return workspaces.get(0);
        }

        String rawName = (user.getName() != null && !user.getName().isBlank()) ? user.getName() : "Personal";
        String cleanSlug = rawName.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (cleanSlug.isBlank()) cleanSlug = "personal";
        String slug = "ws_" + cleanSlug + "_" + UUID.randomUUID().toString().substring(0, 4);

        Workspace ws = new Workspace();
        ws.setId("ws_" + UUID.randomUUID().toString().substring(0, 8));
        ws.setName(rawName + "'s Vault");
        ws.setSlug(slug);
        ws.setType("personal");
        ws.setOwnerUserId(user.getId());
        ws.setEditorsCanPublish(true);
        ws.setStrictSecretGating(false);

        Workspace saved = workspaceRepository.save(ws);

        // Seed default folders
        seedDefaultFolders(saved.getId());

        return saved;
    }

    public void seedDefaultFolders(String workspaceId) {
        List<Folder> existing = folderRepository.findByWorkspaceId(workspaceId);
        if (!existing.isEmpty()) return;

        String[][] defaults = {
                {"hooks", "🪝", "0"},
                {"components", "🧩", "1"},
                {"api", "⚡", "2"},
                {"auth", "🔒", "3"},
                {"db", "💾", "4"},
                {"cli", "💻", "5"}
        };

        for (String[] def : defaults) {
            Folder folder = new Folder(def[0], def[1]);
            folder.setWorkspaceId(workspaceId);
            folder.setPosition(Integer.parseInt(def[2]));
            folderRepository.save(folder);
        }
    }
}
