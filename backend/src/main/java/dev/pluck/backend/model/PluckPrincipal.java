package dev.pluck.backend.model;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PluckPrincipal implements Authentication {

    private final User user;
    private final String authType; // "SESSION" or "PAT"
    private final Token patToken;
    private final List<String> scopes;
    private String activeWorkspaceId;
    private boolean authenticated = true;

    public PluckPrincipal(User user, String authType, Token patToken, List<String> scopes, String activeWorkspaceId) {
        this.user = user;
        this.authType = authType;
        this.patToken = patToken;
        this.scopes = scopes != null ? scopes : List.of();
        this.activeWorkspaceId = activeWorkspaceId;
    }

    public User getUser() {
        return user;
    }

    public String getAuthType() {
        return authType;
    }

    public Token getPatToken() {
        return patToken;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public String getActiveWorkspaceId() {
        return activeWorkspaceId;
    }

    public void setActiveWorkspaceId(String activeWorkspaceId) {
        this.activeWorkspaceId = activeWorkspaceId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = scopes.stream()
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                .collect(Collectors.toList());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return user;
    }

    @Override
    public Object getPrincipal() {
        return user;
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.authenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return user != null ? user.getEmail() : "anonymous";
    }
}
