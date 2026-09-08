package com.tianji.exam.controller;

import com.tianji.exam.domain.dto.AiQuestionDraftUpdateDTO;
import com.tianji.exam.domain.dto.AiQuestionRejectDTO;
import com.tianji.exam.service.IAiQuestionDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能出题草稿管理接口
 */
@Tag(name = "AI 智能出题草稿管理")
@RestController
@RequestMapping("/ai-question-drafts")
@RequiredArgsConstructor
public class AiQuestionDraftController {

    private final IAiQuestionDraftService draftService;

    /**
     * 删除一条尚未发布的 AI 题目草稿。
     */
    @Operation(summary = "删除 AI 题目草稿")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        draftService.deleteDraft(id);
    }

    /**
     * 编辑并重新校验 AI 题目草稿。
     */
    @Operation(summary = "编辑并重新校验 AI 题目草稿")
    @PutMapping("/{id}")
    public void updateDraft(
            @PathVariable("id") Long id,
            @Valid @RequestBody AiQuestionDraftUpdateDTO dto) {
        draftService.updateDraft(id, dto);
    }

    /**
     * 确认 AI 题目草稿。
     */
    @Operation(summary = "确认一条有效的 AI 题目草稿")
    @PostMapping("/{id}/confirm")
    public void confirm(@PathVariable("id") Long id) {
        draftService.confirm(id);
    }

    /**
     * 驳回 AI 题目草稿。
     */
    @Operation(summary = "驳回一条 AI 题目草稿")
    @PostMapping("/{id}/reject")
    public void reject(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) AiQuestionRejectDTO dto) {
        draftService.reject(id, dto == null ? null : dto.getReason());
    }
}
