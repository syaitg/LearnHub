package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.WrongQuestionReviewDTO;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeQuestion;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.domain.po.WrongQuestion;
import com.tianji.exam.domain.po.WrongQuestionReview;
import com.tianji.exam.domain.query.WrongQuestionPageQuery;
import com.tianji.exam.domain.vo.WrongQuestionReviewVO;
import com.tianji.exam.domain.vo.WrongQuestionVO;
import com.tianji.exam.enums.WrongQuestionStatus;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.mapper.WrongQuestionMapper;
import com.tianji.exam.mapper.WrongQuestionReviewMapper;
import com.tianji.exam.service.IWrongQuestionService;
import com.tianji.exam.utils.ObjectiveAnswerGrader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生错题服务实现
 */
@Service
@RequiredArgsConstructor
public class WrongQuestionServiceImpl extends ServiceImpl<WrongQuestionMapper, WrongQuestion>
        implements IWrongQuestionService {

    private final PracticeQuestionMapper practiceQuestionMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final WrongQuestionReviewMapper reviewMapper;

    /**
     * 分页查询当前学生的错题，并使用最近一次练习题快照展示题目内容
     *
     * @param query 错题分页查询参数
     * @return 错题分页结果
     */
    @Override
    public PageDTO<WrongQuestionVO> queryMyWrongQuestions(WrongQuestionPageQuery query) {
        Long userId = requireCurrentUser();
        String status = normalizeStatus(query.getStatus());
        Page<WrongQuestion> page = lambdaQuery()
                .eq(WrongQuestion::getUserId, userId)
                .eq(query.getCourseId() != null, WrongQuestion::getCourseId, query.getCourseId())
                .eq(status != null, WrongQuestion::getStatus, status)
                .page(query.toMpPage("last_wrong_time", false));
        if (page.getRecords().isEmpty()) {
            return PageDTO.empty(page);
        }
        List<WrongQuestionVO> records = page.getRecords().stream()
                .map(wrongQuestion -> toVO(wrongQuestion, getRequiredSnapshot(wrongQuestion)))
                .toList();
        return PageDTO.of(page, records);
    }

    /**
     * 提交一次错题重做并更新错题掌握状态
     *
     * @param id 错题记录 ID
     * @param dto 错题重做参数
     * @return 本次重做结果
     */
    @Override
    @Transactional
    public WrongQuestionReviewVO review(Long id, WrongQuestionReviewDTO dto) {
        WrongQuestion wrongQuestion = lockOwnedWrongQuestion(id);
        PracticeQuestion snapshot = getRequiredSnapshot(wrongQuestion);
        if (!ObjectiveAnswerGrader.supports(snapshot.getType())) {
            throw new IllegalStateException("错题记录关联的题目不是客观题");
        }

        String studentAnswer = dto.getAnswer() == null ? "" : dto.getAnswer().trim();
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.grade(
                snapshot.getType(), snapshot.getStandardAnswer(), studentAnswer, snapshot.getScore());
        LocalDateTime now = LocalDateTime.now();
        WrongQuestionReview review = new WrongQuestionReview()
                .setWrongQuestionId(wrongQuestion.getId())
                .setUserId(wrongQuestion.getUserId())
                .setStudentAnswer(studentAnswer)
                .setCorrect(result.correct())
                .setScore(result.score())
                .setReviewTime(now);
        if (reviewMapper.insert(review) != 1) {
            throw new IllegalStateException("保存错题重做记录失败");
        }

        wrongQuestion.setReviewCount(defaultZero(wrongQuestion.getReviewCount()) + 1)
                .setLastReviewTime(now)
                .setStatus(result.correct()
                        ? WrongQuestionStatus.MASTERED.name()
                        : WrongQuestionStatus.ACTIVE.name())
                .setMasteredTime(result.correct() ? now : null)
                .setUpdateTime(now);
        if (!updateById(wrongQuestion)) {
            throw new IllegalStateException("更新错题掌握状态失败");
        }
        return BeanUtils.toBean(review, WrongQuestionReviewVO.class);
    }

    /**
     * 查询当前学生指定错题的全部重做记录
     *
     * @param id 错题记录 ID
     * @return 重做记录列表
     */
    @Override
    public List<WrongQuestionReviewVO> queryReviews(Long id) {
        WrongQuestion wrongQuestion = getRequiredOwned(id);
        List<WrongQuestionReview> reviews = reviewMapper.selectList(
                Wrappers.<WrongQuestionReview>lambdaQuery()
                        .eq(WrongQuestionReview::getWrongQuestionId, wrongQuestion.getId())
                        .eq(WrongQuestionReview::getUserId, wrongQuestion.getUserId())
                        .orderByDesc(WrongQuestionReview::getReviewTime));
        return BeanUtils.copyList(reviews, WrongQuestionReviewVO.class);
    }

    /**
     * 新增错题或累加同一学生、课程、目录和题目维度的错题次数
     *
     * @param userId 学生用户 ID
     * @param courseId 课程 ID
     * @param targetBizId 练习目录 ID
     * @param practiceQuestionId 最近练习题快照 ID
     * @param questionId 正式题目 ID
     * @param answerId 最近练习答案 ID
     */
    @Override
    public void recordWrongQuestion(Long userId, Long courseId, Long targetBizId,
                                    Long practiceQuestionId, Long questionId, Long answerId) {
        if (userId == null || courseId == null || targetBizId == null
                || practiceQuestionId == null || questionId == null || answerId == null) {
            throw new IllegalArgumentException("记录错题所需的关联信息不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        WrongQuestion wrongQuestion = new WrongQuestion()
                .setId(IdWorker.getId())
                .setUserId(userId)
                .setQuestionId(questionId)
                .setCourseId(courseId)
                .setTargetBizId(targetBizId)
                .setLatestPracticeQuestionId(practiceQuestionId)
                .setLatestAnswerId(answerId)
                .setLastWrongTime(now)
                .setCreateTime(now)
                .setUpdateTime(now);
        int affectedRows = baseMapper.upsertWrongQuestion(wrongQuestion);
        if (affectedRows <= 0) {
            throw new DbException("错题记录保存失败");
        }
    }

    /**
     * 查询并校验当前学生拥有指定错题记录
     *
     * @param id 错题记录 ID
     * @return 错题记录
     */
    private WrongQuestion getRequiredOwned(Long id) {
        WrongQuestion wrongQuestion = getById(id);
        if (wrongQuestion == null) {
            throw new BadRequestException("错题记录不存在");
        }
        if (!requireCurrentUser().equals(wrongQuestion.getUserId())) {
            throw new BadRequestException("无权访问该错题记录");
        }
        return wrongQuestion;
    }

    /**
     * 锁定并校验当前学生拥有指定错题记录
     *
     * @param id 错题记录 ID
     * @return 已锁定的错题记录
     */
    private WrongQuestion lockOwnedWrongQuestion(Long id) {
        getRequiredOwned(id);
        if (baseMapper.lockById(id) == null) {
            throw new BadRequestException("错题记录不存在");
        }
        return getRequiredOwned(id);
    }

    /**
     * 查询并校验错题关联的练习快照、会话和最近一次错误答案
     *
     * @param wrongQuestion 错题记录
     * @return 已通过关联关系校验的练习题快照
     */
    private PracticeQuestion getRequiredSnapshot(WrongQuestion wrongQuestion) {
        PracticeQuestion snapshot = practiceQuestionMapper.selectById(
                wrongQuestion.getLatestPracticeQuestionId());
        if (snapshot == null || !wrongQuestion.getQuestionId().equals(snapshot.getQuestionId())) {
            throw new BadRequestException("错题关联的练习题快照不存在或题目不匹配");
        }
        if (snapshot.getSessionId() == null) {
            throw new BadRequestException("错题关联的练习会话不存在");
        }

        PracticeSession session = practiceSessionMapper.selectById(snapshot.getSessionId());
        if (session == null
                || !wrongQuestion.getUserId().equals(session.getUserId())
                || !wrongQuestion.getCourseId().equals(session.getCourseId())
                || !wrongQuestion.getTargetBizId().equals(session.getTargetBizId())) {
            throw new BadRequestException("错题关联的练习会话信息不匹配");
        }

        PracticeAnswer answer = practiceAnswerMapper.selectById(wrongQuestion.getLatestAnswerId());
        if (answer == null
                || !snapshot.getId().equals(answer.getPracticeQuestionId())
                || !snapshot.getQuestionId().equals(answer.getQuestionId())
                || !snapshot.getSessionId().equals(answer.getSessionId())
                || !wrongQuestion.getUserId().equals(answer.getUserId())) {
            throw new BadRequestException("错题关联的最近一次答案不存在或信息不匹配");
        }
        return snapshot;
    }

    /**
     * 将错题实体和题目快照转换为展示对象
     *
     * @param wrongQuestion 错题记录
     * @param snapshot 最近练习题快照
     * @return 错题展示对象
     */
    private WrongQuestionVO toVO(WrongQuestion wrongQuestion, PracticeQuestion snapshot) {
        if (snapshot == null) {
            throw new IllegalStateException("错题关联的练习题快照不存在");
        }
        WrongQuestionVO vo = BeanUtils.toBean(wrongQuestion, WrongQuestionVO.class);
        vo.setName(snapshot.getName());
        vo.setType(snapshot.getType());
        vo.setDifficulty(snapshot.getDifficulty());
        vo.setScore(snapshot.getScore());
        vo.setOptions(snapshot.getOptions());
        vo.setStandardAnswer(snapshot.getStandardAnswer());
        vo.setAnalysis(snapshot.getAnalysis());
        return vo;
    }

    /**
     * 校验并规范化错题状态筛选条件
     *
     * @param status 原始状态
     * @return 规范化状态；未传时返回 null
     */
    private String normalizeStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        for (WrongQuestionStatus value : WrongQuestionStatus.values()) {
            if (value.name().equals(normalized)) {
                return normalized;
            }
        }
        throw new BadRequestException("错题状态仅支持 ACTIVE 或 MASTERED");
    }

    /**
     * 获取当前登录学生用户 ID
     *
     * @return 当前登录学生用户 ID
     */
    private Long requireCurrentUser() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("当前未登录，无法访问错题记录");
        }
        return userId;
    }

    /**
     * 将可能为空的整数转换为零
     *
     * @param value 原始值
     * @return 非空整数
     */
    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }
}

