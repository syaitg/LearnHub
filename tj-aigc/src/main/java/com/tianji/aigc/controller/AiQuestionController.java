package com.tianji.aigc.controller;

import com.tianji.aigc.service.QuestionGenerationService;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.WebUtils;
import com.tianji.api.dto.aigc.AiQuestionGenerateRequestDTO;
import com.tianji.api.dto.aigc.AiQuestionGenerateResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * AI 智能出题接口
 */
@Tag(name = "AI 智能出题")
@RestController
@RequestMapping("/ai/questions")
@RequiredArgsConstructor
public class AiQuestionController {

    private final QuestionGenerationService questionGenerationService;


    /**
     * 根据课程材料生成题目草稿
     *
     * @param request AI 出题请求
     * @return AI 生成的题目
     */
    @Operation(summary = "生成 AI 题目草稿")
    @PostMapping("/generate")
    public AiQuestionGenerateResultDTO generate(@Valid @RequestBody AiQuestionGenerateRequestDTO request) {
        if (!WebUtils.isFeignRequest()) {
            throw new ForbiddenException("AI 出题接口仅允许服务间调用");
        }
        return questionGenerationService.generate(request);
    }
}
