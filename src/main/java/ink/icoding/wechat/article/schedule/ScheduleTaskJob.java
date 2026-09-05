package ink.icoding.wechat.article.schedule;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

@DisallowConcurrentExecution
public class ScheduleTaskJob implements Job {
    @Autowired
    private TaskExecutionService executionService;
    @Autowired
    private QuartzTaskManager quartzTaskManager;

    @Override
    public void execute(JobExecutionContext context) {
        Long taskId = Long.parseLong(context.getMergedJobDataMap().getString("taskId"));
        try {
            executionService.execute(taskId, "SCHEDULED");
        } finally {
            quartzTaskManager.completeFire(taskId, context.getNextFireTime());
        }
    }
}
