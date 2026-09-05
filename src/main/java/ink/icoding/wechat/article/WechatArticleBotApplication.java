package ink.icoding.wechat.article;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("ink.icoding.wechat.article")
public class WechatArticleBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(WechatArticleBotApplication.class, args);
    }

}
