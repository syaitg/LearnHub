-- 文件表增加云端账号来源字段。执行前请确认数据库中尚未存在 source 列。
ALTER TABLE `file`
    ADD COLUMN `source` TINYINT NULL
    COMMENT '云端账号来源：1-官方腾讯云，2-自有腾讯云；历史数据允许为空'
    AFTER `platform`;

-- 历史数据不能根据 NULL 统一判断来源，请先按文件 key 和实际云端账号逐条核验。
-- 核验为自有账号的记录示例：
-- UPDATE `file` SET `source` = 2 WHERE `id` = 123;
-- 核验为官方账号的记录示例：
-- UPDATE `file` SET `source` = 1 WHERE `id` = 456;
