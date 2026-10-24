package dev.pluck.backend.service;

import dev.pluck.backend.model.Token;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.FolderRepository;
import dev.pluck.backend.repository.TokenRepository;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.repository.WorkspaceRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository userRepository;
    private WorkspaceRepository workspaceRepository;
    private FolderRepository folderRepository;
    private TokenRepository tokenRepository;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        workspaceRepository = Mockito.mock(WorkspaceRepository.class);
        folderRepository = Mockito.mock(FolderRepository.class);
        tokenRepository = Mockito.mock(TokenRepository.class);

        authService = new AuthService(
                userRepository,
                workspaceRepository,
                folderRepository,
                tokenRepository,
                "pluck_super_secure_jwt_secret_key_at_least_256_bits_long_1234567890",
                2592000L
        );
    }

    @Test
    @DisplayName("Stage 2: BCrypt Password Hashing and Verification")
    void testPasswordHashing() {
        String plainPassword = "superSecurePassword123!";
        String hash = authService.encodePassword(plainPassword);

        assertNotNull(hash);
        assertNotEquals(plainPassword, hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"));

        assertTrue(authService.matchesPassword(plainPassword, hash, null));
        assertFalse(authService.matchesPassword("wrongPassword", hash, null));
    }

    @Test
    @DisplayName("Stage 2: JJWT Session Token Issuance and Claim Verification")
    void testJwtSessionToken() {
        User user = new User("usr_alex123", "Alex Dev", "alex@pluck.dev", "A", "owner");
        String token = authService.createSessionToken(user);

        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        Claims claims = authService.parseAndValidateToken(token);
        assertNotNull(claims);
        assertEquals("usr_alex123", claims.getSubject());
        assertEquals("alex@pluck.dev", claims.get("email"));
        assertEquals("Alex Dev", claims.get("name"));
    }

    @Test
    @DisplayName("Stage 2: Cryptographic Personal Access Token (PAT) Generation and Lookup")
    void testPatGenerationAndValidation() {
        User user = new User("usr_alex123", "Alex Dev", "alex@pluck.dev", "A", "owner");
        when(tokenRepository.save(any(Token.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> patResult = authService.generatePat(
                user, "CLI Sync Token", List.of("snippets:read", "snippets:write"), List.of("ws_personal"), "in 90 days"
        );

        assertNotNull(patResult);
        String rawToken = (String) patResult.get("rawToken");
        Token tokenDoc = (Token) patResult.get("token");

        assertTrue(rawToken.startsWith("pluck_pat_"));
        assertNotNull(tokenDoc.getTokenPrefix());
        assertNotNull(tokenDoc.getTokenHash());
        assertNotEquals(rawToken, tokenDoc.getTokenHash());

        // Mock token repository lookup for PAT validation
        when(tokenRepository.findByTokenPrefix(tokenDoc.getTokenPrefix()))
                .thenReturn(Optional.of(tokenDoc));

        Token authenticatedToken = authService.authenticatePat(rawToken);
        assertNotNull(authenticatedToken);
        assertEquals("usr_alex123", authenticatedToken.getUserId());
        assertEquals("Just now", authenticatedToken.getLastUsed());

        // Non-matching token fails
        Token invalidToken = authService.authenticatePat("pluck_pat_00000000000000000000");
        assertNull(invalidToken);
    }
}
