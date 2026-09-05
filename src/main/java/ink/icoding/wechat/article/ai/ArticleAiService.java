package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ink.icoding.llm.agent.AgentClient;
import ink.icoding.llm.agent.AgentClientSession;
import ink.icoding.llm.agent.AgentResultHandler;
import ink.icoding.llm.agent.AgentSessionResult;
import ink.icoding.llm.core.entity.MemoryMultipartFile;
import ink.icoding.llm.core.entity.ModelType;
import ink.icoding.llm.core.model.ContextCompressionStatus;
import ink.icoding.llm.core.model.LLMModel;
import ink.icoding.llm.core.model.TokenUsage;
import ink.icoding.llm.core.tool.ToolDescriptor;
import ink.icoding.llm.core.tool.ToolStatus;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.asset.Asset;
import ink.icoding.wechat.article.asset.AssetService;
import ink.icoding.wechat.article.auth.CurrentUser;
import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.settings.LlmConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ArticleAiService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TOOL_CALLS = 24;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 15;
    private static final Set<String> BROWSER_TOOL_NAMES = Set.of(
            "read_article", "read_blocks", "delete_blocks", "insert_blocks", "replace_blocks",
            "update_metadata", "update_cover");
    private static final String ARTICLE_STYLE_GUIDE = """

            【公众号正文视觉模板】
            新创作整篇文章或整篇重写时，正文必须采用下面的版式。局部修改已有文章时保持原有版式，不要为无关段落重排全文。

            版式要求：
            1. 文章标题放在标题字段中，正文不要机械重复主标题。正文依次由引言、导语、若干章节、配图/图注和收束语组成。
            2. 开头用一段简短引言概括全文核心，视觉上使用浅灰文字和绿色左边线；随后用一个自然段承接正文。
            3. 每章使用两位数字01、02、03……作为视觉章节号；章节号居中、绿色，下面有一条绿色短横线，再放居中的章节标题。这里的数字是章节装饰，不是编号列表。
            4. 正文使用简洁自然段，字号16px、行高1.9、深灰色、段间距16px；不要使用ul、ol、dl、table，也不要写成条目清单。
            5. 图片放在相关段落之后，宽度100%、高度自适应；需要说明时在图片下方使用居中的浅灰小字图注。图片必须来自素材工具返回的publicUrl，不得保留占位图片或外链图片。
            6. 全部样式写在style内联属性中，不依赖class、style标签、脚本或外部CSS。绿色统一使用#07C160，正文颜色使用#333333，辅助文字使用#888888。
            7. 章节通常为2至5个，数量由内容决定。最后用一句与主题相关的简短文字居中收束；不要照抄示例文案。

            HTML结构示例（只参考结构与样式，必须根据实际主题替换所有文字、章节数量、图片和链接）：
            <section style="margin:0 0 30px 0;">
              <blockquote style="margin:0;padding:0 0 0 14px;border-left:3px solid #07C160;color:#888888;font-size:15px;line-height:1.8;">“用一句话概括全文的核心内容。”</blockquote>
            </section>
            <p style="margin:0 0 16px 0;color:#333333;font-size:16px;line-height:1.9;text-align:justify;">正文内容从这里开始，用自然段完成导入。</p>
            <section style="margin:42px 0 28px 0;text-align:center;">
              <div style="color:#07C160;font-size:20px;line-height:1.2;">01</div>
              <div style="width:18px;height:2px;margin:7px auto 16px auto;background:#07C160;"></div>
              <h2 style="margin:0;color:#222222;font-size:20px;font-weight:400;line-height:1.6;text-align:center;">章节标题</h2>
            </section>
            <p style="margin:0 0 16px 0;color:#333333;font-size:16px;line-height:1.9;text-align:justify;">本章正文使用连贯的自然段。</p>
            <figure style="margin:24px 0 10px 0;">
              <img src="素材工具返回的publicUrl" alt="与正文有关的准确描述" style="display:block;width:100%;height:auto;margin:0;" />
              <figcaption style="margin-top:8px;color:#999999;font-size:13px;line-height:1.6;text-align:center;">必要时填写简短图注</figcaption>
            </figure>
            <p style="margin:0 0 16px 0;color:#555555;font-size:14px;line-height:1.8;">需要引用时，用自然段写“参考：来源名称”，并为来源名称添加链接。</p>
            <section style="margin:48px 0 20px 0;text-align:center;">
              <div style="color:#999999;font-size:14px;line-height:1.8;">根据文章主题创作一句简短收束语</div>
            </section>
            """;
    private static final String AGENT_DESCRIPTION = """
            微信公众号文章编辑智能体，通过工具直接操作用户浏览器中的富文本编辑器。

            必须遵守：
            1. 只有可能修改或需要讨论当前文章时，才先调用 read_article；用户明确要求仅搜索或回答外部信息且不修改文章时，不要读取文章。
            2. 屏幕换行不算行；工具中的行号表示标题、段落、列表、引用等顶层逻辑内容块。
            3. 每次修改必须携带最近读取或工具结果返回的 documentVersion。版本过期时重新读取，不要盲目重试。
            4. 每次模型响应最多调用一个会改变正文结构的工具，必须等待工具结果后再决定下一步；不要并行调用多个写工具。
            5. 改写已有内容优先使用 replace_blocks；新增使用 insert_blocks；删除使用 delete_blocks。
            6. insert_blocks 和 replace_blocks 的 blocks 每项必须是完整、可独立插入的 HTML 块。
            7. 只修改标题、摘要、正文和封面。不能发布、删除文章、同步微信、操作其他文章或访问系统数据。
            8. 不要输出 ARTICLE_PATCH 或文章 JSON。所有文章修改必须通过文章编辑工具完成。
            9. 只有收到成功的工具结果后才能声称修改完成；工具失败时必须按错误提示重新读取或调整操作。
            10. 每次调用工具前，先用一句简短中文说明接下来准备做什么；工具结果返回后再继续输出或调用下一个工具。不要预先一次性输出所有操作说明。
            11. 全部工具执行完成后，用中文简洁说明实际完成了什么。若用户只是询问而未要求修改，可以读取后直接回答。
            12. 不要创建计划或子智能体；只使用文章编辑工具完成当前请求。
            13. 需要配图时优先检查用户本轮上传的图片和素材库；也可以生成、编辑或搜索并导入网络图片。所有图片必须先成为素材。正文配图使用返回的publicUrl通过insert_blocks插入语义合适的位置；文章封面使用返回的assetId调用update_cover。不要把正文图片集中堆在文末。
            14. 使用网络资料必须先搜索再浏览来源页；网络图片必须通过 import_web_image 保存来源，不能直接把外链图片插入文章。
            15. 创作新文章或整篇改写时严格采用下方“公众号正文视觉模板”；局部编辑时延续文章现有样式。禁止使用项目符号、编号列表、定义列表或表格，需要表达多项内容时写成连贯段落。
            """ + ARTICLE_STYLE_GUIDE;
    private static final String SCHEDULED_AGENT_DESCRIPTION = """
            微信公众号定时文章创作智能体。每次执行都从当前任务要求出发，自主研究并完成一篇新文章。

            必须遵守：
            1. 这是文章创作任务，不是固定网页采集器。根据任务要求自主决定搜索词、来源和文章结构。
            2. 涉及时效性或外部事实时，先使用search_web搜索，再用browse_webpage阅读重要来源；不得把搜索摘要当成完整事实依据。
            3. 可以使用素材库、网络图片导入、图片生成和图片编辑工具。正文图片使用工具返回的publicUrl，封面通过set_article_draft_cover设置。
            4. 网络图片必须先通过import_web_image进入素材库，禁止在正文中直接引用外链图片。
            5. 正文必须严格采用下方“公众号正文视觉模板”，生成适合微信公众号移动端阅读、带内联样式的HTML；禁止使用项目符号、编号列表、定义列表、表格。事实、数据和引语必须准确，主要来源在文末用自然段说明。
            6. 完成研究和写作后必须调用save_article_draft提交完整文章；未调用该工具就不算完成任务。
            7. 工具成功后再陈述结果。不要创建计划或子智能体，不要尝试自行发布；草稿、微信草稿或发布动作由任务系统统一执行。
            """ + ARTICLE_STYLE_GUIDE;
    private final AiMessageMapper messageMapper;
    private final ArticleAgentSessionMapper agentSessionMapper;
    private final ArticleService articleService;
    private final CurrentUserService currentUserService;
    private final LlmConfigService llmConfigService;
    private final ArticleMediaTools mediaTools;
    private final AssetService assetService;
    private final Map<String, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Object> articleSessionLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "article-ai-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ArticleAiService(AiMessageMapper messageMapper, ArticleAgentSessionMapper agentSessionMapper,
                            ArticleService articleService,
                            CurrentUserService currentUserService, LlmConfigService llmConfigService,
                            ArticleMediaTools mediaTools, AssetService assetService) {
        this.messageMapper = messageMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.articleService = articleService;
        this.currentUserService = currentUserService;
        this.llmConfigService = llmConfigService;
        this.mediaTools = mediaTools;
        this.assetService = assetService;
    }

    public List<AiMessage> messages(Long articleId) {
        articleService.required(articleId);
        return messageMapper.findByArticleId(articleId);
    }

    public SseEmitter chat(Long articleId, String instruction, List<Long> assetIds) {
        if (instruction == null || instruction.isBlank()) throw new BusinessException("请输入编辑要求");
        Article article = articleService.required(articleId);
        CurrentUser user = currentUserService.required();
        List<Asset> attachedAssets = resolveAttachedAssets(article, assetIds);
        save(articleId, "USER", instruction, "COMPLETED", 0, 0, user.id());

        SseEmitter emitter = new SseEmitter(0L);
        EditorSession session = new EditorSession(UUID.randomUUID().toString(), article, user, emitter, attachedAssets);
        sessions.put(session.id, session);
        emitter.onTimeout(() -> session.close("编辑会话已超时"));
        emitter.onError(error -> session.close("编辑会话连接已断开"));
        emitter.onCompletion(() -> session.close("编辑会话已结束"));
        session.startHeartbeat();
        CompletableFuture.runAsync(() -> execute(session, instruction));
        return emitter;
    }

    @PreDestroy
    public void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    private List<Asset> resolveAttachedAssets(Article article, List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) return List.of();
        List<Long> uniqueIds = new ArrayList<>(new HashSet<>(assetIds));
        if (uniqueIds.size() > 4) throw new BusinessException("每轮对话最多上传 4 张图片");
        List<Asset> assets = new ArrayList<>();
        for (Long id : uniqueIds) {
            Asset asset = assetService.required(id);
            if (asset.getAccountId() != null && article.getAccountId() != null
                    && !asset.getAccountId().equals(article.getAccountId())) {
                throw new BusinessException("图片素材不属于当前公众号");
            }
            assets.add(asset);
        }
        return assets;
    }

    public void completeTool(Long articleId, String sessionId, String callId, ToolResultRequest request) {
        EditorSession session = sessions.get(sessionId);
        CurrentUser user = currentUserService.required();
        if (session == null || !session.article.getId().equals(articleId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "AI 编辑会话不存在或已结束");
        }
        if (!session.user.id().equals(user.id())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "不能操作其他用户的 AI 编辑会话");
        }
        session.completeTool(callId, request);
    }

    private void execute(EditorSession session, String instruction) {
        Object lock = articleSessionLocks.computeIfAbsent(session.article.getId(), ignored -> new Object());
        synchronized (lock) {
            executeWithAgentSession(session, instruction);
        }
    }

    private void executeWithAgentSession(EditorSession session, String instruction) {
        LlmConfigService.RuntimeConfig config = llmConfigService.runtime();
        if (!config.available()) {
            session.send("error", Map.of("message", "LLM 尚未在系统设置中启用或未配置 API Key"));
            finishSession(session);
            return;
        }

        AtomicInteger inputTokens = new AtomicInteger();
        AtomicInteger outputTokens = new AtomicInteger();
        StringBuilder assistantText = new StringBuilder();
        try {
            session.send("state", Map.of(
                    "sessionId", session.id,
                    "status", "thinking",
                    "message", "智能体正在分析要求并准备读取编辑区…"));
            AgentClient agent = createArticleAgent(config, session);
            ArticleAgentSession storedSession = agentSessionMapper.findByArticleId(session.article.getId());
            AgentClientSession agentSession = storedSession == null
                    ? agent.createSession()
                    : agent.getSessionFromSerialization(storedSession.getSerializedSession());
            AgentSessionResult result = agentSession.command(commandWithAttachments(instruction, session.attachedAssets),
                            sessionAttachments(session.attachedAssets))
                    .then(new AgentResultHandler() {
                        @Override
                        public void onMessage(String message) {
                            assistantText.append(message);
                            session.send("delta", Map.of("content", message));
                        }

                        @Override
                        public void onUsage(TokenUsage usage) {
                            if (usage != null) {
                                inputTokens.addAndGet(usage.getInputTokens());
                                outputTokens.addAndGet(usage.getOutputTokens());
                            }
                        }

                        @Override
                        public void onTool(ToolDescriptor tool, ToolStatus status) {
                            if (tool != null && !BROWSER_TOOL_NAMES.contains(tool.getName())) {
                                session.serverToolStatus(tool, status);
                            }
                        }

                        @Override
                        public void onToolError(ToolDescriptor tool, Exception error) {
                            if (tool != null && !BROWSER_TOOL_NAMES.contains(tool.getName())) {
                                session.serverToolError(tool, error);
                            }
                        }

                        @Override
                        public void onContextCompression(ContextCompressionStatus status,
                                                         int beforeTokens, int afterTokens) {
                            session.send("state", Map.of(
                                    "sessionId", session.id,
                                    "status", "compressing_context",
                                    "message", status == ContextCompressionStatus.STARTED
                                            ? "正在压缩较早的对话上下文…" : "对话上下文压缩完成"));
                        }
                    });
            result.execute();
            String response = result.get();
            String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
            if (reply.isBlank()) reply = "文章编辑已完成。";

            Article updated = commitSession(session);
            persistAgentSession(storedSession, agentSession, session.article.getId(), session.user.id());
            save(session.article.getId(), "ASSISTANT", reply, "COMPLETED",
                    inputTokens.get(), outputTokens.get(), session.user.id());
            Map<String, Object> completed = new LinkedHashMap<>();
            completed.put("message", reply);
            completed.put("toolCalls", session.toolCalls.get());
            if (session.modified) completed.put("article", updated);
            session.send("completed", completed);
        } catch (Exception exception) {
            String message = readableLlmError(exception);
            save(session.article.getId(), "ASSISTANT", "处理失败：" + message, "FAILED",
                    inputTokens.get(), outputTokens.get(), session.user.id());
            session.send("error", Map.of("message", message));
        } finally {
            finishSession(session);
        }
    }

    private AgentClient createArticleAgent(LlmConfigService.RuntimeConfig config, EditorSession editorSession) {
        AgentClient agent = new AgentClient();
        agent.setName("墨舟微信公众号文章编辑智能体");
        agent.setDescription(AGENT_DESCRIPTION);
        agent.setModel(createModel(config));
        List<ink.icoding.llm.core.tool.Tool> tools = new ArrayList<>(ArticleEditorTools.all((toolName, paramJson) ->
                editorSession.requestTool(toolName, paramJson, null)));
        tools.addAll(mediaTools.create(editorSession.article.getAccountId(), editorSession.user.id(),
                editorSession.mediaMutations::execute));
        agent.setTools(tools);
        return agent;
    }

    private String commandWithAttachments(String instruction, List<Asset> assets) {
        if (assets.isEmpty()) return instruction;
        StringBuilder command = new StringBuilder(instruction).append("\n\n本轮用户提供了以下图片素材，图片内容也附在本消息中：\n");
        for (Asset asset : assets) {
            command.append("- assetId=").append(asset.getId())
                    .append(", publicUrl=").append(asset.getPublicUrl())
                    .append(", filename=").append(asset.getOriginalName()).append('\n');
        }
        command.append("需要使用时可直接把对应 publicUrl 通过 insert_blocks 插入文章；如需修改图片，调用 edit_image。\n");
        return command.toString();
    }

    private List<MemoryMultipartFile> sessionAttachments(List<Asset> assets) {
        return assets.stream().map(asset -> new MemoryMultipartFile(
                assetService.readBytes(asset.getId()), asset.getContentType(), asset.getOriginalName())).toList();
    }

    private void persistAgentSession(ArticleAgentSession storedSession,
                                     AgentClientSession agentSession, Long articleId, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        if (storedSession == null) {
            storedSession = new ArticleAgentSession();
            storedSession.setArticleId(articleId);
            storedSession.setCreatedAt(now);
            storedSession.setSerializedSession(agentSession.serialization());
            storedSession.setUpdatedBy(userId);
            storedSession.setUpdatedAt(now);
            agentSessionMapper.insert(storedSession);
            return;
        }
        storedSession.setSerializedSession(agentSession.serialization());
        storedSession.setUpdatedBy(userId);
        storedSession.setUpdatedAt(now);
        agentSessionMapper.updateById(storedSession);
    }

    private Article commitSession(EditorSession session) {
        EditorDocument document = session.latestDocument;
        if (!session.modified || document == null) return articleService.required(session.article.getId());
        Article original = session.article;
        Long coverAssetId = document.coverAssetId();
        String coverUrl = document.coverUrl();
        if (coverAssetId != null) {
            Asset cover = assetService.required(coverAssetId);
            if (original.getAccountId() != null && cover.getAccountId() != null
                    && !original.getAccountId().equals(cover.getAccountId())) {
                throw new BusinessException("封面素材不属于当前公众号");
            }
            coverUrl = cover.getPublicUrl();
        }
        ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(
                original.getAccountId(), document.title(), original.getAuthor(), document.digest(),
                document.contentHtml(), coverAssetId, coverUrl,
                original.getSourceUrl(), original.getRevision());
        return articleService.updateByAi(original.getId(), request,
                "AI 工具编辑（" + session.toolCalls.get() + " 次工具调用）", session.user.id());
    }

    public ScheduledAgentResult runScheduledAgent(ScheduledAgentRequest request) throws Exception {
        LlmConfigService.RuntimeConfig config = llmConfigService.runtime();
        if (!config.available()) throw new BusinessException("LLM 尚未在系统设置中启用或未配置 API Key");

        ScheduledArticleTools.DraftState draftState = new ScheduledArticleTools.DraftState(request.defaultCoverAssetId());
        ToolMutationDeduplicator mediaMutations = new ToolMutationDeduplicator();
        AgentClient agent = new AgentClient();
        agent.setName("墨舟定时文章创作智能体");
        agent.setDescription(SCHEDULED_AGENT_DESCRIPTION);
        agent.setModel(createModel(config));
        List<ink.icoding.llm.core.tool.Tool> tools = new ArrayList<>(ScheduledArticleTools.all(draftState));
        tools.addAll(mediaTools.create(request.accountId(), request.userId(), mediaMutations::execute));
        agent.setTools(tools);

        AtomicInteger toolCalls = new AtomicInteger();
        Set<String> countedCalls = ConcurrentHashMap.newKeySet();
        List<String> executionLog = java.util.Collections.synchronizedList(new ArrayList<>());
        StringBuilder assistantText = new StringBuilder();
        String deliveryRequirement = "LOCAL_DRAFT".equals(request.outputMode())
                ? "保存为本地草稿；封面可按内容需要设置"
                : "将由系统同步或发布到微信；必须在提交文章前选择、导入或生成合适图片，并调用set_article_draft_cover设置封面";
        String command = """
                当前时间：%s
                目标公众号：%s
                任务完成后的系统动作：%s
                交付约束：%s

                本次创作要求：
                %s
                """.formatted(
                ZonedDateTime.now(ZoneId.of(request.timezone())),
                request.accountId() == null ? "未指定，仅创建本地文章" : "公众号ID " + request.accountId(),
                request.outputMode(), deliveryRequirement, request.instruction());

        AgentSessionResult result = agent.createSession().command(command).then(new AgentResultHandler() {
            @Override
            public void onMessage(String message) {
                if (message != null) assistantText.append(message);
            }

            @Override
            public void onTool(ToolDescriptor tool, ToolStatus status) {
                if (tool == null || status == ToolStatus.PREPARING) return;
                String key = tool.getName() + "\n" + safeCallId(tool);
                if (status == ToolStatus.CALLING && countedCalls.add(key)) {
                    toolCalls.incrementAndGet();
                    executionLog.add("调用工具：" + tool.getName());
                } else if (status == ToolStatus.COMPLETED) {
                    executionLog.add("工具完成：" + tool.getName());
                }
            }

            @Override
            public void onToolError(ToolDescriptor tool, Exception error) {
                executionLog.add("工具失败：" + (tool == null ? "unknown" : tool.getName()) + " - "
                        + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            }
        });
        result.execute();
        String response = result.get();
        ScheduledArticleTools.Draft draft = draftState.snapshot();
        if (draft.coverAssetId() != null) {
            Asset cover = assetService.required(draft.coverAssetId());
            if (request.accountId() != null && cover.getAccountId() != null
                    && !request.accountId().equals(cover.getAccountId())) {
                throw new BusinessException("智能体选择的封面素材不属于任务目标公众号");
            }
        }
        String reply = response == null || response.isBlank() ? assistantText.toString().trim() : response.trim();
        if (reply.isBlank()) reply = "定时文章创作已完成";
        return new ScheduledAgentResult(draft, reply, toolCalls.get(), String.join("\n", executionLog));
    }

    private LLMModel createModel(LlmConfigService.RuntimeConfig config) {
        ModelType modelType = switch (config.provider()) {
            case "ANTHROPIC" -> ModelType.Anthropic;
            case "OPENAI_RESPONSES" -> ModelType.OpenAIResponse;
            case "OPENAI_COMPATIBLE" -> ModelType.OpenAI;
            default -> throw new BusinessException("不支持的 LLM 服务类型：" + config.provider());
        };
        return LLMModel.create(modelType, config.baseUrl(), config.modelName(), config.apiKey());
    }

    private String readableLlmError(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return "浏览器编辑工具等待超时，请保持文章编辑页面打开后重试。";
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) return "AI 处理失败";
        if (message.contains("HTTP 401")) return "LLM 服务鉴权失败（HTTP 401），请检查 API Key 是否正确。";
        if (message.contains("HTTP 403")) {
            return "LLM 服务拒绝访问（HTTP 403）。请确认 API Key 属于当前 Base URL、未过期或禁用，并检查账号权限与 IP 白名单；上游未返回可读的错误详情。";
        }
        if (message.contains("HTTP 404")) return "LLM 接口不存在（HTTP 404），请检查 Base URL 和所选协议。";
        if (message.contains("HTTP 429")) return "LLM 请求过于频繁或额度不足（HTTP 429），请稍后重试或检查账户余额。";
        return message;
    }

    private void save(Long articleId, String role, String content, String status, int input, int output, Long userId) {
        AiMessage message = new AiMessage();
        message.setArticleId(articleId);
        message.setRole(role);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        message.setPromptTokens(input);
        message.setCompletionTokens(output);
        message.setCreatedBy(userId);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private void finishSession(EditorSession session) {
        sessions.remove(session.id, session);
        session.close("编辑会话已结束");
        try {
            session.emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private static String safeCallId(ToolDescriptor descriptor) {
        return descriptor == null || descriptor.getCallId() == null ? "" : descriptor.getCallId();
    }

    private final class EditorSession {
        private final String id;
        private final Article article;
        private final CurrentUser user;
        private final SseEmitter emitter;
        private final List<Asset> attachedAssets;
        private final Map<String, CompletableFuture<ToolResultRequest>> pending = new ConcurrentHashMap<>();
        private final Map<String, String> completedMutations = new ConcurrentHashMap<>();
        private final Map<String, String> currentReadResults = new ConcurrentHashMap<>();
        private final Map<String, String> completedReadInvocations = new ConcurrentHashMap<>();
        private final AtomicInteger toolCalls = new AtomicInteger();
        private final AtomicInteger documentEpoch = new AtomicInteger();
        private final AtomicInteger serverToolSequence = new AtomicInteger();
        private final Map<String, String> serverToolIds = new ConcurrentHashMap<>();
        private final Set<String> completedServerToolKeys = ConcurrentHashMap.newKeySet();
        private final ToolMutationDeduplicator mediaMutations = new ToolMutationDeduplicator();
        private volatile ScheduledFuture<?> heartbeat;
        private volatile EditorDocument latestDocument;
        private volatile boolean modified;
        private volatile boolean closed;

        private EditorSession(String id, Article article, CurrentUser user, SseEmitter emitter,
                              List<Asset> attachedAssets) {
            this.id = id;
            this.article = article;
            this.user = user;
            this.emitter = emitter;
            this.attachedAssets = List.copyOf(attachedAssets);
        }

        private void serverToolStatus(ToolDescriptor tool, ToolStatus status) {
            if (status == ToolStatus.PREPARING) return;
            String key = tool.getName() + "\n" + safeCallId(tool);
            boolean stableKey = !safeCallId(tool).isBlank();
            if (stableKey && completedServerToolKeys.contains(key)) return;
            if (status == ToolStatus.CALLING) {
                String callId = serverToolIds.computeIfAbsent(key,
                        ignored -> "server-tool-" + serverToolSequence.incrementAndGet());
                send("tool.call", Map.of("sessionId", id, "callId", callId,
                        "modelCallId", safeCallId(tool), "name", tool.getName(),
                        "arguments", Map.of()));
            } else if (status == ToolStatus.COMPLETED) {
                String callId = serverToolIds.remove(key);
                if (callId != null) {
                    if (stableKey) completedServerToolKeys.add(key);
                    send("tool.result", Map.of("callId", callId,
                            "name", tool.getName(), "status", "COMPLETED", "message", "工具执行完成"));
                }
            }
        }

        private void serverToolError(ToolDescriptor tool, Exception error) {
            String key = tool.getName() + "\n" + safeCallId(tool);
            boolean stableKey = !safeCallId(tool).isBlank();
            if (stableKey && !completedServerToolKeys.add(key)) return;
            String callId = serverToolIds.remove(key);
            if (callId == null) callId = "server-tool-" + serverToolSequence.incrementAndGet();
            send("tool.result", Map.of("callId", callId, "name", tool.getName(), "status", "FAILED",
                    "message", error.getMessage() == null ? "工具执行失败" : error.getMessage()));
        }

        private String requestTool(String toolName, String paramJson, ToolDescriptor descriptor) throws Exception {
            if (closed) throw new IllegalStateException("浏览器编辑会话已关闭");
            String modelCallId = safeCallId(descriptor);
            JsonNode arguments = MAPPER.readTree(paramJson == null || paramJson.isBlank() ? "{}" : paramJson);
            String signature = toolName + "\n" + MAPPER.writeValueAsString(arguments);
            if (isMutation(toolName)) {
                String cached = completedMutations.get(signature);
                if (cached != null) return cached;
            } else {
                String invocationKey = documentEpoch.get() + "\n" + modelCallId + "\n" + signature;
                String cached = completedReadInvocations.get(invocationKey);
                if (cached == null) cached = currentReadResults.get(signature);
                if (cached != null) return cached;
            }

            int toolNumber = toolCalls.incrementAndGet();
            if (toolNumber > MAX_TOOL_CALLS) {
                throw new IllegalStateException("单次编辑最多调用 " + MAX_TOOL_CALLS + " 次工具");
            }
            String callId = "tool-" + toolNumber;
            CompletableFuture<ToolResultRequest> future = new CompletableFuture<>();
            if (pending.putIfAbsent(callId, future) != null) throw new IllegalStateException("工具调用ID重复");

            Map<String, Object> call = new LinkedHashMap<>();
            call.put("sessionId", id);
            call.put("callId", callId);
            call.put("modelCallId", modelCallId);
            call.put("name", toolName);
            call.put("arguments", MAPPER.convertValue(arguments, Object.class));
            send("tool.call", call);

            if (isStreamingMutation(toolName)) streamBlocks(callId, toolName, arguments);
            try {
                ToolResultRequest result = future.get(90, TimeUnit.SECONDS);
                if (result == null || !Boolean.TRUE.equals(result.success())) {
                    throw new IllegalStateException(result == null || result.result() == null
                            ? "浏览器未能执行工具" : result.result());
                }
                if (isMutation(toolName) && result.document() == null) {
                    throw new IllegalStateException("浏览器没有返回修改后的文章内容");
                }
                if (result.document() != null && !article.getRevision().equals(result.document().articleRevision())) {
                    throw new IllegalStateException("文章数据库版本已变化，请结束本轮编辑并刷新文章");
                }
                if (result.document() != null) latestDocument = result.document();
                if (isMutation(toolName)) modified = true;
                String toolResult = result.result() == null || result.result().isBlank()
                        ? MAPPER.writeValueAsString(result.document()) : result.result();
                toolResult = compactReadResult(toolName, toolResult);
                if (isMutation(toolName)) {
                    completedMutations.put(signature, toolResult);
                    documentEpoch.incrementAndGet();
                    currentReadResults.clear();
                    completedReadInvocations.clear();
                } else {
                    currentReadResults.put(signature, toolResult);
                    completedReadInvocations.put(documentEpoch.get() + "\n" + modelCallId + "\n" + signature,
                            toolResult);
                }
                send("tool.result", Map.of("callId", callId, "name", toolName, "status", "COMPLETED",
                        "message", "工具执行完成"));
                return toolResult;
            } catch (Exception error) {
                send("tool.result", Map.of("callId", callId, "name", toolName, "status", "FAILED",
                        "message", error.getMessage() == null ? "工具执行失败" : error.getMessage()));
                throw error;
            } finally {
                pending.remove(callId);
            }
        }

        private String compactReadResult(String toolName, String value) {
            if (!("read_article".equals(toolName) || "read_blocks".equals(toolName))) return value;
            try {
                JsonNode root = MAPPER.readTree(value);
                if (root != null && root.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) root).remove("contentHtml");
                    JsonNode blocks = root.get("blocks");
                    if (blocks != null && blocks.isArray()) {
                        blocks.forEach(block -> {
                            if (block.isObject()) {
                                ((com.fasterxml.jackson.databind.node.ObjectNode) block).remove("text");
                            }
                        });
                    }
                    return MAPPER.writeValueAsString(root);
                }
            } catch (Exception ignored) {
            }
            return value;
        }

        private void streamBlocks(String callId, String toolName, JsonNode arguments) throws InterruptedException {
            send("editor.insert.start", Map.of("callId", callId, "name", toolName));
            JsonNode blocks = arguments.get("blocks");
            int count = blocks != null && blocks.isArray() ? blocks.size() : 0;
            for (int index = 0; index < count; index++) {
                send("editor.insert.delta", Map.of(
                        "callId", callId, "index", index, "total", count,
                        "contentHtml", blocks.get(index).asText()));
                if (index + 1 < count) Thread.sleep(90L);
            }
            send("editor.insert.completed", Map.of("callId", callId, "total", count));
        }

        private void completeTool(String callId, ToolResultRequest result) {
            CompletableFuture<ToolResultRequest> future = pending.get(callId);
            if (future == null) throw new BusinessException(HttpStatus.NOT_FOUND, "工具调用不存在或已经完成");
            if (!future.complete(result)) throw new BusinessException("工具结果已经提交");
        }

        private void startHeartbeat() {
            heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                    () -> send("heartbeat", Map.of("sessionId", id, "timestamp", System.currentTimeMillis())),
                    HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        private void send(String event, Object data) {
            if (closed) return;
            try {
                synchronized (emitter) {
                    emitter.send(SseEmitter.event().name(event).data(data));
                }
            } catch (Exception error) {
                close("浏览器编辑会话连接已断开");
            }
        }

        private void close(String reason) {
            if (closed) return;
            closed = true;
            ScheduledFuture<?> heartbeatTask = heartbeat;
            if (heartbeatTask != null) heartbeatTask.cancel(false);
            pending.values().forEach(future -> future.completeExceptionally(new IllegalStateException(reason)));
            pending.clear();
        }
    }

    private static boolean isStreamingMutation(String toolName) {
        return "insert_blocks".equals(toolName) || "replace_blocks".equals(toolName);
    }

    private static boolean isMutation(String toolName) {
        return isStreamingMutation(toolName) || "delete_blocks".equals(toolName)
                || "update_metadata".equals(toolName) || "update_cover".equals(toolName);
    }

    public record EditorBlock(Integer line, String type, String text, String html) {
    }

    public record EditorDocument(String title, String digest, String contentHtml, Long coverAssetId,
                                 String coverUrl, Long documentVersion, Integer articleRevision,
                                 List<EditorBlock> blocks) {
    }

    public record ToolResultRequest(Boolean success, String result, EditorDocument document) {
    }

    public record Patch(String title, String digest, String contentHtml) {
    }

    public record ScheduledAgentRequest(Long accountId, Long userId, Long defaultCoverAssetId,
                                        String timezone, String outputMode, String instruction) {
    }

    public record ScheduledAgentResult(ScheduledArticleTools.Draft draft, String message,
                                       int toolCalls, String executionLog) {
    }
}
