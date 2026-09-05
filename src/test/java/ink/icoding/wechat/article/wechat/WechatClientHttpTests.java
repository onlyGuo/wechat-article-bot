package ink.icoding.wechat.article.wechat;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatClientHttpTests {

    @Test
    void jsonPostCarriesContentLengthInsteadOfChunkedEncoding() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> contentLength = new AtomicReference<>();
        AtomicReference<String> transferEncoding = new AtomicReference<>();
        server.createContext("/stable_token", exchange -> {
            contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
            transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"access_token\":\"test\",\"expires_in\":7200}".getBytes(StandardCharsets.UTF_8);
            // Several WeChat endpoints return a JSON body while declaring text/plain.
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            byte[] body = WechatClient.createRestClient().post()
                    .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/stable_token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("grant_type", "client_credential", "appid", "test", "secret", "test"))
                    .retrieve().body(byte[].class);
            Map<?, ?> response = WechatClient.decodeResponse(body);

            assertEquals("test", response.get("access_token"));
            assertTrue(Long.parseLong(contentLength.get()) > 0);
            assertNull(transferEncoding.get());
        } finally {
            server.stop(0);
        }
    }
}
