package com.tianji.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.AiQuestionBatchConfirmDTO;
import com.tianji.exam.domain.dto.AiQuestionBatchCreateDTO;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.query.AiQuestionBatchPageQuery;
import com.tianji.exam.domain.vo.AiQuestionBatchVO;

import java.util.List;

/**
 * AI 出题批次服务
 */
public interface IAiQuestionBatchService extends IService<AiQuestionBatch> {

    /**
     * 创建 AI 出题批次
     */
    Long createBatch(AiQuestionBatchCreateDTO dto);

    /**
     * 分页查询 AI 出题批次
     */
    PageDTO<AiQuestionBatchVO> queryPage(AiQuestionBatchPageQuery query);

    /**
     * 查询 AI 出题批次详情
     */
    AiQuestionBatchVO queryDetail(Long id);

    /**
     * 批量确认 AI 题目草稿
     */
    void confirmDrafts(Long batchId, AiQuestionBatchConfirmDTO dto);

    /**
     * 发布已确认的 AI 题目
     */
    List<Long> publish(Long batchId);

    /**
     * 重试生成失败的 AI 出题批次
     */
    void retry(Long batchId);
}
