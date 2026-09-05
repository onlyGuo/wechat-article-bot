package ink.icoding.wechat.article.schedule;

import org.junit.jupiter.api.Test;
import org.quartz.CronExpression;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleCronTests {

    @Test
    void graphicalBuilderPatternsAreValidQuartzExpressions() {
        List.of(
                "0/30 * * * * ?",
                "0 0/15 * * * ?",
                "0 5 0/2 * * ?",
                "0 30 9 * * ?",
                "0 30 9 ? * MON,WED,FRI",
                "0 30 9 1,15 * ?",
                "0 30 9 L * ?",
                "0 30 9 21 7 ?"
        ).forEach(expression -> assertTrue(CronExpression.isValidExpression(expression), expression));
    }

    @Test
    void yearQualifiedCronRunsOnlyOnce() throws Exception {
        LocalDateTime now = LocalDateTime.now().plusDays(2).withNano(0);
        String expression = "%d %d %d %d %d ? %d".formatted(
                now.getSecond(), now.getMinute(), now.getHour(), now.getDayOfMonth(),
                now.getMonthValue(), now.getYear());
        CronExpression cron = new CronExpression(expression);
        cron.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        Date first = cron.getNextValidTimeAfter(new Date());
        assertNotNull(first);
        assertNull(cron.getNextValidTimeAfter(first));
    }
}
