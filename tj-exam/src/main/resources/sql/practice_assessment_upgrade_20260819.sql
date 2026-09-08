-- P1-C 练习批改功能升级脚本：增加当前正式总分字段。
-- 该脚本仅需在尚未添加 final_score 字段的数据库中执行一次。
ALTER TABLE `practice_session`
    ADD COLUMN `final_score` INT NOT NULL DEFAULT 0 COMMENT '当前正式总分' AFTER `objective_score`;
