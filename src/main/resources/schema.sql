-- 建表语句：项目启动时自动执行（表已存在就跳过）
-- 本期只建 user 表（用户/角色），后面每做一个功能加一张表

CREATE TABLE IF NOT EXISTS user (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL UNIQUE COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密后的）',
    real_name   VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    role        VARCHAR(20)  NOT NULL COMMENT '角色：applicant申请人/chairman主席团/teacher团委老师/leader学院领导/admin管理员',
    phone       VARCHAR(20)  COMMENT '联系方式',
    email       VARCHAR(50)  COMMENT '邮箱',
    create_time DATETIME     COMMENT '注册时间'
) COMMENT '用户表';

-- 社团表（主办单位）：申请活动时选择是哪个社团主办的
CREATE TABLE IF NOT EXISTS club (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100) NOT NULL COMMENT '社团名称（主办单位）',
    description VARCHAR(500) COMMENT '社团简介',
    leader_id   BIGINT COMMENT '负责人（对应user表id）',
    create_time DATETIME COMMENT '创建时间'
) COMMENT '社团表';

-- 社团成员表：记录社团成员名单（成员管理用，一个社团可有多名成员）
CREATE TABLE IF NOT EXISTS club_member (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    club_id     BIGINT NOT NULL COMMENT '所属社团',
    member_name VARCHAR(50) NOT NULL COMMENT '成员姓名',
    join_time   DATETIME COMMENT '加入时间'
) COMMENT '社团成员表';

-- 活动申请表：对应学院《活动经费申请表》主表
CREATE TABLE IF NOT EXISTS activity (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    club_id       BIGINT NOT NULL COMMENT '主办社团',
    organizer     VARCHAR(100) COMMENT '承办/协办单位',
    title         VARCHAR(200) NOT NULL COMMENT '活动内容（全称）',
    location      VARCHAR(100) COMMENT '活动地点',
    start_time    DATETIME COMMENT '活动开始时间',
    end_time      DATETIME COMMENT '活动结束时间',
    total_budget  DECIMAL(10,2) DEFAULT 0 COMMENT '预算合计（自动算）',
    total_apply   DECIMAL(10,2) DEFAULT 0 COMMENT '申请金额合计（自动算）',
    status        VARCHAR(30) NOT NULL DEFAULT 'submitted' COMMENT '状态：submitted已提交/chairman_passed主席团通过/teacher_passed团委老师通过/leader_passed领导通过/approved获批/rejected驳回',
    apply_user    BIGINT NOT NULL COMMENT '申请人（=活动负责人）',
    first_apply_time DATETIME COMMENT '首次提交时间，未知时为空',
    apply_time    DATETIME COMMENT '最新提交时间',
    reject_reason VARCHAR(500) COMMENT '驳回原因',
    archived      TINYINT DEFAULT 0 COMMENT '是否已归档：0未归档 1已归档（活动结束后入台账）',
    archive_time  DATETIME COMMENT '归档时间',
    archive_operator_id BIGINT NULL COMMENT '归档操作人账号，历史未知时为空',
    archive_operator_name VARCHAR(50) NULL COMMENT '归档时的操作人姓名',
    submission_token VARCHAR(36) NULL COMMENT '新增申请提交凭证',
    UNIQUE KEY uk_activity_submission (apply_user, submission_token)
) COMMENT '活动申请表';

-- 预算明细表：对应申请表"经费预算明细"栏目，一行一条开支
CREATE TABLE IF NOT EXISTS budget_item (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    activity_id   BIGINT NOT NULL COMMENT '所属申请表',
    item_name     VARCHAR(100) NOT NULL COMMENT '开支项目',
    quantity      INT NOT NULL COMMENT '数量',
    unit_price    DECIMAL(10,2) NOT NULL COMMENT '单价',
    budget_amount DECIMAL(10,2) COMMENT '预算金额=数量×单价（自动算）',
    apply_amount  DECIMAL(10,2) COMMENT '申请金额'
) COMMENT '预算明细表';

-- 审批记录表：谁在什么时间做了什么审批（留痕，对应申请表三级签章栏）
CREATE TABLE IF NOT EXISTS approve_log (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    activity_id   BIGINT NOT NULL COMMENT '所属申请表',
    stage         VARCHAR(20) COMMENT '审批级：chairman主席团/teacher团委老师/leader学院领导',
    action        VARCHAR(20) COMMENT '动作：pass通过/reject驳回',
    operator_id   BIGINT COMMENT '审批人id',
    operator_name VARCHAR(50) COMMENT '审批人姓名',
    comment       VARCHAR(500) COMMENT '审批意见',
    create_time   DATETIME COMMENT '审批时间'
) COMMENT '审批记录表';

-- 支出登记表：获批后登记每笔实际支出（用于超支预警与预算执行分析）
CREATE TABLE IF NOT EXISTS expense (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    activity_id    BIGINT NOT NULL COMMENT '所属申请表',
    item_name      VARCHAR(100) NOT NULL COMMENT '支出项目',
    amount         DECIMAL(10,2) NOT NULL COMMENT '支出金额',
    expense_time   DATETIME COMMENT '系统登记时间',
    occurred_date  DATE COMMENT '实际支出日期，可不填',
    note           VARCHAR(200) COMMENT '备注',
    budget_item_id BIGINT COMMENT '关联预算明细行id（用于明细级执行对比）',
    submission_token VARCHAR(36) NULL COMMENT '支出登记提交凭证',
    UNIQUE KEY uk_expense_submission (activity_id, submission_token)
) COMMENT '支出登记表';

-- 消息通知表：流程待办与结果提醒（提交/审批/驳回时自动生成）
CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT NOT NULL COMMENT '接收人id',
    type        VARCHAR(20) COMMENT '类型：todo待办/result结果',
    content     VARCHAR(200) COMMENT '通知内容',
    related_id  BIGINT COMMENT '关联申请id',
    is_read     TINYINT DEFAULT 0 COMMENT '0未读 1已读',
    is_processed TINYINT NOT NULL DEFAULT 0 COMMENT '待办处理状态：0待处理 1已处理',
    create_time DATETIME COMMENT '通知时间'
) COMMENT '消息通知表';
