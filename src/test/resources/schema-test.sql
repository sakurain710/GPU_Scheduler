CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    mobile VARCHAR(20),
    avatar VARCHAR(500),
    gender TINYINT DEFAULT 0,
    user_type TINYINT DEFAULT 1,
    status TINYINT DEFAULT 1,
    login_ip VARCHAR(50),
    login_at DATETIME,
    pwd_reset_at DATETIME,
    remark VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME
);

CREATE TABLE IF NOT EXISTS `resource` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type TINYINT DEFAULT 1,
    parent_id BIGINT,
    path VARCHAR(255),
    sort_order INT DEFAULT 0,
    description VARCHAR(500),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `permission` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(150) NOT NULL,
    name VARCHAR(100) NOT NULL,
    resource_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    status TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    parent_role_id BIGINT,
    role_type TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    description VARCHAR(500),
    status TINYINT DEFAULT 1,
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `role_permission` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `user_role` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    expires_at DATETIME,
    granted_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `gpu` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    manufacturer VARCHAR(64) NOT NULL,
    memory_gb DECIMAL(8,2) NOT NULL,
    allocated_memory_gb DECIMAL(8,2) DEFAULT 0,
    computing_power_tflops DECIMAL(10,4) NOT NULL,
    status TINYINT DEFAULT 1,
    last_heartbeat_at DATETIME,
    offline_reason VARCHAR(500),
    remark VARCHAR(500),
    created_by BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME
);

CREATE TABLE IF NOT EXISTS `gpu_task` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    gpu_id BIGINT,
    title VARCHAR(128) NOT NULL,
    description CLOB,
    task_type VARCHAR(64) NOT NULL,
    min_memory_gb DECIMAL(8,2) NOT NULL,
    compute_units_gflop DECIMAL(16,4) NOT NULL,
    base_priority TINYINT DEFAULT 5,
    enqueue_at TIMESTAMP,
    dispatched_at TIMESTAMP,
    estimated_finish_at TIMESTAMP,
    finished_at TIMESTAMP,
    estimated_seconds DECIMAL(12,4),
    actual_seconds DECIMAL(12,4),
    apply_reason VARCHAR(500),
    reviewer_id BIGINT,
    review_at DATETIME,
    reject_reason VARCHAR(500),
    cancel_reason VARCHAR(500),
    status TINYINT DEFAULT 1,
    error_message CLOB,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME
);

CREATE TABLE IF NOT EXISTS `gpu_task_log` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    gpu_id BIGINT,
    event VARCHAR(32) NOT NULL,
    old_status TINYINT,
    new_status TINYINT,
    age_delta DECIMAL(8,4),
    detail CLOB,
    operator_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `task_dlq` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    retry_count INT DEFAULT 0,
    failure_reason VARCHAR(1000) NOT NULL,
    payload CLOB,
    status TINYINT DEFAULT 1,
    processed_by BIGINT,
    processed_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `notification` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_id BIGINT,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    read_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `ops_event_log` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64),
    target_id BIGINT,
    reason VARCHAR(1000),
    operator_id BIGINT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
