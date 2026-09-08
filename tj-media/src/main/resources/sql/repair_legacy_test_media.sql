-- 历史测试视频修复脚本
--
-- 用法：
-- 1. 先执行“查询正常媒资”语句，确认 2095879957129056258 确实是可以播放的新视频。
-- 2. 把需要修复的历史测试媒资 ID 填入临时表，不能把所有媒资都直接改掉。
-- 3. 执行整个脚本，确认最后的结果后提交事务；如结果不符合预期，把 COMMIT 改成 ROLLBACK。
--
-- 说明：脚本会同时复制 file_id、source、media_url 等字段。
-- 只改 media 表记录，不改变课程目录中的 media_id，因此课程仍然引用原来的媒资记录。
-- 这样历史课程会使用新视频对应的 file_id 和原始播放地址，并由后端重新生成防盗链参数。

SET @normal_media_id = 2095879957129056258;

-- 查询正常媒资，确认它的来源、文件编号和原始播放地址均有效。
SELECT id,
       file_id,
       source,
       filename,
       media_url,
       cover_url,
       duration,
       size,
       status
FROM `media`
WHERE id = @normal_media_id;

DROP TEMPORARY TABLE IF EXISTS `tmp_legacy_test_media`;
CREATE TEMPORARY TABLE `tmp_legacy_test_media` (
    media_id BIGINT NOT NULL PRIMARY KEY
);

-- 已确认的历史测试媒资 ID：
-- 2094804692399943682：MySQL零基础通关指南：从入门到实战
-- 2094806119792898050：tjxt体验课
-- 2095468424780181506：测试AI工作台
INSERT INTO `tmp_legacy_test_media` (media_id)
VALUES (2094804692399943682),
       (2094806119792898050),
       (2095468424780181506);
-- 执行修复前，先查看待修改的记录及其课程引用情况。
SELECT old_media.id AS legacy_media_id,
       old_media.file_id AS old_file_id,
       old_media.source AS old_source,
       old_media.media_url AS old_media_url,
       catalogue.course_id,
       catalogue.name AS catalogue_name
FROM `tmp_legacy_test_media` ids
JOIN `media` old_media ON old_media.id = ids.media_id
LEFT JOIN `course_catalogue` catalogue ON catalogue.media_id = old_media.id
WHERE old_media.id <> @normal_media_id;

START TRANSACTION;

-- 将选中的历史测试视频改为正常视频的媒资信息。
UPDATE `media` old_media
JOIN `tmp_legacy_test_media` ids ON ids.media_id = old_media.id
JOIN `media` normal_media ON normal_media.id = @normal_media_id
SET old_media.file_id = normal_media.file_id,
    old_media.source = normal_media.source,
    old_media.filename = normal_media.filename,
    old_media.media_url = normal_media.media_url,
    old_media.cover_url = normal_media.cover_url,
    old_media.duration = normal_media.duration,
    old_media.size = normal_media.size,
    old_media.status = normal_media.status
WHERE old_media.id <> @normal_media_id
  AND normal_media.source = 2
  AND normal_media.status = 2
  AND normal_media.file_id IS NOT NULL
  AND normal_media.file_id <> ''
  AND normal_media.media_url IS NOT NULL
  AND normal_media.media_url <> '';

SET @updated_rows = ROW_COUNT();

-- 查看修复后的记录，确认 file_id、source 和 media_url 已与正常媒资一致。
SELECT old_media.id AS legacy_media_id,
       old_media.file_id,
       old_media.source,
       old_media.media_url,
       old_media.duration,
       old_media.status
FROM `tmp_legacy_test_media` ids
JOIN `media` old_media ON old_media.id = ids.media_id
WHERE old_media.id <> @normal_media_id;

SELECT @updated_rows AS updated_rows;

-- 确认结果无误后提交；如需撤销本次修改，请将下一行改为 ROLLBACK。
COMMIT;
