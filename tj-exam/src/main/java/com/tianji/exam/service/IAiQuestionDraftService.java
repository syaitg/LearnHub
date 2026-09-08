package com.tianji.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.exam.domain.dto.AiQuestionDraftUpdateDTO;
import com.tianji.exam.domain.po.AiQuestionDraft;

/**
 * AI 题目草稿服务
 */
public interface IAiQuestionDraftService extends IService<AiQuestionDraft> {

    /**
     * 删除一条尚未发布的 AI 题目草稿
     */
    void deleteDraft(Long id);

    /**
     * 编辑并重新校验 AI 题目草稿
     */
    void updateDraft(Long id, AiQuestionDraftUpdateDTO dto);

    /**
     * 确认 AI 题目草稿
     */
    void confirm(Long id);

    /**
     * 驳回 AI 题目草稿
     */
    void reject(Long id, String reason);
}
