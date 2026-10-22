package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 保存归档时的操作人，历史未知信息不推测补填。 */
@Component
@DependsOnDatabaseInitialization
public class ArchiveOperatorSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public ArchiveOperatorSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        Long idColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'activity' AND COLUMN_NAME = 'archive_operator_id'", Long.class);
        if (idColumns == 0) {
            jdbcTemplate.execute("ALTER TABLE activity ADD COLUMN archive_operator_id BIGINT NULL "
                    + "COMMENT '归档操作人账号，历史未知时为空'");
        }
        Long nameColumns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'activity' AND COLUMN_NAME = 'archive_operator_name'", Long.class);
        if (nameColumns == 0) {
            jdbcTemplate.execute("ALTER TABLE activity ADD COLUMN archive_operator_name VARCHAR(50) NULL "
                    + "COMMENT '归档时的操作人姓名'");
        }
    }
}
