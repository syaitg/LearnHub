-- P1-A V1.1：为已有 AI 出题批次表补充章内小节范围字段。
-- 本脚本兼容未升级和已升级数据库，重复执行不会因为字段已存在而失败。
-- 新环境直接执行最新版 ai_question.sql 即可；执行本脚本前请确认当前数据库已选中。

-- 补充章级出题选择的小节 ID 列表字段。
SET @scope_section_ids_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_question_batch'
      AND COLUMN_NAME = 'scope_section_ids'
);
SET @scope_section_ids_sql := IF(
    @scope_section_ids_exists = 0,
    'ALTER TABLE `ai_question_batch` ADD COLUMN `scope_section_ids` JSON DEFAULT NULL COMMENT ''章级出题选择的小节 ID 列表'' AFTER `scope_name`',
    'SELECT 1'
);
PREPARE stmt_scope_section_ids FROM @scope_section_ids_sql;
EXECUTE stmt_scope_section_ids;
DEALLOCATE PREPARE stmt_scope_section_ids;

-- 补充章级出题选择的小节名称列表字段。
SET @scope_section_names_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ai_question_batch'
      AND COLUMN_NAME = 'scope_section_names'
);
SET @scope_section_names_sql := IF(
    @scope_section_names_exists = 0,
    'ALTER TABLE `ai_question_batch` ADD COLUMN `scope_section_names` JSON DEFAULT NULL COMMENT ''章级出题选择的小节名称列表'' AFTER `scope_section_ids`',
    'SELECT 1'
);
PREPARE stmt_scope_section_names FROM @scope_section_names_sql;
EXECUTE stmt_scope_section_names;
DEALLOCATE PREPARE stmt_scope_section_names;
