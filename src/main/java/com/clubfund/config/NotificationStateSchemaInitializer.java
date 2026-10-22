package com.clubfund.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 为旧通知补充独立处理状态，保留通知内容与已读状态。 */
@Component
@DependsOnDatabaseInitialization
public class NotificationStateSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public NotificationStateSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        Long columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'is_processed'", Long.class);
        if (columns == 0) {
            // NULL 标记尚未迁移的旧通知，迁移中断后可在下次启动继续。
            jdbcTemplate.execute("ALTER TABLE notification ADD COLUMN is_processed TINYINT NULL "
                    + "DEFAULT NULL COMMENT '待办处理状态：0待处理 1已处理'");
        }
        String nullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'is_processed'", String.class);
        if ("YES".equals(nullable)) {
            // 旧通知用固定前缀识别审批阶段；较新的结果通知划分驳回前后的轮次。
            // 通知 ID 可区分同一秒内的驳回与重提，避免重新激活旧轮次的待办。
            jdbcTemplate.update("""
                    UPDATE notification n
                    LEFT JOIN activity a ON a.id = n.related_id
                    LEFT JOIN notification r ON r.related_id = n.related_id AND r.type = 'result' AND r.id > n.id
                    SET n.is_processed = CASE WHEN n.type = 'todo' AND r.id IS NULL AND (
                        (a.status = 'submitted' AND (n.content LIKE '新活动申请待审批：%'
                            OR n.content LIKE '申请已重新提交，待审批：%'))
                        OR (a.status = 'chairman_passed' AND n.content LIKE '申请待团委老师审批：%')
                        OR (a.status = 'teacher_passed' AND n.content LIKE '申请待学院领导审批：%')
                    ) THEN 0 ELSE 1 END
                    WHERE n.is_processed IS NULL
                    """);
            jdbcTemplate.execute("ALTER TABLE notification MODIFY COLUMN is_processed TINYINT NOT NULL "
                    + "DEFAULT 0 COMMENT '待办处理状态：0待处理 1已处理'");
        }
    }
}