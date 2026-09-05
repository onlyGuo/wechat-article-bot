package ink.icoding.wechat.article.article;

import ink.icoding.wechat.article.common.ApiResponse;
import ink.icoding.wechat.article.common.BusinessException;
import ink.icoding.wechat.article.common.PageResult;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);
    private final ArticleService service;
    private final ExecutorService wechatExecutor = Executors.newFixedThreadPool(4, task -> {
        Thread thread = new Thread(task, "article-wechat-sse");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "article-wechat-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ArticleController(ArticleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResult<Article>> list(@RequestParam(required = false) Long accountId,
            @RequestParam(defaultValue = "DRAFT") String status, @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.list(accountId, status, keyword, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Article> get(@PathVariable Long id) {
        return ApiResponse.ok(service.required(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Article> create(@RequestBody ArticleService.ArticleRequest request) {
        return ApiResponse.ok(service.create(request, "MANUAL"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Article> update(@PathVariable Long id, @RequestBody ArticleService.ArticleRequest request) {
        return ApiResponse.ok(service.update(id, request, "MANUAL", "手动编辑"));
    }

    @GetMapping("/{id}/revisions")
    public ApiResponse<List<ArticleRevision>> revisions(@PathVariable Long id) {
        return ApiResponse.ok(service.revisions(id));
    }

    @PostMapping("/{id}/revisions/{revision}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','EDITOR')")
    public ApiResponse<Article> rollback(@PathVariable Long id, @PathVariable Integer revision) {
        return ApiResponse.ok(service.rollback(id, revision));
    }

    @PostMapping(value = "/{id}/wechat-draft", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','REVIEWER')")
    public SseEmitter syncDraft(@PathVariable Long id, HttpServletResponse response) {
        return wechatStream(id, false, response);
    }

    @PostMapping(value = "/{id}/publish", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','REVIEWER')")
    public SseEmitter publish(@PathVariable Long id, HttpServletResponse response) {
        return wechatStream(id, true, response);
    }

    @PostMapping("/{id}/publish/status")
    public ApiResponse<ArticleService.PublishStatus> publishStatus(@PathVariable Long id) {
        return ApiResponse.ok(service.refreshPublishStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PreDestroy
    public void shutdownWechatExecutors() {
        wechatExecutor.shutdownNow();
        heartbeatExecutor.shutdownNow();
    }

    private SseEmitter wechatStream(Long id, boolean publish, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean connected = new AtomicBoolean(true);
        emitter.onCompletion(() -> connected.set(false));
        emitter.onTimeout(() -> connected.set(false));
        emitter.onError(error -> connected.set(false));
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(
                () -> emit(emitter, connected, "heartbeat", Map.of("timestamp", System.currentTimeMillis())),
                10, 10, TimeUnit.SECONDS);

        wechatExecutor.execute(() -> {
            try {
                Article article = publish
                        ? service.publish(id, progress -> emit(emitter, connected, "progress", progress))
                        : service.syncDraft(id, progress -> emit(emitter, connected, "progress", progress));
                String message = publish ? "发布任务已提交给微信" : "文章已同步到微信草稿箱";
                emit(emitter, connected, "completed", Map.of("message", message, "article", article));
            } catch (Exception exception) {
                String fallback = publish ? "提交微信发布失败" : "同步微信草稿失败";
                String message = exception.getMessage() == null || exception.getMessage().isBlank()
                        ? fallback : exception.getMessage();
                if (exception instanceof BusinessException) {
                    log.info("{}: articleId={}, message={}", fallback, id, message);
                } else {
                    log.warn("{}: articleId={}", fallback, id, exception);
                }
                emit(emitter, connected, "error", Map.of("message", message));
            } finally {
                heartbeat.cancel(false);
                if (connected.get()) emitter.complete();
            }
        });
        return emitter;
    }

    private void emit(SseEmitter emitter, AtomicBoolean connected, String event, Object data) {
        if (!connected.get()) return;
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event().name(event).data(data));
            }
        } catch (Exception exception) {
            connected.set(false);
        }
    }
}
