package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 历史支出保持空凭证，新登记按活动与提交凭证唯一。 */
@Component
@DependsOnDatabaseInitialization
public class ExpenseSubmissionSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ExpenseSubmissionSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'expense' AND COLUMN_NAME = 'submission_token'", Long.class);
        if (columns == 0) {
            jdbcTemplate.execute("ALTER TABLE expense ADD COLUMN submission_token VARCHAR(36) NULL "
                    + "COMMENT '支出登记提交凭证'");
        }
        Long indexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'expense' AND INDEX_NAME = 'uk_expense_submission'", Long.class);
        if (indexes == 0) {
            jdbcTemplate.execute("ALTER TABLE expense ADD UNIQUE INDEX uk_expense_submission "
                    + "(activity_id, submission_token)");
        }
    }
}