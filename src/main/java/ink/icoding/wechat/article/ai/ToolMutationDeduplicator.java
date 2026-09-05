package ink.icoding.wechat.article.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Prevents the same side-effecting server tool call from being executed more than once in one agent turn.
 */
public final class ToolMutationDeduplicator {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ConcurrentHashMap<String, CompletableFuture<String>> executions = new ConcurrentHashMap<>();

    public String execute(String toolName, String paramJson, Supplier<String> action) {
        String key = toolName + "\n" + normalizeJson(paramJson);
        CompletableFuture<String> created = new CompletableFuture<>();
        CompletableFuture<String> existing = executions.putIfAbsent(key, created);
        if (existing != null) return await(existing);

        try {
            String result = action.get();
            created.complete(result);
            return result;
        } catch (Throwable error) {
            created.completeExceptionally(error);
            executions.remove(key, created);
            throw propagate(error);
        }
    }

    private static String normalizeJson(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            JsonNode json = MAPPER.readTree(value);
            return MAPPER.writeValueAsString(json);
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private static String await(CompletableFuture<String> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            throw propagate(error.getCause() == null ? error : error.getCause());
        }
    }

    private static RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtimeException) return runtimeException;
        if (error instanceof Error fatal) throw fatal;
        return new IllegalStateException(error);
    }
}
