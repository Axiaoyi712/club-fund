package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 实际支出日期独立保存，历史登记时间不能据此推断为实际日期。 */
@Component
@DependsOnDatabaseInitialization
public class ExpenseDateSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseDateSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'expense' AND COLUMN_NAME = 'occurred_date'", Long.class);
        if (columns == 0) {
            jdbcTemplate.execute("ALTER TABLE expense ADD COLUMN occurred_date DATE NULL "
                    + "COMMENT '实际支出日期，可不填'");
        }
    }
}