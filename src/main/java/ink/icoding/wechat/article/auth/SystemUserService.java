package ink.icoding.wechat.article.auth;

import ink.icoding.wechat.article.auth.model.SystemUser;
import ink.icoding.wechat.article.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

@Service
public class SystemUserService {
    private static final Set<String> ROLES = Set.of("ADMIN", "OPERATOR", "EDITOR", "REVIEWER", "VIEWER");
    private final UserMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public SystemUserService(UserMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserView> list() { return mapper.findAll().stream().map(this::view).toList(); }

    public UserView create(CreateRequest request) {
        if (mapper.findByUsername(request.username()) != null) throw new BusinessException("用户名已存在");
        validateRole(request.role());
        SystemUser user = new SystemUser();
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus("ACTIVE");
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        mapper.insert(user);
        return view(mapper.findById(user.getId()));
    }

    public UserView update(Long id, UpdateRequest request) {
        SystemUser user = required(id);
        validateRole(request.role());
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        user.setStatus(request.status());
        mapper.update(user);
        return view(required(id));
    }

    public void resetPassword(Long id, PasswordRequest request) {
        required(id);
        mapper.updatePassword(id, passwordEncoder.encode(request.password()));
    }

    private SystemUser required(Long id) {
        SystemUser user = mapper.findById(id);
        if (user == null) throw new BusinessException("系统用户不存在");
        return user;
    }

    private void validateRole(String role) {
        if (!ROLES.contains(role)) throw new BusinessException("无效的角色");
    }

    private UserView view(SystemUser user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.getStatus(),
                user.getLastLoginAt(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public record CreateRequest(@NotBlank String username, @NotBlank String displayName,
                                @NotBlank @Size(min = 8) String password, @NotBlank String role) {}
    public record UpdateRequest(@NotBlank String displayName, @NotBlank String role, @NotBlank String status) {}
    public record PasswordRequest(@NotBlank @Size(min = 8) String password) {}
    public record UserView(Long id, String username, String displayName, String role, String status,
                           java.time.LocalDateTime lastLoginAt, java.time.LocalDateTime createdAt,
                           java.time.LocalDateTime updatedAt) {}
}
