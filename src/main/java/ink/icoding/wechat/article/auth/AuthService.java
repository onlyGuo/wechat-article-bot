package ink.icoding.wechat.article.auth;

import ink.icoding.wechat.article.auth.model.AuthToken;
import ink.icoding.wechat.article.auth.model.SystemUser;
import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final AuthTokenMapper tokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final long tokenTtlHours;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserMapper userMapper, AuthTokenMapper tokenMapper, PasswordEncoder passwordEncoder,
                       @Value("${app.security.token-ttl-hours:24}") long tokenTtlHours) {
        this.userMapper = userMapper;
        this.tokenMapper = tokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtlHours = tokenTtlHours;
    }

    public LoginResult login(String username, String password) {
        SystemUser user = userMapper.findByUsername(username);
        if (user == null || !"ACTIVE".equals(user.getStatus()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthToken token = new AuthToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusHours(tokenTtlHours));
        token.setCreatedAt(LocalDateTime.now());
        tokenMapper.insert(token);
        userMapper.touchLogin(user.getId());
        return new LoginResult(rawToken, toCurrentUser(user), token.getExpiresAt());
    }

    public CurrentUser authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        AuthToken token = tokenMapper.findValid(hash(rawToken));
        if (token == null) return null;
        SystemUser user = userMapper.findById(token.getUserId());
        if (user == null || !"ACTIVE".equals(user.getStatus())) return null;
        return toCurrentUser(user);
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) tokenMapper.delete(hash(rawToken));
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        SystemUser user = userMapper.findById(userId);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("当前密码不正确");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(newPassword));
        tokenMapper.deleteByUserId(userId);
    }

    private CurrentUser toCurrentUser(SystemUser user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record LoginResult(String token, CurrentUser user, LocalDateTime expiresAt) {}
}
