package ink.icoding.wechat.article.schedule;

import org.quartz.*;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

@Service
public class QuartzTaskManager {
    private final Scheduler scheduler;
    private final ScheduleTaskMapper mapper;

    public QuartzTaskManager(Scheduler scheduler, ScheduleTaskMapper mapper) {
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restore() {
        for (ScheduleTask task : mapper.findEnabled()) schedule(task);
    }

    public void schedule(ScheduleTask task) {
        try {
            JobKey jobKey = jobKey(task.getId());
            if (scheduler.checkExists(jobKey)) scheduler.deleteJob(jobKey);
            if (!Boolean.TRUE.equals(task.getEnabled())) {
                mapper.updateNextRun(task.getId(), null);
                return;
            }
            JobDetail detail = JobBuilder.newJob(ScheduleTaskJob.class).withIdentity(jobKey)
                    .usingJobData("taskId", String.valueOf(task.getId())).build();
            CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(task.getCronExpression())
                    .inTimeZone(TimeZone.getTimeZone(task.getTimezone()))
                    .withMisfireHandlingInstructionDoNothing();
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity("task-trigger-" + task.getId(), "article-tasks")
                    .forJob(detail).withSchedule(schedule).build();
            Date next = scheduler.scheduleJob(detail, trigger);
            mapper.updateNextRun(task.getId(), LocalDateTime.ofInstant(next.toInstant(), ZoneId.of(task.getTimezone())));
        } catch (Exception exception) {
            throw new IllegalArgumentException("定时表达式无效：" + exception.getMessage(), exception);
        }
    }

    public void delete(Long id) {
        try {
            scheduler.deleteJob(jobKey(id));
        } catch (SchedulerException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public void completeFire(Long taskId, Date nextFireTime) {
        ScheduleTask task = mapper.findById(taskId);
        if (task == null) return;
        LocalDateTime next = nextFireTime == null ? null
                : LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.of(task.getTimezone()));
        mapper.completeScheduledFire(taskId, next);
    }

    private JobKey jobKey(Long id) {
        return new JobKey("article-task-" + id, "article-tasks");
    }
}
