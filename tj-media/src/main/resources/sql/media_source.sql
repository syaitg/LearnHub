-- 媒资表增加云端账号来源字段。执行前请确认数据库中尚未存在 source 列。
ALTER TABLE `media`
    ADD COLUMN `source` TINYINT NULL
    COMMENT '云端账号来源：1-官方腾讯云，2-自有腾讯云；历史数据允许为空'
    AFTER `file_id`;

-- 历史数据不能根据 NULL 统一判断来源，请先按 file_id 和实际云端账号逐条核验。
-- 核验为自有账号的记录示例：
-- UPDATE `media` SET `source` = 2 WHERE `file_id` = '已核验的自有账号文件ID';
-- 核验为官方账号的记录示例：
-- UPDATE `media` SET `source` = 1 WHERE `file_id` = '已核验的官方账号文件ID';
-- media_url 应保存不带 t、exper、sign、psign 等临时参数的原始播放地址。
