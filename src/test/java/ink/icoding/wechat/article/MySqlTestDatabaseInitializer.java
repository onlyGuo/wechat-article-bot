package ink.icoding.wechat.article;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 在 Spring 创建任何业务 Bean 前清空专用 MySQL 测试库，保证集成测试可重复执行。
 */
public class MySqlTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String TEST_DATABASE = "wechat-article-test";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment environment = applicationContext.getEnvironment();
        String url = environment.getRequiredProperty("spring.datasource.url");
        String username = environment.getRequiredProperty("spring.datasource.username");
        String password = environment.getRequiredProperty("spring.datasource.password");

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())) {
                throw new IllegalStateException("测试数据源必须是 MySQL");
            }
            if (!TEST_DATABASE.equals(connection.getCatalog())) {
                throw new IllegalStateException("拒绝清理非测试库：" + connection.getCatalog());
            }
            dropAllTables(connection);
        } catch (Exception exception) {
            throw new IllegalStateException("初始化 MySQL 测试库失败", exception);
        }
    }

    private void dropAllTables(Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        List<String> tables = new ArrayList<>();
        try (ResultSet resultSet = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (resultSet.next()) tables.add(resultSet.getString("TABLE_NAME"));
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tables) {
                statement.execute("DROP TABLE `" + table.replace("`", "``") + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }
}
