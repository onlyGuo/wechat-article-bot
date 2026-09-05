package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.LlmConfigService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class ImageGenerationService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration IMAGE_REQUEST_TIMEOUT = Duration.ofMinutes(10);
    private final LlmConfigService configService;
    private final AssetService assetService;
    private final SafeWebService webService;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public ImageGenerationService(LlmConfigService configService, AssetService assetService,
                                  SafeWebService webService) {
        this.configService = configService;
        this.assetService = assetService;
        this.webService = webService;
    }

    public Asset generate(Long accountId, String prompt, String filename, Long userId) {
        LlmConfigService.RuntimeConfig config = requiredConfig();
        try {
            byte[] body = MAPPER.writeValueAsBytes(Map.of(
                    "model", config.imageModelName(), "prompt", prompt,
                    "size", "1024x1024", "n", 1, "response_format", "b64_json"));
            HttpRequest request = request(config, "/v1/images/generations")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            ImagePayload image = parseImageResponse(send(request));
            return assetService.saveImage(accountId, defaultName(filename, "ai-generated.png"), image.contentType,
                    image.bytes, "AI_GENERATED", null, prompt, userId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("AI 图片生成失败：" + exception.getMessage());
        }
    }

    public Asset edit(Long accountId, Long sourceAssetId, String prompt, String filename, Long userId) {
        LlmConfigService.RuntimeConfig config = requiredConfig();
        Asset source = assetService.required(sourceAssetId);
        byte[] sourceBytes = assetService.readBytes(sourceAssetId);
        try {
            String boundary = "----WechatArticle" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipart(boundary, config.imageModelName(), prompt, source, sourceBytes);
            HttpRequest request = request(config, "/v1/images/edits")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            ImagePayload image = parseImageResponse(send(request));
            return assetService.saveImage(accountId, defaultName(filename, "ai-edited.png"), image.contentType,
                    image.bytes, "AI_EDITED", source.getPublicUrl(), prompt, userId);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("AI 图片编辑失败：" + exception.getMessage());
        }
    }

    private LlmConfigService.RuntimeConfig requiredConfig() {
        LlmConfigService.RuntimeConfig config = configService.runtime();
        if (!config.imageAvailable()) throw new BusinessException("尚未在系统设置中配置图片模型");
        return config;
    }

    private HttpRequest.Builder request(LlmConfigService.RuntimeConfig config, String path) {
        return HttpRequest.newBuilder(URI.create(config.imageBaseUrl() + path))
                .timeout(IMAGE_REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.imageApiKey())
                .header("Accept", "application/json");
    }

    private byte[] send(HttpRequest request) throws Exception {
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = new String(response.body(), StandardCharsets.UTF_8);
            if (message.length() > 1000) message = message.substring(0, 1000);
            throw new BusinessException("图片服务 HTTP " + response.statusCode() + "：" + message);
        }
        return response.body();
    }

    private ImagePayload parseImageResponse(byte[] response) throws Exception {
        JsonNode item = MAPPER.readTree(response).path("data").path(0);
        String base64 = item.path("b64_json").asText();
        if (!base64.isBlank()) return new ImagePayload("image/png", Base64.getDecoder().decode(base64));
        String url = item.path("url").asText();
        if (!url.isBlank()) {
            SafeWebService.BinaryResponse image = webService.downloadImage(url);
            return new ImagePayload(image.contentType(), image.bytes());
        }
        throw new BusinessException("图片服务没有返回图片数据");
    }

    private byte[] multipart(String boundary, String model, String prompt, Asset source, byte[] image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        field(output, boundary, "model", model);
        field(output, boundary, "prompt", prompt);
        field(output, boundary, "size", "1024x1024");
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\""
                + safeFilename(source.getOriginalName()) + "\"\r\nContent-Type: " + source.getContentType()
                + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(image);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private void field(ByteArrayOutputStream output, String boundary, String name, String value) throws Exception {
        output.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private String safeFilename(String value) {
        return Path.of(value == null ? "image.png" : value).getFileName().toString().replace("\"", "");
    }

    private String defaultName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record ImagePayload(String contentType, byte[] bytes) {}
}
