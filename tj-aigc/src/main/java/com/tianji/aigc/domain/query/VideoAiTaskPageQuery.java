package com.tianji.aigc.domain.query;

import com.tianji.common.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 视频 AI 处理任务分页查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "视频 AI 处理任务分页查询参数")
public class VideoAiTaskPageQuery extends PageQuery {
    private Long courseId;
    private Long sectionId;
    private Long mediaId;
    private String status;
}