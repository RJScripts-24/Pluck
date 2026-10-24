package dev.pluck.backend.config;

import dev.pluck.backend.model.PluckPrincipal;
import dev.pluck.backend.model.Token;
import dev.pluck.backend.model.User;
import dev.pluck.backend.repository.UserRepository;
import dev.pluck.backend.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class CompositeAuthFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final dev.pluck.backend.repository.WorkspaceRepository workspaceRepository;

    @Autowired
    public CompositeAuthFilter(AuthService authService, UserRepository userRepository, dev.pluck.backend.repository.WorkspaceRepository workspaceRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
    }

    private String resolveUserWorkspace(User user, String headerWs, List<String> fallbackWsList) {
        dev.pluck.backend.model.Workspace personalWs = authService.ensurePersonalWorkspace(user);
        String defaultWs = personalWs != null ? personalWs.getId() : ("ws_" + user.getId());

        if (headerWs != null && !headerWs.isBlank() && !"ws_personal".equals(headerWs.trim())) {
            String candidate = headerWs.trim();
            Optional<dev.pluck.backend.model.Workspace> wsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(candidate);
            if (wsOpt.isPresent()) {
                dev.pluck.backend.model.Workspace ws = wsOpt.get();
                if (user.getId().equals(ws.getOwnerUserId())) {
                    return candidate;
                }
            }
        }

        if (fallbackWsList != null && !fallbackWsList.isEmpty()) {
            String candidate = fallbackWsList.get(0);
            if (!"ws_personal".equals(candidate)) {
                Optional<dev.pluck.backend.model.Workspace> wsOpt = workspaceRepository.findByIdAndDeletedAtIsNull(candidate);
                if (wsOpt.isPresent()) {
                    dev.pluck.backend.model.Workspace ws = wsOpt.get();
                    if (user.getId().equals(ws.getOwnerUserId())) {
                        return candidate;
                    }
                }
            }
        }

        return defaultWs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Check Authorization header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String tokenStr = authHeader.substring(7).trim();

                // Case A: Developer Personal Access Token (PAT)
                if (tokenStr.startsWith("pluck_pat_")) {
                    Token pat = authService.authenticatePat(tokenStr);
                    if (pat != null) {
                        Optional<User> userOpt = userRepository.findById(pat.getUserId());
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            String headerWs = request.getHeader("X-Workspace-Id");
                            String activeWs = resolveUserWorkspace(user, headerWs, pat.getWorkspaceIds());
                            PluckPrincipal principal = new PluckPrincipal(user, "PAT", pat, pat.getScopes(), activeWs);
                            SecurityContextHolder.getContext().setAuthentication(principal);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }
                } else {
                    // Case B: Bearer JWT session token
                    Claims claims = authService.parseAndValidateToken(tokenStr);
                    if (claims != null) {
                        String userId = claims.getSubject();
                        Optional<User> userOpt = userRepository.findById(userId);
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            String headerWs = request.getHeader("X-Workspace-Id");
                            String activeWs = resolveUserWorkspace(user, headerWs, null);
                            PluckPrincipal principal = new PluckPrincipal(user, "SESSION", null, List.of("all"), activeWs);
                            SecurityContextHolder.getContext().setAuthentication(principal);
                            filterChain.doFilter(request, response);
                            return;
                        }
                    }
                }
            }

            // 2. Check Cookie ("pluck_session" or "__Host-pluck_session")
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("pluck_session".equals(cookie.getName()) || "__Host-pluck_session".equals(cookie.getName())) {
                        String sessionToken = cookie.getValue();
                        if (sessionToken != null && !sessionToken.isBlank()) {
                            Claims claims = authService.parseAndValidateToken(sessionToken);
                            if (claims != null) {
                                String userId = claims.getSubject();
                                Optional<User> userOpt = userRepository.findById(userId);
                                if (userOpt.isPresent()) {
                                    User user = userOpt.get();
                                    String headerWs = request.getHeader("X-Workspace-Id");
                                    String activeWs = resolveUserWorkspace(user, headerWs, null);
                                    PluckPrincipal principal = new PluckPrincipal(user, "SESSION", null, List.of("all"), activeWs);
                                    SecurityContextHolder.getContext().setAuthentication(principal);
                                    filterChain.doFilter(request, response);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // In case of error in token parsing, proceed anonymously
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
