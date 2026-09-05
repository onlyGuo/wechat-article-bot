package ink.icoding.wechat.article;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.sql.Connection;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = MySqlTestDatabaseInitializer.class)
class CoreApiIntegrationTests {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern EDITOR_SESSION = Pattern.compile("\\\"sessionId\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loginThenCreateAndQueryArticle() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String databaseProduct = connection.getMetaData().getDatabaseProductName();
            if (!"MySQL".equals(databaseProduct)) {
                throw new AssertionError("集成测试必须使用 MySQL，实际为 " + databaseProduct);
            }
        }

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("登录已失效"));

        mockMvc.perform(get("/api/articles").header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("登录已失效"));

        String loginJson = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andReturn().getResponse().getContentAsString();

        Matcher matcher = TOKEN.matcher(loginJson);
        if (!matcher.find()) throw new AssertionError("登录响应中缺少 token");
        String authorization = "Bearer " + matcher.group(1);

        mockMvc.perform(post("/api/articles")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "接口验收文章",
                                  "author": "测试用户",
                                  "digest": "自动化测试创建",
                                  "contentHtml": "<h2>正文</h2><script>alert(1)</script><p>内容</p>"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("接口验收文章"))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.contentHtml").value("<h2>正文</h2><p>内容</p>"));

        var draftStream = mockMvc.perform(post("/api/articles/1/wechat-draft")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        draftStream.getAsyncResult(5_000L);
        mockMvc.perform(asyncDispatch(draftStream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    if (!body.contains("event:progress") || !body.contains("event:error")
                            || !body.contains("请先选择目标公众号")) {
                        throw new AssertionError("微信草稿 SSE 没有返回进度和错误事件: " + body);
                    }
                });

        mockMvc.perform(get("/api/articles")
                        .header("Authorization", authorization)
                        .param("status", "DRAFT")
                        .param("keyword", "接口验收"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("接口验收文章"));

        String unchangedArticle = """
                {
                  "title": "接口验收文章",
                  "author": "测试用户",
                  "digest": "自动化测试创建",
                  "contentHtml": "<h2>正文</h2><p>内容</p>",
                  "revision": 1
                }
                """;
        mockMvc.perform(put("/api/articles/1")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unchangedArticle))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(1));

        mockMvc.perform(get("/api/articles/1/revisions")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(put("/api/articles/1")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unchangedArticle.replace("接口验收文章", "接口验收文章（已修改）")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2));

        mockMvc.perform(put("/api/articles/1")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unchangedArticle.replace("接口验收文章", "过期版本修改")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("文章已被其他操作修改，请刷新后重试"));

        mockMvc.perform(post("/api/articles/1/ai/chat")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"把标题写得更简洁\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string("Cache-Control", "no-cache, no-transform"))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        mockMvc.perform(put("/api/settings/llm")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "OPENAI_RESPONSES",
                                  "baseUrl": "https://llm.example.test/v1/responses/",
                                  "modelName": "integration-model",
                                  "apiKey": "integration-secret-key",
                                  "enabled": true,
                                  "temperature": 0.5,
                                  "maxTokens": 2048
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseUrl").value("https://llm.example.test"))
                .andExpect(jsonPath("$.data.provider").value("OPENAI_RESPONSES"))
                .andExpect(jsonPath("$.data.modelName").value("integration-model"))
                .andExpect(jsonPath("$.data.hasApiKey").value(true));

        verifyBrowserToolCallingLoop(authorization);

        mockMvc.perform(put("/api/settings/llm")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "ANTHROPIC",
                                  "baseUrl": "https://api.anthropic.com/",
                                  "modelName": "claude-sonnet-test",
                                  "enabled": true,
                                  "temperature": 0.4,
                                  "maxTokens": 4096
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("ANTHROPIC"))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.anthropic.com"))
                .andExpect(jsonPath("$.data.modelName").value("claude-sonnet-test"))
                .andExpect(jsonPath("$.data.hasApiKey").value(true));

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "持久化调度验收",
                                  "cronExpression": "0 0 12 * * ?",
                                  "timezone": "Asia/Shanghai",
                                  "aiPrompt": "每天搜索并浏览人工智能行业动态，核实来源后创作一篇公众号文章。",
                                  "outputMode": "LOCAL_DRAFT",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("持久化调度验收"));

        Long persistedJobs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS", Long.class);
        if (persistedJobs == null || persistedJobs < 1) throw new AssertionError("Quartz 任务没有持久化到数据库");

        verifyScheduledAgentToolCalling(authorization);

        mockMvc.perform(put("/api/auth/password")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Admin@123\",\"newPassword\":\"Admin@456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("登录已失效"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin@456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void verifyBrowserToolCallingLoop(String authorization) throws Exception {
        AtomicInteger llmRequests = new AtomicInteger();
        List<String> llmRequestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> respondAsFakeResponsesApi(
                exchange, llmRequests.incrementAndGet(), llmRequestBodies));
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            mockMvc.perform(put("/api/settings/llm")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "provider": "OPENAI_RESPONSES",
                                      "baseUrl": "%s",
                                      "modelName": "tool-calling-test",
                                      "enabled": true,
                                      "temperature": 0.2,
                                      "maxTokens": 1024
                                    }
                                    """.formatted(baseUrl)))
                    .andExpect(status().isOk());

            var chat = mockMvc.perform(post("/api/articles/1/ai/chat")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instruction\":\"读取当前文章并告诉我标题\"}"))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String sessionId = waitForEditorSession(chat);
            mockMvc.perform(post("/api/articles/1/ai/sessions/{sessionId}/tools/tool-1/result", sessionId)
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "success": true,
                                      "result": "{\\\"title\\\":\\\"接口验收文章（已修改）\\\",\\\"documentVersion\\\":0,\\\"blocks\\\":[{\\\"line\\\":1,\\\"type\\\":\\\"heading\\\",\\\"text\\\":\\\"正文\\\"}]}",
                                      "document": {
                                        "title": "接口验收文章（已修改）",
                                        "digest": "自动化测试创建",
                                        "contentHtml": "<h2>正文</h2><p>内容</p>",
                                        "documentVersion": 0,
                                        "articleRevision": 2,
                                        "blocks": [{"line":1,"type":"heading","text":"正文","html":"<h2>正文</h2>"}]
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk());

            waitForSseMarker(chat, "tool-2", 8_000L);
            waitForSseMarker(chat, "event:editor.insert.completed", 8_000L);
            mockMvc.perform(post("/api/articles/1/ai/sessions/{sessionId}/tools/tool-2/result", sessionId)
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "success": true,
                                      "result": "{\\\"message\\\":\\\"已替换第1至2行\\\",\\\"documentVersion\\\":3}",
                                      "document": {
                                        "title": "接口验收文章（已修改）",
                                        "digest": "自动化测试创建",
                                        "contentHtml": "<h2>工具改写标题</h2><p>工具改写内容</p>",
                                        "documentVersion": 3,
                                        "articleRevision": 2,
                                        "blocks": [
                                          {"line":1,"type":"heading","text":"工具改写标题","html":"<h2>工具改写标题</h2>"},
                                          {"line":2,"type":"paragraph","text":"工具改写内容","html":"<p>工具改写内容</p>"}
                                        ]
                                      }
                                    }
                                    """))
                    .andExpect(status().isOk());

            chat.getAsyncResult(10_000L);
            mockMvc.perform(asyncDispatch(chat))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                        if (!body.contains("event:tool.call") || !body.contains("read_article")) {
                            throw new AssertionError("SSE 中没有浏览器工具调用过程: " + body);
                        }
                        if (!body.contains("\"arguments\":{\"reason\":\"读取浏览器当前文章\"}")) {
                            throw new AssertionError("工具参数没有按原始 JSON 下发给浏览器: " + body);
                        }
                        if (!body.contains("event:editor.insert.delta") || !body.contains("工具改写内容")) {
                            throw new AssertionError("替换工具没有通过 SSE 流式下发内容块: " + body);
                        }
                        long browserToolCalls = body.lines().filter("event:tool.call"::equals).count();
                        if (browserToolCalls != 2) {
                            throw new AssertionError("重复的模型工具调用不应再次下发浏览器，实际调用 " + browserToolCalls + " 次: " + body);
                        }
                        if (!body.contains("event:completed") || !body.contains("已通过工具完成文章改写")) {
                            throw new AssertionError("Tool Calling Agent 没有完成第二轮响应: " + body);
                        }
                    });
            if (llmRequests.get() != 3) throw new AssertionError("工具循环应请求 LLM 三次，实际为 " + llmRequests.get());
            String serializedSession = jdbcTemplate.queryForObject(
                    "SELECT SERIALIZED_SESSION FROM ARTICLE_AGENT_SESSION WHERE ARTICLE_ID = 1", String.class);
            if (serializedSession == null || !serializedSession.contains("\"toolCalls\"")
                    || !serializedSession.contains("\"toolResult\"")
                    || !serializedSession.contains("call-read-1")
                    || !serializedSession.contains("call-replace-1")) {
                throw new AssertionError("Agent4j Session 没有完整持久化工具调用链: " + serializedSession);
            }
            mockMvc.perform(get("/api/articles/1").header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.revision").value(3))
                    .andExpect(jsonPath("$.data.contentHtml").value("<h2>工具改写标题</h2><p>工具改写内容</p>"));

            byte[] png = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
            mockMvc.perform(multipart("/api/assets")
                            .file(new MockMultipartFile("file", "reference.png", "image/png", png))
                            .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.sourceType").value("USER_UPLOAD"));

            String localImageUrl = jdbcTemplate.queryForObject(
                    "SELECT PUBLIC_URL FROM ASSET WHERE ID = 1", String.class);
            mockMvc.perform(put("/api/articles/1")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "接口验收文章（已修改）",
                                      "author": "测试用户",
                                      "digest": "自动化测试创建",
                                      "contentHtml": "<h2>工具改写标题</h2><p><img src=\\\"%s\\\" alt=\\\"素材库图片\\\"></p>",
                                      "revision": 3
                                    }
                                    """.formatted(localImageUrl)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.revision").value(4))
                    .andExpect(jsonPath("$.data.contentHtml").value(
                            "<h2>工具改写标题</h2><p><img src=\"" + localImageUrl
                                    + "\" alt=\"素材库图片\"></p>"));

            mockMvc.perform(get("/api/articles/1").header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.contentHtml").value(
                            "<h2>工具改写标题</h2><p><img src=\"" + localImageUrl
                                    + "\" alt=\"素材库图片\"></p>"));

            var followUp = mockMvc.perform(post("/api/articles/1/ai/chat")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"instruction\":\"继续基于刚才的修改给一句总结\",\"assetIds\":[1]}"))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();
            followUp.getAsyncResult(10_000L);
            mockMvc.perform(asyncDispatch(followUp))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                        if (!body.contains("Agent4j Session 已恢复")) {
                            throw new AssertionError("恢复 Session 后没有完成第二轮对话: " + body);
                        }
                    });
            if (llmRequests.get() != 4) throw new AssertionError("第二轮应只请求 LLM 一次，实际总数为 " + llmRequests.get());
            String restoredRequest = llmRequestBodies.get(3);
            if (!restoredRequest.contains("function_call_output")
                    || !restoredRequest.contains("call-read-1")
                    || !restoredRequest.contains("call-replace-1")
                    || !restoredRequest.contains("input_image")
                    || !restoredRequest.contains("data:image/png;base64,")
                    || !restoredRequest.contains("继续基于刚才的修改给一句总结")) {
                throw new AssertionError("第二轮请求没有恢复 Agent4j 的完整工具上下文: " + restoredRequest);
            }
            Long agentSessionRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM ARTICLE_AGENT_SESSION WHERE ARTICLE_ID = 1", Long.class);
            if (agentSessionRows == null || agentSessionRows != 1L) {
                throw new AssertionError("每篇文章应该只持久化一个 Agent4j Session，实际为 " + agentSessionRows);
            }
        } finally {
            server.stop(0);
        }
    }

    private void verifyScheduledAgentToolCalling(String authorization) throws Exception {
        AtomicInteger llmRequests = new AtomicInteger();
        List<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int requestNumber = llmRequests.incrementAndGet();
            String events;
            if (requestNumber == 1) {
                String arguments = "{\\\"title\\\":\\\"定时 Agent 创作验收\\\","
                        + "\\\"author\\\":\\\"墨舟智能体\\\","
                        + "\\\"digest\\\":\\\"由定时智能体通过工具提交的文章\\\","
                        + "\\\"contentHtml\\\":\\\"<h2>今日观察</h2><p>这是完整的定时创作文章。</p>\\\","
                        + "\\\"sourceUrl\\\":\\\"https://example.com/source\\\"}";
                String tool = "{\"type\":\"function_call\",\"id\":\"fc-save-task\","
                        + "\"call_id\":\"call-save-task\",\"name\":\"save_article_draft\","
                        + "\"arguments\":\"" + arguments + "\"}";
                events = "data: {\"type\":\"response.output_item.added\",\"item\":" + tool + "}\n\n"
                        + "data: {\"type\":\"response.output_item.done\",\"item\":" + tool + "}\n\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[" + tool
                        + "],\"usage\":{\"input_tokens\":30,\"output_tokens\":8}}}\n\n";
            } else {
                events = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"定时文章已经通过工具提交。\"}\n\n"
                        + "data: {\"type\":\"response.output_text.done\"}\n\n"
                        + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[],"
                        + "\"usage\":{\"input_tokens\":40,\"output_tokens\":10}}}\n\n";
            }
            byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            mockMvc.perform(put("/api/settings/llm")
                            .header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "provider": "OPENAI_RESPONSES",
                                      "baseUrl": "%s",
                                      "modelName": "scheduled-agent-test",
                                      "enabled": true,
                                      "temperature": 0.2,
                                      "maxTokens": 2048
                                    }
                                    """.formatted(baseUrl)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/tasks/1/run")
                            .header("Authorization", authorization))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("RUNNING"));

            long deadline = System.currentTimeMillis() + 10_000L;
            String runStatus = "RUNNING";
            while (System.currentTimeMillis() < deadline) {
                runStatus = jdbcTemplate.queryForObject(
                        "SELECT STATUS FROM TASK_RUN ORDER BY ID DESC LIMIT 1", String.class);
                if (!"RUNNING".equals(runStatus)) break;
                Thread.sleep(25L);
            }
            if (!"SUCCESS".equals(runStatus)) {
                String message = jdbcTemplate.queryForObject(
                        "SELECT MESSAGE FROM TASK_RUN ORDER BY ID DESC LIMIT 1", String.class);
                throw new AssertionError("定时 Agent 没有成功完成：" + runStatus + " / " + message);
            }
            String scheduledTitle = jdbcTemplate.queryForObject(
                    "SELECT TITLE FROM ARTICLE WHERE SOURCE_TYPE = 'SCHEDULED' ORDER BY ID DESC LIMIT 1", String.class);
            if (!"定时 Agent 创作验收".equals(scheduledTitle)) {
                throw new AssertionError("定时 Agent 没有通过工具创建文章：" + scheduledTitle);
            }
            Integer toolCalls = jdbcTemplate.queryForObject(
                    "SELECT TOOL_CALL_COUNT FROM TASK_RUN ORDER BY ID DESC LIMIT 1", Integer.class);
            if (toolCalls == null || toolCalls < 1) throw new AssertionError("任务运行没有记录工具调用");
            if (llmRequests.get() != 2) throw new AssertionError("定时 Agent 工具循环应请求模型两次");
            String firstRequest = requestBodies.get(0);
            if (!firstRequest.contains("search_web") || !firstRequest.contains("browse_webpage")
                    || !firstRequest.contains("generate_image") || !firstRequest.contains("save_article_draft")
                    || !firstRequest.contains("每天搜索并浏览人工智能行业动态")) {
                throw new AssertionError("定时 Agent 没有获得完整工具和任务要求：" + firstRequest);
            }
        } finally {
            server.stop(0);
        }
    }

    private String waitForEditorSession(org.springframework.test.web.servlet.MvcResult chat) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000L;
        while (System.currentTimeMillis() < deadline) {
            Matcher matcher = EDITOR_SESSION.matcher(chat.getResponse().getContentAsString());
            if (matcher.find() && chat.getResponse().getContentAsString().contains("event:tool.call")) return matcher.group(1);
            Thread.sleep(25L);
        }
        throw new AssertionError("等待浏览器工具调用超时: " + chat.getResponse().getContentAsString());
    }

    private void waitForSseMarker(org.springframework.test.web.servlet.MvcResult chat, String marker, long timeout) throws Exception {
        long deadline = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < deadline) {
            if (chat.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(marker)) return;
            Thread.sleep(25L);
        }
        throw new AssertionError("等待 SSE 事件超时，缺少 " + marker + ": "
                + chat.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private void respondAsFakeResponsesApi(HttpExchange exchange, int requestNumber,
                                           List<String> requestBodies) throws java.io.IOException {
        requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String events;
        if (requestNumber == 1) {
            String tool = "{\"type\":\"function_call\",\"id\":\"fc-read-1\",\"call_id\":\"call-read-1\",\"name\":\"read_article\",\"arguments\":\"{\\\"reason\\\":\\\"读取浏览器当前文章\\\"}\"}";
            String duplicate = "{\"type\":\"function_call\",\"id\":\"fc-read-duplicate\",\"call_id\":\"call-read-duplicate\",\"name\":\"read_article\",\"arguments\":\"{\\\"reason\\\":\\\"读取浏览器当前文章\\\"}\"}";
            events = "data: {\"type\":\"response.output_item.added\",\"item\":" + tool + "}\n\n"
                    + "data: {\"type\":\"response.output_item.done\",\"item\":" + tool + "}\n\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[" + tool + "," + duplicate + "],\"usage\":{\"input_tokens\":12,\"output_tokens\":4}}}\n\n";
        } else if (requestNumber == 2) {
            String arguments = "{\\\"startLine\\\":1,\\\"endLine\\\":2,\\\"blocks\\\":[\\\"<h2>工具改写标题</h2>\\\",\\\"<p>工具改写内容</p>\\\"],\\\"expectedDocumentVersion\\\":0}";
            String tool = "{\"type\":\"function_call\",\"id\":\"fc-replace-1\",\"call_id\":\"call-replace-1\",\"name\":\"replace_blocks\",\"arguments\":\"" + arguments + "\"}";
            String duplicate = "{\"type\":\"function_call\",\"id\":\"fc-replace-duplicate\",\"call_id\":\"call-replace-duplicate\",\"name\":\"replace_blocks\",\"arguments\":\"" + arguments + "\"}";
            events = "data: {\"type\":\"response.output_item.added\",\"item\":" + tool + "}\n\n"
                    + "data: {\"type\":\"response.output_item.done\",\"item\":" + tool + "}\n\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[" + tool + "," + duplicate + "],\"usage\":{\"input_tokens\":20,\"output_tokens\":8}}}\n\n";
        } else if (requestNumber == 3) {
            events = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"已通过工具完成文章改写。\"}\n\n"
                    + "data: {\"type\":\"response.output_text.done\"}\n\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[],\"usage\":{\"input_tokens\":20,\"output_tokens\":10}}}\n\n";
        } else {
            events = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"Agent4j Session 已恢复。\"}\n\n"
                    + "data: {\"type\":\"response.output_text.done\"}\n\n"
                    + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[],\"usage\":{\"input_tokens\":40,\"output_tokens\":8}}}\n\n";
        }
        byte[] bytes = events.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.sendResponseHeaders(200, 0);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
