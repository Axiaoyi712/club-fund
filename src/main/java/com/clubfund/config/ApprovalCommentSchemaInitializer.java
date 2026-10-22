package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 兼容原有 200 字字段，扩容时保留历史审批内容。 */
@Component
@DependsOnDatabaseInitialization
public class ApprovalCommentSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ApprovalCommentSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        expandColumn("activity", "reject_reason", "驳回原因");
        expandColumn("approve_log", "comment", "审批意见");
    }

    private void expandColumn(String table, String column, String description) {
        Long length = jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Long.class, table, column);
        if (length < 500) {
            jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY COLUMN " + column
                    + " VARCHAR(500) NULL COMMENT '" + description + "'");
        }
    }
}
