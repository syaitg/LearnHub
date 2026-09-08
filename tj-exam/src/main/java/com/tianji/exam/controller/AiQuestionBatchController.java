package com.tianji.exam.controller;

import com.tianji.api.dto.exam.AiQuestionBatchCreateRequestDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.WebUtils;
import com.tianji.common.utils.BeanUtils;
import com.tianji.exam.domain.dto.AiQuestionBatchConfirmDTO;
import com.tianji.exam.domain.dto.AiQuestionBatchCreateDTO;
import com.tianji.exam.domain.query.AiQuestionBatchPageQuery;
import com.tianji.exam.domain.vo.AiQuestionBatchVO;
import com.tianji.exam.service.IAiQuestionBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 出题批次接口
 */
@Tag(name = "AI 出题批次")
@RestController
@RequestMapping("/ai-question-batches")
@RequiredArgsConstructor
public class AiQuestionBatchController {

    private final IAiQuestionBatchService batchService;

    /**
     * 创建 AI 出题草稿批次
     *
     * @param dto 创建参数
     * @return 批次 ID
     */
    @Operation(summary = "创建 AI 出题批次")
    @PostMapping
    public Long createBatch(@Valid @RequestBody AiQuestionBatchCreateDTO dto) {
        return batchService.createBatch(dto);
    }

    /**
     * 为内部服务创建 AI 出题草稿批次
     *
     * @param request 内部创建参数
     * @return 批次 ID
     */
    @Operation(summary = "内部创建 AI 出题批次")
    @PostMapping("/internal")
    public Long createInternalBatch(@Valid @RequestBody AiQuestionBatchCreateRequestDTO request) {
        if (!WebUtils.isFeignRequest()) {
            throw new ForbiddenException("内部 AI 出题接口仅允许服务间调用");
        }
        return batchService.createBatch(BeanUtils.copyBean(request, AiQuestionBatchCreateDTO.class));
    }

    /**
     * 分页查询 AI 出题批次
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询 AI 出题批次")
    @GetMapping("/page")
    public PageDTO<AiQuestionBatchVO> queryPage(@Valid AiQuestionBatchPageQuery query) {
        return batchService.queryPage(query);
    }

    /**
     * 查询 AI 出题批次详情
     *
     * @param id 批次 ID
     * @return 批次详情
     */
    @Operation(summary = "查询 AI 出题批次详情")
    @GetMapping("/{id}")
    public AiQuestionBatchVO queryDetail(@PathVariable("id") Long id) {
        return batchService.queryDetail(id);
    }

    /**
     * 确认 AI 题目草稿
     *
     * @param id 批次 ID
     * @param dto 确认参数
     */
    @Operation(summary = "确认 AI 题目草稿")
    @PostMapping("/{id}/confirm")
    public void confirmDrafts(@PathVariable("id") Long id,
                              @Valid @RequestBody(required = false) AiQuestionBatchConfirmDTO dto) {
        batchService.confirmDrafts(id, dto == null ? new AiQuestionBatchConfirmDTO() : dto);
    }

    /**
     * 发布批次中已确认的 AI 题目
     *
     * @param id 批次 ID
     * @return 已发布题目 ID 列表
     */
    @Operation(summary = "发布已确认的 AI 题目")
    @PostMapping("/{id}/publish")
    public List<Long> publish(@PathVariable("id") Long id) {
        return batchService.publish(id);
    }

    /**
     * 重试生成失败的 AI 出题批次
     *
     * @param id 批次 ID
     */
    @Operation(summary = "重试 AI 出题批次")
    @PostMapping("/{id}/retry")
    public void retry(@PathVariable("id") Long id) {
        batchService.retry(id);
    }
}
