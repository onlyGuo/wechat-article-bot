package ink.icoding.wechat.article.schedule;

import ink.icoding.wechat.article.auth.CurrentUserService;
import ink.icoding.wechat.article.common.BusinessException;
import jakarta.validation.constraints.NotBlank;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
public class ScheduleTaskService {
    private static final Set<String> OUTPUT_MODES = Set.of("LOCAL_DRAFT", "WECHAT_DRAFT", "AUTO_PUBLISH");
    private final ScheduleTaskMapper mapper;
    private final TaskRunMapper runMapper;
    private final QuartzTaskManager quartz;
    private final TaskExecutionService executionService;
    private final CurrentUserService currentUserService;

    public ScheduleTaskService(ScheduleTaskMapper mapper, TaskRunMapper runMapper, QuartzTaskManager quartz,
                               TaskExecutionService executionService, CurrentUserService currentUserService) {
        this.mapper = mapper;
        this.runMapper = runMapper;
        this.quartz = quartz;
        this.executionService = executionService;
        this.currentUserService = currentUserService;
    }

    public List<ScheduleTask> list() { return mapper.findAll(); }

    public ScheduleTask required(Long id) {
        ScheduleTask task = mapper.findById(id);
        if (task == null) throw new BusinessException("定时任务不存在");
        return task;
    }

    @Transactional
    public ScheduleTask create(TaskRequest request) {
        ScheduleTask task = apply(new ScheduleTask(), request);
        task.setCreatedBy(currentUserService.required().id());
        task.setCreatedAt(java.time.LocalDateTime.now());
        task.setUpdatedAt(java.time.LocalDateTime.now());
        mapper.insert(task);
        quartz.schedule(task);
        return required(task.getId());
    }

    @Transactional
    public ScheduleTask update(Long id, TaskRequest request) {
        ScheduleTask task = apply(required(id), request);
        mapper.update(task);
        quartz.schedule(task);
        return required(id);
    }

    public TaskRun run(Long id) {
        required(id);
        return executionService.start(id, "MANUAL");
    }

    public List<TaskRun> runs(Long id) {
        required(id);
        return runMapper.findRuns(id);
    }

    @Transactional
    public void delete(Long id) {
        required(id);
        quartz.delete(id);
        mapper.delete(id);
    }

    private ScheduleTask apply(ScheduleTask task, TaskRequest request) {
        if (!CronExpression.isValidExpression(request.cronExpression())) throw new BusinessException("Cron 表达式无效");
        task.setName(request.name());
        task.setAccountId(request.accountId());
        task.setCoverAssetId(request.coverAssetId());
        task.setCronExpression(request.cronExpression());
        task.setTimezone(request.timezone() == null ? "Asia/Shanghai" : request.timezone());
        try {
            ZoneId.of(task.getTimezone());
        } catch (Exception error) {
            throw new BusinessException("时区无效：" + task.getTimezone());
        }
        try {
            CronExpression cron = new CronExpression(task.getCronExpression());
            cron.setTimeZone(java.util.TimeZone.getTimeZone(task.getTimezone()));
            if ((request.enabled() == null || request.enabled())
                    && cron.getNextValidTimeAfter(new Date()) == null) {
                throw new BusinessException("该执行计划已经没有未来触发时间，请重新选择时间");
            }
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException("Cron 表达式无效：" + error.getMessage());
        }
        task.setAiPrompt(request.aiPrompt().trim());
        task.setOutputMode(request.outputMode() == null ? "LOCAL_DRAFT" : request.outputMode());
        if (!OUTPUT_MODES.contains(task.getOutputMode())) throw new BusinessException("不支持的任务输出方式");
        if (!"LOCAL_DRAFT".equals(task.getOutputMode()) && task.getAccountId() == null) {
            throw new BusinessException("同步微信的任务必须选择目标公众号");
        }
        task.setEnabled(request.enabled() == null || request.enabled());
        return task;
    }

    public record TaskRequest(@NotBlank String name, Long accountId, Long coverAssetId, @NotBlank String cronExpression,
                              String timezone, @NotBlank String aiPrompt,
                              String outputMode, Boolean enabled) {}
}
