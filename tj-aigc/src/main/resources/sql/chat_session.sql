-- AI chat session history table.
-- Run this script against the tj_aigc database.
CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` BIGINT NOT NULL COMMENT 'data id',
  `session_id` VARCHAR(64) NOT NULL COMMENT 'chat session id',
  `user_id` BIGINT NOT NULL COMMENT 'user id',
  `title` VARCHAR(100) DEFAULT NULL COMMENT 'chat session title',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `creater` BIGINT NOT NULL COMMENT 'created by',
  `updater` BIGINT NOT NULL COMMENT 'updated by',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_session_session_id` (`session_id`),
  KEY `idx_chat_session_user_update` (`user_id`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI chat session history';
