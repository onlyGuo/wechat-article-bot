package ink.icoding.wechat.article.auth;

import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    public CurrentUser required() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser currentUser) return currentUser;
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录已失效");
    }
}
