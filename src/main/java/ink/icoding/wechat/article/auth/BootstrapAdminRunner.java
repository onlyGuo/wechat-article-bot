package ink.icoding.wechat.article.auth;

import ink.icoding.wechat.article.auth.model.SystemUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public BootstrapAdminRunner(UserMapper userMapper, PasswordEncoder passwordEncoder,
                                @Value("${app.bootstrap.admin-username}") String username,
                                @Value("${app.bootstrap.admin-password}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userMapper.count() > 0) return;
        SystemUser user = new SystemUser();
        user.setUsername(username);
        user.setDisplayName("系统管理员");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("ADMIN");
        user.setStatus("ACTIVE");
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.insert(user);
        log.warn("已创建初始管理员账号 {}，请登录后尽快修改默认密码", username);
    }
}
