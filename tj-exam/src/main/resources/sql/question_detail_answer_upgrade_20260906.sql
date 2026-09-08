-- AI 出题的主观题参考答案可能超过原 answer 字段长度，发布时不得截断答案。
-- TEXT 最多可保存 65,535 字节，能够覆盖当前业务允许的 10,000 字符答案。
ALTER TABLE `question_detail`
    MODIFY COLUMN `answer` TEXT COMMENT '选择题答案或主观题参考答案';
