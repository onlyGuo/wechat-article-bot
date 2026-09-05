package ink.icoding.wechat.article.follower;

import ink.icoding.wechat.article.account.WechatAccountService;
import ink.icoding.wechat.article.common.PageResult;
import ink.icoding.wechat.article.wechat.WechatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WechatFollowerService {
    private final WechatFollowerMapper mapper;
    private final WechatAccountService accountService;
    private final WechatClient wechatClient;

    public WechatFollowerService(WechatFollowerMapper mapper, WechatAccountService accountService, WechatClient wechatClient) {
        this.mapper = mapper;
        this.accountService = accountService;
        this.wechatClient = wechatClient;
    }

    public PageResult<WechatFollower> list(Long accountId, String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, pageSize));
        return new PageResult<>(mapper.findPage(accountId, keyword, (safePage - 1) * safeSize, safeSize),
                mapper.countPage(accountId, keyword), safePage, safeSize);
    }

    @SuppressWarnings("unchecked")
    public SyncResult sync(Long accountId) {
        accountService.required(accountId);
        List<String> openIds = new ArrayList<>();
        String next = "";
        for (int page = 0; page < 100; page++) {
            Map<String, Object> response = wechatClient.followerPage(accountId, next);
            Object dataValue = response.get("data");
            if (dataValue instanceof Map<?, ?> data && data.get("openid") instanceof List<?> ids) {
                ids.forEach(id -> openIds.add(String.valueOf(id)));
            }
            String newNext = response.get("next_openid") == null ? "" : String.valueOf(response.get("next_openid"));
            if (newNext.isBlank() || newNext.equals(next)) break;
            next = newNext;
        }
        int saved = 0;
        for (int start = 0; start < openIds.size(); start += 100) {
            List<String> batch = openIds.subList(start, Math.min(openIds.size(), start + 100));
            for (Map<String, Object> profile : wechatClient.followerProfiles(accountId, batch)) {
                mapper.upsert(fromProfile(accountId, profile));
                saved++;
            }
        }
        return new SyncResult(openIds.size(), saved);
    }

    private WechatFollower fromProfile(Long accountId, Map<String, Object> profile) {
        WechatFollower follower = new WechatFollower();
        follower.setAccountId(accountId);
        follower.setOpenId(string(profile, "openid"));
        follower.setUnionId(string(profile, "unionid"));
        follower.setNickname(string(profile, "nickname"));
        follower.setAvatarUrl(string(profile, "headimgurl"));
        follower.setRemark(string(profile, "remark"));
        Object tags = profile.get("tagid_list");
        if (tags instanceof List<?> list) follower.setTags(list.stream().map(String::valueOf).collect(Collectors.joining(",")));
        follower.setSubscribed(number(profile, "subscribe") == 1);
        long timestamp = number(profile, "subscribe_time");
        if (timestamp > 0) follower.setSubscribedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()));
        return follower;
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }

    public record SyncResult(int discovered, int saved) {}
}
