package org.example.sliders.config;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * 提供 agent_test 数据源，供 AnswerTools 执行 SQL 查询
 */
@Component
public class DataSourceProvider {

    private static DataSource dataSource;

    public DataSourceProvider(DataSource dataSource) {
        DataSourceProvider.dataSource = dataSource;
    }

    public static DataSource getDataSource() {
        return dataSource;
    }
}
