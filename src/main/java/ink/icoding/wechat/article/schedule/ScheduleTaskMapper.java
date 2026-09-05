package ink.icoding.wechat.article.schedule;

import ink.icoding.smartmybatis.entity.expression.Where;
import ink.icoding.smartmybatis.mapper.base.SmartMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ScheduleTaskMapper extends SmartMapper<ScheduleTask> {
    default List<ScheduleTask> findAll() {
        return selectWithRelations(Where.where().orderBy(ScheduleTask::getCreatedAt).desc());
    }

    default ScheduleTask findById(Long id) {
        List<ScheduleTask> tasks = selectWithRelations(Where.where(ScheduleTask::getId).eq(id).limit(1));
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    default List<ScheduleTask> findEnabled() {
        return select(Where.where(ScheduleTask::getEnabled).eq(true));
    }

    default long countEnabled() {
        return count(Where.where(ScheduleTask::getEnabled).eq(true));
    }

    default int update(ScheduleTask task) {
        task.setUpdatedAt(LocalDateTime.now());
        return updateById(task);
    }

    default int touchRun(Long id) {
        ScheduleTask task = selectById(id);
        if (task == null) return 0;
        task.setLastRunAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return updateById(task);
    }

    default int updateNextRun(Long id, LocalDateTime nextRunAt) {
        ScheduleTask task = selectById(id);
        if (task == null) return 0;
        task.setNextRunAt(nextRunAt);
        task.setUpdatedAt(LocalDateTime.now());
        return updateById(task);
    }

    default int completeScheduledFire(Long id, LocalDateTime nextRunAt) {
        ScheduleTask task = selectById(id);
        if (task == null) return 0;
        task.setNextRunAt(nextRunAt);
        if (nextRunAt == null) task.setEnabled(false);
        task.setUpdatedAt(LocalDateTime.now());
        return updateById(task);
    }

    default int delete(Long id) {
        return deleteById(id);
    }
}
