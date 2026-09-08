package com.tianji.api.dto.course;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 课程目录业务详情
 */
@Data
@Schema(description = "课程目录业务校验详情")
public class CatalogueDetailDTO {
    private Long id;
    private String name;
    private Long courseId;

    /** 1-章，2-小节，3-练习或测试 */
    private Integer type;
    private Long parentCatalogueId;
    private Long mediaId;
    private Long videoId;
    private Integer mediaDuration;
    private Integer cIndex;
}
