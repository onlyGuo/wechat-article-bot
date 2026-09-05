package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.ai.ArticleAiService;
import ink.icoding.wechat.article.ai.ScheduledArticleTools;
import ink.icoding.wechat.article.article.Article;
import ink.icoding.wechat.article.article.ArticleService;
import ink.icoding.wechat.article.common.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskExecutionService {
    private final ScheduleTaskMapper mapper;
    private final TaskRunMapper runMapper;
    private final ArticleAiService aiService;
    private final ArticleService articleService;
    private final Map<Long, Object> taskLocks = new ConcurrentHashMap<>();

    public TaskExecutionService(ScheduleTaskMapper mapper, TaskRunMapper runMapper,
                                ArticleAiService aiService, ArticleService articleService) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.aiService = aiService;
        this.articleService = articleService;
    }

    /** Starts a manual execution without keeping the HTTP request open for the entire agent run. */
    public TaskRun start(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(taskId, triggerType);
        CompletableFuture.runAsync(() -> executeRun(task, run));
        return run;
    }

    /** Executes synchronously for Quartz, so DisallowConcurrentExecution remains effective. */
    public TaskRun execute(Long taskId, String triggerType) {
        ScheduleTask task = requiredTask(taskId);
        TaskRun run = createRun(taskId, triggerType);
        return executeRun(task, run);
    }

    private ScheduleTask requiredTask(Long taskId) {
        ScheduleTask task = mapper.findById(taskId);
        if (task == null) throw new BusinessException("定时任务不存在");
        return task;
    }

    private TaskRun createRun(Long taskId, String triggerType) {
        synchronized (taskLocks.computeIfAbsent(taskId, ignored -> new Object())) {
            if (runMapper.findRunning(taskId) != null) throw new BusinessException("任务正在执行，请勿重复启动");
            TaskRun run = new TaskRun();
            run.setTaskId(taskId);
            run.setTriggerType(triggerType);
            run.setStatus("RUNNING");
            run.setFetchedCount(0);
            run.setGeneratedCount(0);
            run.setToolCallCount(0);
            run.setStartedAt(LocalDateTime.now());
            runMapper.insert(run);
            return run;
        }
    }

    private TaskRun executeRun(ScheduleTask task, TaskRun run) {
        try {
            ArticleAiService.ScheduledAgentResult result = aiService.runScheduledAgent(
                    new ArticleAiService.ScheduledAgentRequest(task.getAccountId(), task.getCreatedBy(),
                            task.getCoverAssetId(), task.getTimezone(), task.getOutputMode(), task.getAiPrompt()));
            ScheduledArticleTools.Draft draft = result.draft();
            ArticleService.ArticleRequest request = new ArticleService.ArticleRequest(task.getAccountId(),
                    draft.title(), draft.author(), draft.digest(), draft.contentHtml(),
                    draft.coverAssetId(), null, draft.sourceUrl(), null);
            Article article = articleService.createForTask(request, task.getCreatedBy());
            run.setArticleId(article.getId());
            run.setGeneratedCount(1);
            run.setToolCallCount(result.toolCalls());
            run.setExecutionLog(result.executionLog());

            if ("WECHAT_DRAFT".equals(task.getOutputMode())) articleService.syncDraft(article.getId());
            if ("AUTO_PUBLISH".equals(task.getOutputMode())) articleService.publish(article.getId());

            run.setStatus("SUCCESS");
            run.setMessage(trimMessage(result.message()));
        } catch (Exception error) {
            run.setStatus("FAILED");
            run.setMessage(trimMessage(error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage()));
        } finally {
            runMapper.finishRun(run);
            mapper.touchRun(task.getId());
        }
        return runMapper.selectById(run.getId());
    }

    private String trimMessage(String value) {
        if (value == null || value.isBlank()) return "任务执行结束";
        return value.length() > 60_000 ? value.substring(0, 60_000) : value;
    }
}
