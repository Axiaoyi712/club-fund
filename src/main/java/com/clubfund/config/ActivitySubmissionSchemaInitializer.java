package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 历史申请保持空凭证，新申请按申请人与提交凭证唯一。 */
@Component
@DependsOnDatabaseInitialization
public class ActivitySubmissionSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ActivitySubmissionSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'activity' AND COLUMN_NAME = 'submission_token'", Long.class);
        if (columns == 0) {
            jdbcTemplate.execute("ALTER TABLE activity ADD COLUMN submission_token VARCHAR(36) NULL "
                    + "COMMENT '新增申请提交凭证'");
        }
        Long indexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'activity' AND INDEX_NAME = 'uk_activity_submission'", Long.class);
        if (indexes == 0) {
            jdbcTemplate.execute("ALTER TABLE activity ADD UNIQUE INDEX uk_activity_submission "
                    + "(apply_user, submission_token)");
        }
    }
}