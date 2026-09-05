package ink.icoding.wechat.article.wechat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.wechat.article.account.WechatAccount;
import ink.icoding.wechat.article.account.WechatAccountMapper;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.CryptoService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class WechatClient {
    private static final String API = "https://api.weixin.qq.com";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private final WechatAccountMapper accountMapper;
    private final CryptoService cryptoService;
    private final RestClient restClient;

    public WechatClient(WechatAccountMapper accountMapper, CryptoService cryptoService) {
        this.accountMapper = accountMapper;
        this.cryptoService = cryptoService;
        this.restClient = createRestClient();
    }

    /**
     * WeChat rejects some chunked POST requests with an empty HTTP 412 response. Buffering makes
     * Spring calculate and send Content-Length for JSON and multipart requests.
     */
    static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        return RestClient.builder()
                .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
                .build();
    }

    public String token(Long accountId) {
        WechatAccount account = requiredAccount(accountId);
        if (account.getTokenEncrypted() != null && account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            return cryptoService.decrypt(account.getTokenEncrypted());
        }
        return refresh(account);
    }

    public String forceRefreshToken(Long accountId) {
        return refresh(requiredAccount(accountId));
    }

    private String refresh(WechatAccount account) {
        try {
            Map<String, Object> response = decodeResponse(restClient.post().uri(API + "/cgi-bin/stable_token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("grant_type", "client_credential", "appid", account.getAppId(),
                            "secret", cryptoService.decrypt(account.getAppSecretEncrypted()), "force_refresh", true))
                    .retrieve().body(byte[].class));
            ensureSuccess(response, "获取 Access Token");
            String accessToken = String.valueOf(response.get("access_token"));
            int expiresIn = ((Number) response.getOrDefault("expires_in", 7200)).intValue();
            accountMapper.updateToken(account.getId(), cryptoService.encrypt(accessToken),
                    LocalDateTime.now().plusSeconds(Math.max(300, expiresIn - 120)), "CONNECTED");
            return accessToken;
        } catch (BusinessException exception) {
            accountMapper.updateConnection(account.getId(), "FAILED");
            throw exception;
        } catch (RestClientResponseException exception) {
            accountMapper.updateConnection(account.getId(), "FAILED");
            throw new BusinessException(readableHttpError(exception));
        } catch (Exception exception) {
            accountMapper.updateConnection(account.getId(), "FAILED");
            throw new BusinessException("连接微信失败：" + exception.getMessage());
        }
    }

    public String uploadThumb(Long accountId, Path file) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("media", new FileSystemResource(file));
        Map<String, Object> response = decodeResponse(restClient.post()
                .uri(API + "/cgi-bin/material/add_material?access_token=" + token(accountId) + "&type=thumb")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(byte[].class));
        ensureSuccess(response, "上传永久封面素材");
        Object mediaId = response.get("media_id");
        if (mediaId == null) throw new BusinessException("微信未返回封面 media_id");
        return String.valueOf(mediaId);
    }

    public String uploadArticleImage(Long accountId, Path file) {
        try (WechatImageProcessor.PreparedImage prepared = WechatImageProcessor.prepare(file)) {
            var body = new LinkedMultiValueMap<String, Object>();
            body.add("media", new FileSystemResource(prepared.path()));
            Map<String, Object> response = decodeResponse(restClient.post()
                    .uri(API + "/cgi-bin/media/uploadimg?access_token=" + token(accountId))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(byte[].class));
            ensureSuccess(response, "上传微信正文图片");
            Object url = response.get("url");
            if (url == null || String.valueOf(url).isBlank()) throw new BusinessException("微信未返回正文图片 URL");
            return String.valueOf(url);
        }
    }

    @SuppressWarnings("unchecked")
    public String addDraft(Long accountId, Map<String, Object> article) {
        Map<String, Object> response = post(accountId, "/cgi-bin/draft/add",
                Map.of("articles", List.of(article)), "创建微信草稿");
        Object mediaId = response.get("media_id");
        if (mediaId == null) throw new BusinessException("微信未返回草稿 media_id");
        return String.valueOf(mediaId);
    }

    public void updateDraft(Long accountId, String mediaId, Map<String, Object> article) {
        post(accountId, "/cgi-bin/draft/update",
                Map.of("media_id", mediaId, "index", 0, "articles", article), "更新微信草稿");
    }

    public String publish(Long accountId, String mediaId) {
        Map<String, Object> response = post(accountId, "/cgi-bin/freepublish/submit",
                Map.of("media_id", mediaId), "提交微信发布");
        Object publishId = response.get("publish_id");
        if (publishId == null) throw new BusinessException("微信未返回发布任务 ID");
        return String.valueOf(publishId);
    }

    public Map<String, Object> publishStatus(Long accountId, String publishId) {
        return post(accountId, "/cgi-bin/freepublish/get",
                Map.of("publish_id", publishId), "查询微信发布状态");
    }

    public Map<String, Object> followerPage(Long accountId, String nextOpenId) {
        String uri = API + "/cgi-bin/user/get?access_token=" + token(accountId)
                + "&next_openid=" + (nextOpenId == null ? "" : nextOpenId);
        Map<String, Object> response = decodeResponse(restClient.get().uri(uri).retrieve().body(byte[].class));
        ensureSuccess(response, "获取公众号用户列表");
        return response;
    }

    public List<Map<String, Object>> followerProfiles(Long accountId, List<String> openIds) {
        if (openIds.isEmpty()) return List.of();
        List<Map<String, String>> users = openIds.stream()
                .map(openId -> Map.of("openid", openId, "lang", "zh_CN")).toList();
        Map<String, Object> response = post(accountId, "/cgi-bin/user/info/batchget",
                Map.of("user_list", users), "批量获取公众号用户资料");
        Object list = response.get("user_info_list");
        if (!(list instanceof List<?> values)) return List.of();
        return values.stream().filter(Map.class::isInstance).map(value -> (Map<String, Object>) value).toList();
    }

    private Map<String, Object> post(Long accountId, String path, Object body, String operation) {
        Map<String, Object> response = decodeResponse(restClient.post()
                .uri(API + path + "?access_token=" + token(accountId))
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(byte[].class));
        ensureSuccess(response, operation);
        return response;
    }

    static Map<String, Object> decodeResponse(byte[] body) {
        if (body == null || body.length == 0) throw new BusinessException("微信接口返回为空");
        try {
            return JSON.readValue(body, JSON_MAP);
        } catch (Exception exception) {
            String text = new String(body, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            if (text.length() > 500) text = text.substring(0, 500) + "…";
            throw new BusinessException("微信接口返回了无法识别的内容：" + text);
        }
    }

    private void ensureSuccess(Map<String, Object> response, String operation) {
        if (response == null) throw new BusinessException("微信接口返回为空");
        Object codeValue = response.get("errcode");
        int code = codeValue instanceof Number number ? number.intValue() : 0;
        if (code == 48001) {
            if ("提交微信发布".equals(operation)) {
                throw new BusinessException("微信拒绝“提交微信发布”：当前公众号没有发布接口权限（48001）。"
                        + "自 2025 年 7 月起，个人主体、企业主体未认证及不支持认证的账号不再具备该权限；"
                        + "已同步的文章仍可在微信草稿箱中手动发布");
            }
            throw new BusinessException("微信拒绝“" + operation + "”：当前公众号没有该接口权限（48001）。"
                    + "请在微信公众平台的接口权限页面确认账号已获得此能力");
        }
        if (code != 0) throw new BusinessException("微信“" + operation + "”失败 " + code + "："
                + response.getOrDefault("errmsg", "unknown"));
    }

    private String readableHttpError(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        if (body != null) body = body.replaceAll("\\s+", " ").trim();
        if (body != null && body.length() > 500) body = body.substring(0, 500) + "…";
        String prefix = "连接微信失败（HTTP " + exception.getStatusCode().value() + "）";
        if (body != null && !body.isBlank()) return prefix + "：" + body;
        if (exception.getStatusCode().value() == 412) {
            return prefix + "：微信接口未返回错误详情。项目已发送 Content-Length；"
                    + "如果仍然出现此错误，请检查服务器出口代理是否删除或改写了该请求头。";
        }
        return prefix + "：微信接口未返回错误详情";
    }

    private WechatAccount requiredAccount(Long id) {
        WechatAccount account = accountMapper.findById(id);
        if (account == null) throw new BusinessException("公众号不存在");
        if (!"ACTIVE".equals(account.getStatus())) throw new BusinessException("公众号已停用");
        return account;
    }
}
