package com.tianji.exam.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.exam.domain.dto.WrongQuestionReviewDTO;
import com.tianji.exam.domain.query.WrongQuestionPageQuery;
import com.tianji.exam.domain.vo.WrongQuestionReviewVO;
import com.tianji.exam.domain.vo.WrongQuestionVO;
import com.tianji.exam.service.IWrongQuestionService;
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
 * 学生错题相关接口
 */
@Tag(name = "学生错题相关接口")
@RestController
@RequestMapping("/wrong-questions")
@RequiredArgsConstructor
public class WrongQuestionController {

    private final IWrongQuestionService wrongQuestionService;

    /**
     * 分页查询当前学生的错题
     *
     * @param query 错题分页查询参数
     * @return 错题分页结果
     */
    @Operation(summary = "分页查询我的错题")
    @GetMapping("/page")
    public PageDTO<WrongQuestionVO> queryMyWrongQuestions(@Valid WrongQuestionPageQuery query) {
        return wrongQuestionService.queryMyWrongQuestions(query);
    }

    /**
     * 查询指定错题的重做记录
     *
     * @param id 错题记录 ID
     * @return 重做记录列表
     */
    @Operation(summary = "查询错题重做记录")
    @GetMapping("/{id}/reviews")
    public List<WrongQuestionReviewVO> queryReviews(@PathVariable("id") Long id) {
        return wrongQuestionService.queryReviews(id);
    }

    /**
     * 提交一次错题重做
     *
     * @param id 错题记录 ID
     * @param dto 错题重做参数
     * @return 本次重做结果
     */
    @Operation(summary = "提交错题重做")
    @PostMapping("/{id}/reviews")
    public WrongQuestionReviewVO review(@PathVariable("id") Long id,
                                        @Valid @RequestBody WrongQuestionReviewDTO dto) {
        return wrongQuestionService.review(id, dto);
    }
}
