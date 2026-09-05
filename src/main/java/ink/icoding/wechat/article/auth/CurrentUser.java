package ink.icoding.wechat.article.auth;

public record CurrentUser(Long id, String username, String displayName, String role) {
}
