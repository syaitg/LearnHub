package com.tianji.aigc.controller;

import com.tianji.aigc.service.SubjectiveGradingService;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.WebUtils;
import com.tianji.api.dto.aigc.SubjectiveGradingRequestDTO;
import com.tianji.api.dto.aigc.SubjectiveGradingResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 主观题 AI 评估接口
 */
@Tag(name = "主观题 AI 评估")
@RestController
@RequestMapping("/ai/subjective-grading")
@RequiredArgsConstructor
public class SubjectiveGradingController {

    private final SubjectiveGradingService subjectiveGradingService;


    /**
     * 根据题目、评分要点和学生答案生成评估建议
     *
     * @param request 主观题评估请求
     * @return 主观题评估结果
     */
    @Operation(summary = "生成主观题 AI 评估建议")
    @PostMapping
    public SubjectiveGradingResultDTO grade(@Valid @RequestBody SubjectiveGradingRequestDTO request) {
        if (!WebUtils.isFeignRequest()) {
            throw new ForbiddenException("主观题 AI 评估接口仅允许服务间调用");
        }
        return subjectiveGradingService.grade(request);
    }
}
