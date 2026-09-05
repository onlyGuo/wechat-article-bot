package ink.icoding.wechat.article.account;

import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import ink.icoding.wechat.article.wechat.WechatClient;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

@Service
public class WechatAccountService {
    private final WechatAccountMapper mapper;
    private final CryptoService cryptoService;
    private final WechatClient wechatClient;

    public WechatAccountService(WechatAccountMapper mapper, CryptoService cryptoService, WechatClient wechatClient) {
        this.mapper = mapper;
        this.cryptoService = cryptoService;
        this.wechatClient = wechatClient;
    }

    public List<AccountView> list() {
        return mapper.findAll().stream().map(this::view).toList();
    }

    public AccountView get(Long id) {
        return view(required(id));
    }

    @Transactional
    public AccountView create(AccountRequest request) {
        if (request.appSecret() == null || request.appSecret().isBlank()) {
            throw new BusinessException("AppSecret 不能为空");
        }
        WechatAccount account = new WechatAccount();
        apply(account, request);
        account.setAppSecretEncrypted(cryptoService.encrypt(request.appSecret().trim()));
        account.setStatus("ACTIVE");
        account.setConnectionStatus("UNCHECKED");
        account.setCapabilities("DRAFT,PUBLISH,MATERIAL,FOLLOWER");
        account.setCreatedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        mapper.insert(account);
        return view(required(account.getId()));
    }

    @Transactional
    public AccountView update(Long id, AccountRequest request) {
        WechatAccount account = required(id);
        apply(account, request);
        if (request.appSecret() != null && !request.appSecret().isBlank()) {
            account.setAppSecretEncrypted(cryptoService.encrypt(request.appSecret().trim()));
        }
        mapper.update(account);
        return view(required(id));
    }

    public AccountView test(Long id) {
        wechatClient.forceRefreshToken(id);
        return view(required(id));
    }

    @Transactional
    public void delete(Long id) {
        required(id);
        mapper.delete(id);
    }

    public WechatAccount required(Long id) {
        WechatAccount account = mapper.findById(id);
        if (account == null) throw new BusinessException("公众号不存在");
        return account;
    }

    private void apply(WechatAccount account, AccountRequest request) {
        account.setName(request.name().trim());
        account.setAppId(request.appId().trim());
        account.setOriginalId(request.originalId());
        account.setAccountType(request.accountType() == null ? "SERVICE" : request.accountType());
        account.setVerified(Boolean.TRUE.equals(request.verified()));
        account.setAvatarUrl(request.avatarUrl());
        account.setDefaultAuthor(request.defaultAuthor());
        account.setDefaultStyle(request.defaultStyle());
        if (request.status() != null) account.setStatus(request.status());
    }

    private AccountView view(WechatAccount account) {
        return new AccountView(account.getId(), account.getName(), account.getAppId(), account.getOriginalId(),
                account.getAccountType(), account.getVerified(), account.getAvatarUrl(), account.getDefaultAuthor(),
                account.getDefaultStyle(), account.getStatus(), account.getConnectionStatus(), account.getCapabilities(),
                account.getTokenExpiresAt(), account.getLastCheckedAt(), account.getCreatedAt(), account.getUpdatedAt());
    }

    public record AccountRequest(@NotBlank String name, @NotBlank String appId, String appSecret, String originalId,
                                 String accountType, Boolean verified, String avatarUrl, String defaultAuthor,
                                 String defaultStyle, String status) {}

    public record AccountView(Long id, String name, String appId, String originalId, String accountType,
                              Boolean verified, String avatarUrl, String defaultAuthor, String defaultStyle,
                              String status, String connectionStatus, String capabilities,
                              java.time.LocalDateTime tokenExpiresAt, java.time.LocalDateTime lastCheckedAt,
                              java.time.LocalDateTime createdAt, java.time.LocalDateTime updatedAt) {}
}
