package ink.icoding.wechat.article.dashboard;

import ink.icoding.wechat.article.account.WechatAccountMapper;
import ink.icoding.wechat.article.ai.AiMessageMapper;
import ink.icoding.wechat.article.article.ArticleMapper;
import ink.icoding.wechat.article.common.ApiResponse;
import ink.icoding.wechat.article.follower.WechatFollowerMapper;
import ink.icoding.wechat.article.schedule.ScheduleTaskMapper;
import ink.icoding.wechat.article.schedule.TaskRun;
import ink.icoding.wechat.article.schedule.TaskRunMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final WechatAccountMapper accounts;
    private final ArticleMapper articles;
    private final ScheduleTaskMapper tasks;
    private final TaskRunMapper taskRuns;
    private final WechatFollowerMapper followers;
    private final AiMessageMapper messages;

    public DashboardController(WechatAccountMapper accounts, ArticleMapper articles, ScheduleTaskMapper tasks,
                               TaskRunMapper taskRuns,
                               WechatFollowerMapper followers, AiMessageMapper messages) {
        this.accounts = accounts;
        this.articles = articles;
        this.tasks = tasks;
        this.taskRuns = taskRuns;
        this.followers = followers;
        this.messages = messages;
    }

    @GetMapping
    public ApiResponse<Dashboard> get() {
        return ApiResponse.ok(new Dashboard(accounts.count(), articles.countDrafts(), articles.countPublished(),
                articles.countFailed(), tasks.countEnabled(), followers.countSubscribed(), messages.totalTokens(),
                taskRuns.findRecentRuns(8)));
    }

    public record Dashboard(long accounts, long drafts, long published, long failed, long activeTasks,
                            long followers, long aiTokens, List<TaskRun> recentRuns) {}
}
