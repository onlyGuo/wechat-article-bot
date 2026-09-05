package ink.icoding.wechat.article.schedule;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskRunMapper extends SmartMapper<TaskRun> {
    default int finishRun(TaskRun changes) {
        TaskRun run = selectById(changes.getId());
        if (run == null) return 0;
        run.setStatus(changes.getStatus());
        run.setFetchedCount(changes.getFetchedCount());
        run.setGeneratedCount(changes.getGeneratedCount());
        run.setArticleId(changes.getArticleId());
        run.setToolCallCount(changes.getToolCallCount());
        run.setMessage(changes.getMessage());
        run.setExecutionLog(changes.getExecutionLog());
        run.setFinishedAt(LocalDateTime.now());
        return updateById(run);
    }

    default List<TaskRun> findRuns(Long taskId) {
        return select(Where.where(TaskRun::getTaskId).eq(taskId)
                .orderBy(TaskRun::getStartedAt).desc().limit(100));
    }

    default List<TaskRun> findRecentRuns(int limit) {
        return select(Where.where().orderBy(TaskRun::getStartedAt).desc().limit(Math.max(1, limit)));
    }

    default TaskRun findRunning(Long taskId) {
        List<TaskRun> runs = select(Where.where(TaskRun::getTaskId).eq(taskId)
                .and(TaskRun::getStatus).eq("RUNNING")
                .orderBy(TaskRun::getStartedAt).desc().limit(1));
        return runs.isEmpty() ? null : runs.get(0);
    }
}
