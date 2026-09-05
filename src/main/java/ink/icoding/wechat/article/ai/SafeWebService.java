package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.wechat.article.common.BusinessException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SafeWebService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public List<SearchResult> searchWeb(String query, int limit) {
        String url = "https://www.bing.com/search?q=" + encode(query);
        Document document = Jsoup.parse(getText(url, 1_500_000), url);
        List<SearchResult> results = new ArrayList<>();
        for (Element item : document.select("li.b_algo")) {
            Element link = item.selectFirst("h2 a[href]");
            if (link == null) continue;
            String target = link.absUrl("href");
            if (!isPublicHttpUrl(target)) continue;
            Element summary = item.selectFirst(".b_caption p");
            results.add(new SearchResult(link.text(), target, summary == null ? "" : summary.text()));
            if (results.size() >= clamp(limit, 1, 10)) break;
        }
        return results;
    }

    public List<ImageSearchResult> searchImages(String query, int limit) {
        String url = "https://www.bing.com/images/search?q=" + encode(query);
        Document document = Jsoup.parse(getText(url, 2_000_000), url);
        List<ImageSearchResult> results = new ArrayList<>();
        for (Element item : document.select("a.iusc[m]")) {
            try {
                JsonNode metadata = MAPPER.readTree(item.attr("m"));
                String imageUrl = metadata.path("murl").asText();
                String sourceUrl = metadata.path("purl").asText();
                if (!isPublicHttpUrl(imageUrl) || !isPublicHttpUrl(sourceUrl)) continue;
                results.add(new ImageSearchResult(metadata.path("t").asText(), imageUrl, sourceUrl));
                if (results.size() >= clamp(limit, 1, 10)) break;
            } catch (Exception ignored) {
            }
        }
        return results;
    }

    public PageContent browse(String url) {
        String html = getText(url, 2_000_000);
        Document document = Jsoup.parse(html, url);
        document.select("script,style,noscript,svg,nav,footer,form").remove();
        String text = document.body() == null ? "" : document.body().text();
        if (text.length() > 14_000) text = text.substring(0, 14_000);
        return new PageContent(document.title(), document.location(), text);
    }

    public BinaryResponse downloadImage(String url) {
        HttpResponse<InputStream> response = sendFollowingRedirects(url, "image/*", 3);
        String contentType = response.headers().firstValue("content-type").orElse("")
                .split(";", 2)[0].trim().toLowerCase();
        if (!IMAGE_TYPES.contains(contentType)) throw new BusinessException("网络地址返回的不是支持的图片格式");
        byte[] bytes = readLimited(response.body(), 10 * 1024 * 1024);
        return new BinaryResponse(response.uri().toString(), contentType, bytes);
    }

    private String getText(String url, int maxBytes) {
        HttpResponse<InputStream> response = sendFollowingRedirects(url, "text/html,application/xhtml+xml", 3);
        String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase();
        if (!contentType.contains("text/html") && !contentType.contains("text/plain")
                && !contentType.contains("application/xhtml")) {
            throw new BusinessException("网页返回了不支持的内容类型");
        }
        return new String(readLimited(response.body(), maxBytes), StandardCharsets.UTF_8);
    }

    private HttpResponse<InputStream> sendFollowingRedirects(String value, String accept, int maxRedirects) {
        try {
            URI uri = URI.create(value);
            for (int redirect = 0; redirect <= maxRedirects; redirect++) {
                validatePublicUri(uri);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "Mozilla/5.0 WechatArticleAssistant/1.0")
                        .header("Accept", accept)
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new BusinessException("网页重定向缺少地址"));
                    uri = uri.resolve(location);
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new BusinessException("网页请求失败（HTTP " + response.statusCode() + "）");
                }
                return response;
            }
            throw new BusinessException("网页重定向次数过多");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("网页访问失败：" + exception.getMessage());
        }
    }

    private void validatePublicUri(URI uri) throws Exception {
        if (uri == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) throw new BusinessException("仅允许访问 HTTP/HTTPS 公网地址");
        String host = uri.getHost();
        String lowerHost = host.toLowerCase();
        if ("localhost".equals(lowerHost) || lowerHost.endsWith(".localhost")
                || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")
                || lowerHost.endsWith(".ts.net")) {
            throw new BusinessException("禁止访问内网或本机地址（" + host + "）");
        }
        boolean literalAddress = isIpLiteral(host);
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            byte[] raw = address.getAddress();
            boolean carrierGradeNat = raw.length == 4 && (raw[0] & 0xff) == 100 && (raw[1] & 0xc0) == 64;
            boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
            boolean proxyFakeV6 = isProxyFakeIpv6(raw);
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || (carrierGradeNat && literalAddress)
                    || (uniqueLocalV6 && (literalAddress || !proxyFakeV6))) {
                throw new BusinessException("禁止访问内网或本机地址（" + host + " -> "
                        + address.getHostAddress() + "）");
            }
        }
    }

    private boolean isProxyFakeIpv6(byte[] raw) {
        return raw.length == 16
                && (raw[0] & 0xff) == 0xfd && (raw[1] & 0xff) == 0xfe
                && (raw[2] & 0xff) == 0xdc && (raw[3] & 0xff) == 0xba
                && (raw[4] & 0xff) == 0x98 && (raw[5] & 0xff) == 0x76;
    }

    private boolean isIpLiteral(String host) {
        if (host == null || host.isBlank()) return false;
        if (host.indexOf(':') >= 0) return true;
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return true;
    }

    private boolean isPublicHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private byte[] readLimited(InputStream input, int limit) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0; ) {
                total += read;
                if (total > limit) throw new BusinessException("远程内容超过大小限制");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("远程内容读取失败：" + exception.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public record SearchResult(String title, String url, String summary) {}
    public record ImageSearchResult(String title, String imageUrl, String sourcePageUrl) {}
    public record PageContent(String title, String url, String text) {}
    public record BinaryResponse(String finalUrl, String contentType, byte[] bytes) {}
}
