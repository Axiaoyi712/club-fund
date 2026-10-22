package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 旧申请时间可能已被重提覆盖，不能据此回填首次提交时间。 */
@Component
@DependsOnDatabaseInitialization
public class ActivityTimeSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ActivityTimeSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'activity' AND COLUMN_NAME = 'first_apply_time'", Long.class);
        if (columns == 0) {
            jdbcTemplate.execute("ALTER TABLE activity ADD COLUMN first_apply_time DATETIME NULL "
                    + "COMMENT '首次提交时间，未知时为空'");
        }
    }
}
