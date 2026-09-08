package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.constants.QuestionType;
import com.tianji.exam.domain.dto.PracticeAnswerSaveDTO;
import com.tianji.exam.domain.dto.PracticeAiReviewConfirmDTO;
import com.tianji.exam.domain.dto.PracticeSessionCreateDTO;
import com.tianji.exam.domain.event.PracticeSubjectiveReviewRequestedEvent;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeQuestion;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.domain.po.Question;
import com.tianji.exam.domain.po.QuestionBiz;
import com.tianji.exam.domain.po.QuestionDetail;
import com.tianji.exam.domain.query.PracticeAiReviewPageQuery;
import com.tianji.exam.domain.query.PracticeSessionPageQuery;
import com.tianji.exam.domain.vo.PracticeAiReviewItemVO;
import com.tianji.exam.domain.vo.PracticeQuestionVO;
import com.tianji.exam.domain.vo.PracticeSessionVO;
import com.tianji.exam.enums.GradingMethod;
import com.tianji.exam.enums.PracticeAnswerStatus;
import com.tianji.exam.enums.PracticeSessionStatus;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.mapper.QuestionBizMapper;
import com.tianji.exam.mapper.QuestionDetailMapper;
import com.tianji.exam.mapper.QuestionMapper;
import com.tianji.exam.service.IPracticeSessionService;
import com.tianji.exam.service.IWrongQuestionService;
import com.tianji.exam.utils.ObjectiveAnswerGrader;
import lombok.RequiredArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生练习会话服务实现
 */
@Service
@RequiredArgsConstructor
public class PracticeSessionServiceImpl extends ServiceImpl<PracticeSessionMapper, PracticeSession>
        implements IPracticeSessionService {
    private static final int CATALOGUE_PRACTICE = 3;
    /** 旧版学习页将小节练习题直接绑定到视频小节目录。 */
    private static final int CATALOGUE_SECTION = 2;
    private static final int DEFAULT_AI_REVIEW_MAX_RETRY_COUNT = 3;
    private static final String AI_RUBRIC_VERSION = "subjective-rubric-v1";
    private static final String AI_DISCLAIMER = "主观题结果由 AI 提供评估建议，不代表正式考试成绩。";
    private final CatalogueClient catalogueClient;
    private final CourseClient courseClient;
    private final LearningClient learningClient;
    private final QuestionBizMapper questionBizMapper;
    private final QuestionMapper questionMapper;
    private final QuestionDetailMapper questionDetailMapper;
    private final PracticeQuestionMapper practiceQuestionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final IWrongQuestionService wrongQuestionService;
    private final ApplicationEventPublisher eventPublisher;


    /**
     * 创建练习会话，并初始化题目快照与作答记录
     */
    @Override
    @Transactional
    public Long createSession(PracticeSessionCreateDTO dto) {
        Long userId = requireCurrentUser();
        String requestId = trimToNull(dto.getRequestId());
        PracticeSession existing = queryByRequestId(userId, requestId);
        if (existing != null) {
            validateExistingRequest(existing, dto);
            return existing.getId();
        }
        validateLearningQualification(dto.getCourseId());
        CatalogueDetailDTO target = catalogueClient.queryCatalogueDetail(dto.getTargetBizId());
        validateTarget(dto, target);
        List<QuestionBiz> relations = deduplicateRelations(questionBizMapper.selectList(
                Wrappers.<QuestionBiz>lambdaQuery()
                        .eq(QuestionBiz::getBizId, dto.getTargetBizId())
                        .orderByAsc(QuestionBiz::getId)));
        if (CollUtils.isEmpty(relations)) {
            throw new BadRequestException("该练习目录暂未关联可练习题目");
        }
        List<Long> questionIds = relations.stream().map(QuestionBiz::getQuestionId).toList();
        Map<Long, Question> questionMap = questionMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));
        Map<Long, QuestionDetail> detailMap = questionDetailMapper.selectBatchIds(questionIds).stream()
                .collect(Collectors.toMap(QuestionDetail::getId, Function.identity()));
        List<PracticeQuestion> snapshots = createQuestionSnapshots(relations, questionMap, detailMap);
        LocalDateTime now = LocalDateTime.now();
        PracticeSession session = createSessionEntity(dto, target, userId, requestId, snapshots, now);
        if (!saveSession(session, requestId)) {
            PracticeSession concurrent = queryByRequestId(userId, requestId);
            if (concurrent != null) {
                validateExistingRequest(concurrent, dto);
                return concurrent.getId();
            }
            throw new DbException("练习会话创建失败");
        }
        saveQuestionsAndAnswers(session.getId(), userId, snapshots, now);
        return session.getId();
    }


    /**
     * 查询指定练习会话详情
     */
    @Override
    public PracticeSessionVO querySession(Long id) {
        return toVO(getRequiredOwned(id), true);
    }


    /**
     * 分页查询当前用户的练习会话
     */
    @Override
    public PageDTO<PracticeSessionVO> queryMySessions(PracticeSessionPageQuery query) {
        Long userId = requireCurrentUser();
        String status = normalizeSessionStatus(query.getStatus());
        Page<PracticeSession> page = lambdaQuery()
                .eq(PracticeSession::getUserId, userId)
                .eq(query.getCourseId() != null, PracticeSession::getCourseId, query.getCourseId())
                .eq(query.getTargetBizId() != null, PracticeSession::getTargetBizId, query.getTargetBizId())
                .eq(status != null, PracticeSession::getStatus, status)
                .page(query.toMpPage("create_time", false));
        return PageDTO.of(page, session -> toVO(session, false));
    }

    /**
     * 分页查询课程创建者待审核的主观题 AI 评估结果
     *
     * @param query 待审核项查询参数
     * @return 待审核项分页结果
     */
    @Override
    public PageDTO<PracticeAiReviewItemVO> queryPendingAiReviews(PracticeAiReviewPageQuery query) {
        Long reviewerId = requireCurrentUser();
        validateReviewConfidenceRange(query);
        validateCourseReviewer(query.getCourseId(), reviewerId);
        Page<PracticeAiReviewItemVO> page = practiceAnswerMapper.selectPendingAiReviewPage(
                query.toMpPage(), query);
        return PageDTO.of(page);
    }



    /**
     * 保存当前用户对练习题的作答内容
     */
    @Override
    @Transactional
    public void saveAnswer(Long sessionId, Long practiceQuestionId, PracticeAnswerSaveDTO dto) {
        PracticeSession session = lockOwnedSession(sessionId);
        if (!PracticeSessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BadRequestException("练习已提交，不能再修改答案");
        }
        getRequiredQuestion(sessionId, practiceQuestionId);
        String answerText = dto.getAnswer() == null ? "" : dto.getAnswer().trim();
        LocalDateTime now = LocalDateTime.now();
        int updated = practiceAnswerMapper.update(null, Wrappers.<PracticeAnswer>lambdaUpdate()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .eq(PracticeAnswer::getPracticeQuestionId, practiceQuestionId)
                .in(PracticeAnswer::getStatus,
                        PracticeAnswerStatus.UNANSWERED.name(), PracticeAnswerStatus.SAVED.name())
                .set(PracticeAnswer::getStudentAnswer, answerText)
                .set(PracticeAnswer::getStatus, answerText.isBlank()
                        ? PracticeAnswerStatus.UNANSWERED.name() : PracticeAnswerStatus.SAVED.name())
                .set(PracticeAnswer::getScore, null)
                .set(PracticeAnswer::getCorrect, null)
                .set(PracticeAnswer::getAiSuggestedScore, null)
                .set(PracticeAnswer::getAiTotalScore, null)
                .set(PracticeAnswer::getMatchedPoints, null)
                .set(PracticeAnswer::getMissingPoints, null)
                .set(PracticeAnswer::getIncorrectStatements, null)
                .set(PracticeAnswer::getImprovementSuggestion, null)
                .set(PracticeAnswer::getConfidence, null)
                .set(PracticeAnswer::getManualReviewRecommended, null)
                .set(PracticeAnswer::getRubricVersion, null)
                .set(PracticeAnswer::getAiFailureReason, null)
                .set(PracticeAnswer::getAnswerTime, answerText.isBlank() ? null : now)
                .set(PracticeAnswer::getEvaluatedTime, null)
                .set(PracticeAnswer::getUpdateTime, now));
        if (updated != 1) {
            throw new BadRequestException("练习答案状态已变化，请刷新后重试");
        }
        refreshAnsweredCount(sessionId, now);
    }


    /**
     * 提交练习会话并完成客观题判分
     */
    @Override
    @Transactional
    public PracticeSessionVO submit(Long sessionId) {
        PracticeSession session = lockOwnedSession(sessionId);
        if (!PracticeSessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            return toVO(session, true);
        }
        List<PracticeQuestion> questions = queryQuestions(sessionId);
        Map<Long, PracticeAnswer> answerMap = queryAnswers(sessionId).stream()
                .collect(Collectors.toMap(PracticeAnswer::getPracticeQuestionId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        int answeredCount = 0;
        int objectiveScore = 0;
        int correctCount = 0;
        int subjectivePending = 0;
        int pendingAiReview = 0;
        for (PracticeQuestion question : questions) {
            PracticeAnswer answer = answerMap.get(question.getId());
            if (answer == null) {
                throw new IllegalStateException("练习题快照缺少对应的学生答案记录");
            }
            boolean answered = StringUtils.isNotBlank((CharSequence)answer.getStudentAnswer());
            if (answered) {
                ++answeredCount;
            }
            if (ObjectiveAnswerGrader.supports(question.getType())) {
                ObjectiveAnswerGrader.GradeResult result = gradeObjectiveQuestion(question, answer, now);
                objectiveScore += result.score();
                if (result.correct()) {
                    ++correctCount;
                } else {
                    wrongQuestionService.recordWrongQuestion(session.getUserId(), session.getCourseId(), session.getTargetBizId(), question.getId(), question.getQuestionId(), answer.getId());
                }
            } else if (answered) {
                markPendingAiReview(answer, now);
                ++subjectivePending;
                ++pendingAiReview;
            } else {
                markUnansweredSubjective(answer, question, now);
                ++subjectivePending;
            }
            if (this.practiceAnswerMapper.updateById(answer) == 1) continue;
            throw new DbException("更新练习答案判分结果失败");
        }
        completeSubmission(session, answeredCount, objectiveScore, correctCount, subjectivePending, now);
        if (pendingAiReview > 0) {
            eventPublisher.publishEvent(new PracticeSubjectiveReviewRequestedEvent(sessionId, session.getUserId(), 0));
        }
        return toVO(session, true);
    }


    /**
     * 重试练习会话中的主观题 AI 评估
     */
    @Override
    @Transactional
    public void retryAiReview(Long sessionId) {
        PracticeSession session = lockOwnedSession(sessionId);
        if (PracticeSessionStatus.IN_PROGRESS.name().equals(session.getStatus())) {
            throw new BadRequestException("练习尚未提交，不能重试 AI 评估");
        }
        int retryCount = session.getAiReviewRetryCount() == null ? 0 : session.getAiReviewRetryCount();
        int maxRetryCount = session.getAiReviewMaxRetryCount() == null
                ? DEFAULT_AI_REVIEW_MAX_RETRY_COUNT
                : session.getAiReviewMaxRetryCount();
        if (retryCount >= maxRetryCount) {
            throw new BadRequestException("主观题 AI 评估已达到最大重试次数");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = practiceAnswerMapper.update(null, Wrappers.<PracticeAnswer>lambdaUpdate()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .eq(PracticeAnswer::getStatus, PracticeAnswerStatus.AI_REVIEW_FAILED.name())
                .set(PracticeAnswer::getStatus, PracticeAnswerStatus.PENDING_AI_REVIEW.name())
                .set(PracticeAnswer::getAiFailureReason, null)
                .set(PracticeAnswer::getEvaluatedTime, null)
                .set(PracticeAnswer::getUpdateTime, now));
        if (updated == 0) {
            throw new BadRequestException("当前练习没有可重试的 AI 评估题目");
        }
        int pendingCount = practiceAnswerMapper.countUnfinishedAiReview(sessionId)
                + practiceAnswerMapper.countAiReviewed(sessionId)
                + practiceAnswerMapper.countAiReviewFailed(sessionId);
        session.setStatus(PracticeSessionStatus.SUBMITTED.name())
                .setAiReviewPendingCount(pendingCount)
                .setAiReviewRetryCount(retryCount + 1)
                .setCompletedTime(null)
                .setUpdateTime(now);
        if (!updateById(session)) {
            throw new DbException("更新练习会话状态失败");
        }
        eventPublisher.publishEvent(new PracticeSubjectiveReviewRequestedEvent(
                sessionId, session.getUserId(), retryCount + 1));
    }


    /**
     * 由课程创建者确认主观题 AI 评估结果并写入正式分数
     *
     * @param sessionId 练习会话 ID
     * @param dto 人工确认参数
     */
    @Override
    @Transactional
    public void confirmAiReview(Long sessionId, PracticeAiReviewConfirmDTO dto) {
        Long reviewerId = requireCurrentUser();
        if (dto == null || dto.getAnswerId() == null || dto.getFinalScore() == null) {
            throw new BadRequestException("人工确认参数不能为空");
        }
        PracticeSession session = getRequiredSession(sessionId);
        validateCourseReviewer(session.getCourseId(), reviewerId);
        session = lockSession(sessionId);
        PracticeAnswer answer = practiceAnswerMapper.selectOne(Wrappers.<PracticeAnswer>lambdaQuery()
                .eq(PracticeAnswer::getId, dto.getAnswerId())
                .eq(PracticeAnswer::getSessionId, sessionId)
                .eq(PracticeAnswer::getUserId, session.getUserId()));
        if (answer == null) {
            throw new BadRequestException("练习答案不存在或不属于当前练习");
        }
        if (!PracticeAnswerStatus.AI_REVIEWED.name().equals(answer.getStatus())
                && !PracticeAnswerStatus.MANUALLY_REVIEWED.name().equals(answer.getStatus())) {
            throw new BadRequestException("只有 AI 评估完成的主观题才能人工确认");
        }
        PracticeQuestion question = practiceQuestionMapper.selectById(answer.getPracticeQuestionId());
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new BadRequestException("练习题快照不存在");
        }
        if (ObjectiveAnswerGrader.supports(question.getType())) {
            throw new BadRequestException("客观题不支持主观题 AI 人工确认");
        }
        if (dto.getFinalScore() < 0) {
            throw new BadRequestException("最终得分不能小于 0");
        }
        if (dto.getFinalScore() > question.getScore()) {
            throw new BadRequestException("最终得分不能超过题目总分");
        }
        LocalDateTime now = LocalDateTime.now();
        answer.setStatus(PracticeAnswerStatus.MANUALLY_REVIEWED.name())
                .setScore(dto.getFinalScore())
                .setCorrect(null)
                .setEvaluatedTime(now)
                .setUpdateTime(now);
        if (practiceAnswerMapper.updateById(answer) != 1) {
            throw new IllegalStateException("保存人工确认分数失败");
        }
        refreshSessionAfterReview(session, now);
    }

    /**
     * 人工确认答案后重新汇总正式总分、待处理数量和会话状态。
     *
     * @param session 当前已锁定的练习会话
     * @param now 当前时间
     */
    private void refreshSessionAfterReview(PracticeSession session, LocalDateTime now) {
        int unfinishedCount = practiceAnswerMapper.countUnfinishedAiReview(session.getId());
        int reviewedCount = practiceAnswerMapper.countAiReviewed(session.getId());
        int failedCount = practiceAnswerMapper.countAiReviewFailed(session.getId());
        int pendingCount = unfinishedCount + reviewedCount + failedCount;
        String status = pendingCount > 0
                ? PracticeSessionStatus.SUBMITTED.name()
                : PracticeSessionStatus.COMPLETED.name();
        session.setStatus(status)
                .setFinalScore(practiceAnswerMapper.sumScoredAnswers(session.getId()))
                .setAiReviewPendingCount(pendingCount)
                .setCompletedTime(pendingCount == 0 ? now : null)
                .setUpdateTime(now);
        if (!updateById(session)) {
            throw new IllegalStateException("更新练习会话汇总结果失败");
        }
    }
    /**
     * 校验待审核列表的置信度范围
     *
     * @param query 待审核项查询参数
     */
    private void validateReviewConfidenceRange(PracticeAiReviewPageQuery query) {
        if (query.getMinConfidence() != null && query.getMaxConfidence() != null
                && query.getMinConfidence().compareTo(query.getMaxConfidence()) > 0) {
            throw new BadRequestException("最低置信度不能大于最高置信度");
        }
    }
    /**
     * 校验当前用户是否为课程创建者
     *
     * @param courseId 课程 ID
     * @param userId 当前用户 ID
     */
    private void validateCourseReviewer(Long courseId, Long userId) {
        CourseBaseInfoDTO course = courseClient.baseInfo(courseId, true);
        if (course == null || !userId.equals(course.getCreater())) {
            throw new ForbiddenException("只有课程创建者可以查看或确认主观题 AI 评估结果");
        }
    }
    /**
     * 按题目关联顺序去重练习题关联记录
     *
     * @param relations 题目关联记录
     * @return 去重后的题目关联记录
     */
    private List<QuestionBiz> deduplicateRelations(List<QuestionBiz> relations) {
        if (CollUtils.isEmpty(relations)) {
            return List.of();
        }
        LinkedHashMap<Long, QuestionBiz> relationMap = new LinkedHashMap<>();
        for (QuestionBiz relation : relations) {
            if (relation.getQuestionId() == null) {
                continue;
            }
            relationMap.putIfAbsent(relation.getQuestionId(), relation);
        }
        return new ArrayList<>(relationMap.values());
    }


    /**
     * 根据正式题目数据创建练习题快照
     */
    private List<PracticeQuestion> createQuestionSnapshots(List<QuestionBiz> relations, Map<Long, Question> questionMap, Map<Long, QuestionDetail> detailMap) {
        List<PracticeQuestion> snapshots = new ArrayList<>(relations.size());
        int sequence = 1;
        for (QuestionBiz relation : relations) {
            Question question = questionMap.get(relation.getQuestionId());
            QuestionDetail detail = detailMap.get(relation.getQuestionId());
            validateQuestionSource(question, detail);
            snapshots.add(new PracticeQuestion()
                    .setQuestionId(question.getId())
                    .setSequenceNo(sequence++)
                    .setName(question.getName().trim())
                    .setType(question.getType())
                    .setDifficulty(question.getDifficulty())
                    .setScore(question.getScore())
                    .setOptions(detail.getOptions())
                    .setStandardAnswer(detail.getAnswer().trim())
                    .setAnalysis(trimToNull(detail.getAnalysis()))
                    .setGradingMethod(ObjectiveAnswerGrader.supports(question.getType())
                            ? GradingMethod.OBJECTIVE_RULE.name()
                            : GradingMethod.AI_SUGGESTION.name()));
        }
        return snapshots;
    }


    /**
     * 校验题目及题目详情数据完整有效
     */
    private void validateQuestionSource(Question question, QuestionDetail detail) {
        if (question == null || detail == null || QuestionType.of(question.getType()) == null) {
            throw new BadRequestException("练习目录中存在无效题目");
        }
        if (StringUtils.isBlank(question.getName()) || question.getScore() == null || question.getScore() <= 0) {
            throw new BadRequestException("练习目录中存在题干或分值无效的题目");
        }
        if (StringUtils.isBlank(detail.getAnswer())) {
            throw new BadRequestException("练习目录中存在缺少标准答案的题目");
        }
        if (ObjectiveAnswerGrader.supports(question.getType())) {
            if (question.getType() != QuestionType.JUDGE.getValue() && CollUtils.isEmpty(detail.getOptions())) {
                throw new BadRequestException("选择题必须配置选项");
            }
            if (!ObjectiveAnswerGrader.gradeWithLegacyChoiceCompatibility(
                    question.getType(), detail.getAnswer(), detail.getAnswer(), question.getScore()).correct()) {
                throw new BadRequestException("练习目录中存在格式不合法的标准答案");
            }
        }
    }


    /**
     * 构建练习会话实体
     */
    private PracticeSession createSessionEntity(PracticeSessionCreateDTO dto, CatalogueDetailDTO target, Long userId, String requestId, List<PracticeQuestion> snapshots, LocalDateTime now) {
        int objectiveCount = (int) snapshots.stream()
                .filter(question -> ObjectiveAnswerGrader.supports(question.getType()))
                .count();
        int objectiveFullScore = snapshots.stream()
                .filter(question -> ObjectiveAnswerGrader.supports(question.getType()))
                .mapToInt(PracticeQuestion::getScore)
                .sum();
        int totalScore = snapshots.stream().mapToInt(PracticeQuestion::getScore).sum();
        return new PracticeSession()
                .setId(IdWorker.getId())
                .setUserId(userId)
                .setCourseId(dto.getCourseId())
                .setTargetBizId(dto.getTargetBizId())
                .setTargetName(target.getName())
                .setStatus(PracticeSessionStatus.IN_PROGRESS.name())
                .setTotalQuestions(snapshots.size())
                .setAnsweredCount(0)
                .setObjectiveCount(objectiveCount)
                .setSubjectiveCount(snapshots.size() - objectiveCount)
                .setTotalScore(totalScore)
                .setObjectiveFullScore(objectiveFullScore)
                .setObjectiveScore(0)
                .setCorrectCount(0)
                .setAiReviewPendingCount(0)
                .setAiReviewRetryCount(0)
                .setAiReviewMaxRetryCount(DEFAULT_AI_REVIEW_MAX_RETRY_COUNT)
                .setRequestId(requestId)
                .setCreateTime(now)
                .setUpdateTime(now);
    }


    /**
     * 保存练习题快照及初始化答案记录
     */
    private void saveQuestionsAndAnswers(Long sessionId, Long userId, List<PracticeQuestion> snapshots, LocalDateTime now) {
        for (PracticeQuestion snapshot : snapshots) {
            snapshot.setSessionId(sessionId).setCreateTime(now);
            if (practiceQuestionMapper.insert(snapshot) != 1) {
                throw new DbException("保存练习题快照失败");
            }
            PracticeAnswer answer = new PracticeAnswer().setSessionId(sessionId).setPracticeQuestionId(snapshot.getId()).setQuestionId(snapshot.getQuestionId()).setUserId(userId).setStudentAnswer("").setStatus(PracticeAnswerStatus.UNANSWERED.name()).setCreateTime(now).setUpdateTime(now);
            if (this.practiceAnswerMapper.insert(answer) == 1) continue;
            throw new DbException("保存练习答案初始化记录失败");
        }
    }


    /**
     * 保存练习会话并处理幂等请求冲突
     */
    private boolean saveSession(PracticeSession session, String requestId) {
        return requestId == null ? save(session) : baseMapper.insertIgnore(session) == 1;
    }


    /**
     * 按用户和请求标识查询已有练习会话
     */
    private PracticeSession queryByRequestId(Long userId, String requestId) {
        if (requestId == null) {
            return null;
        }
        return lambdaQuery()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getRequestId, requestId)
                .one();
    }


    /**
     * 校验幂等请求对应的练习参数一致
     */
    private void validateExistingRequest(PracticeSession existing, PracticeSessionCreateDTO dto) {
        if (!Objects.equals(existing.getCourseId(), dto.getCourseId()) || !Objects.equals(existing.getTargetBizId(), dto.getTargetBizId())) {
            throw new BadRequestException("幂等请求标识已用于其他练习");
        }
    }


    /**
     * 校验当前用户是否具备课程学习资格
     */
    private void validateLearningQualification(Long courseId) {
        if (learningClient.isLessonValid(courseId) == null) {
            throw new ForbiddenException("当前用户没有该课程的学习资格");
        }
    }


    /**
     * 校验练习目标目录合法且属于指定课程
     */
    private void validateTarget(PracticeSessionCreateDTO dto, CatalogueDetailDTO target) {
        if (target == null) {
            throw new BadRequestException("练习目录不存在");
        }
        if (!dto.getCourseId().equals(target.getCourseId())) {
            throw new BadRequestException("练习目录不属于指定课程");
        }
        // 新数据使用类型 3；兼容旧课程中绑定在视频小节（类型 2）上的练习题。
        if (target.getType() == null
                || (target.getType() != CATALOGUE_PRACTICE && target.getType() != CATALOGUE_SECTION)) {
            throw new BadRequestException("仅练习或测试目录可以创建练习会话");
        }
    }


    /**
     * 判定客观题答案并构建判分结果
     */
    private ObjectiveAnswerGrader.GradeResult gradeObjectiveQuestion(PracticeQuestion question, PracticeAnswer answer, LocalDateTime now) {
        ObjectiveAnswerGrader.GradeResult result = ObjectiveAnswerGrader.gradeWithLegacyChoiceCompatibility(
                question.getType(), question.getStandardAnswer(), answer.getStudentAnswer(), question.getScore());
        answer.setStatus(PracticeAnswerStatus.OBJECTIVE_GRADED.name())
                .setScore(result.score())
                .setCorrect(result.correct())
                .setEvaluatedTime(now)
                .setUpdateTime(now);
        if (questionMapper.incrementAnswerStatistics(question.getQuestionId(), result.correct()) != 1) {
            throw new IllegalStateException("更新题目作答统计失败");
        }
        return result;
    }


    /**
     * 将已作答主观题标记为待 AI 评估
     */
    private void markPendingAiReview(PracticeAnswer answer, LocalDateTime now) {
        answer.setStatus(PracticeAnswerStatus.PENDING_AI_REVIEW.name())
                .setScore(null)
                .setCorrect(null)
                .setAiFailureReason(null)
                .setUpdateTime(now);
    }


    /**
     * 处理未作答的主观题答案
     */
    private void markUnansweredSubjective(PracticeAnswer answer, PracticeQuestion question, LocalDateTime now) {
        answer.setStatus(PracticeAnswerStatus.AI_REVIEWED.name()).setAiSuggestedScore(Integer.valueOf(0)).setAiTotalScore(question.getScore()).setMatchedPoints(List.of()).setMissingPoints(List.of("本题未作答")).setIncorrectStatements(List.of()).setImprovementSuggestion("请先完整作答，再对照参考答案检查得分点。").setConfidence(BigDecimal.ONE).setManualReviewRecommended(Boolean.valueOf(false)).setRubricVersion(AI_RUBRIC_VERSION).setEvaluatedTime(now).setUpdateTime(now);
    }


    /**
     * 汇总提交结果并更新练习会话状态
     */
    private void completeSubmission(PracticeSession session, int answeredCount, int objectiveScore, int correctCount, int subjectivePending, LocalDateTime now) {
        String status = subjectivePending > 0 ? PracticeSessionStatus.SUBMITTED.name() : PracticeSessionStatus.COMPLETED.name();
        session.setStatus(status)
                .setAnsweredCount(answeredCount)
                .setObjectiveScore(objectiveScore)
                .setFinalScore(objectiveScore)
                .setCorrectCount(correctCount)
                .setAiReviewPendingCount(subjectivePending)
                .setSubmittedTime(now)
                .setCompletedTime(subjectivePending > 0 ? null : now)
                .setUpdateTime(now);
        if (!updateById(session)) {
            throw new IllegalStateException("更新练习会话提交状态失败");
        }
    }


    /**
     * 查询指定练习会话
     *
     * @param id 练习会话 ID
     * @return 练习会话
     */
    private PracticeSession getRequiredSession(Long id) {
        PracticeSession session = getById(id);
        if (session == null) {
            throw new BadRequestException("练习会话不存在");
        }
        return session;
    }

    /**
     * 锁定并读取指定练习会话
     *
     * @param id 练习会话 ID
     * @return 已锁定的练习会话
     */
    private PracticeSession lockSession(Long id) {
        if (baseMapper.lockById(id) == null) {
            throw new BadRequestException("练习会话不存在");
        }
        return getRequiredSession(id);
    }
    /**
     * 查询并校验当前用户拥有的练习会话。
     *
     * @param id 练习会话 ID
     * @return 当前用户拥有的练习会话
     */
    private PracticeSession getRequiredOwned(Long id) {
        PracticeSession session = getById(id);
        if (session == null) {
            throw new BadRequestException("练习会话不存在");
        }
        if (!requireCurrentUser().equals(session.getUserId())) {
            throw new BadRequestException("无权访问该练习会话");
        }
        return session;
    }


    /**
     * 锁定并校验当前用户拥有的练习会话
     */
    private PracticeSession lockOwnedSession(Long id) {
        getRequiredOwned(id);
        if (baseMapper.lockById(id) == null) {
            throw new BadRequestException("练习会话不存在");
        }
        return getRequiredOwned(id);
    }


    /**
     * 查询指定练习会话中的题目快照
     */
    private PracticeQuestion getRequiredQuestion(Long sessionId, Long questionId) {
        PracticeQuestion question = practiceQuestionMapper.selectById(questionId);
        if (question == null || !sessionId.equals(question.getSessionId())) {
            throw new BadRequestException("练习题不存在或不属于当前会话");
        }
        return question;
    }


    /**
     * 查询练习会话的题目快照
     */
    private List<PracticeQuestion> queryQuestions(Long sessionId) {
        return practiceQuestionMapper.selectList(Wrappers.<PracticeQuestion>lambdaQuery()
                .eq(PracticeQuestion::getSessionId, sessionId)
                .orderByAsc(PracticeQuestion::getSequenceNo));
    }


    /**
     * 查询练习会话的作答记录
     */
    private List<PracticeAnswer> queryAnswers(Long sessionId) {
        return practiceAnswerMapper.selectList(Wrappers.<PracticeAnswer>lambdaQuery()
                .eq(PracticeAnswer::getSessionId, sessionId));
    }


    /**
     * 刷新练习会话已作答题目数量
     */
    private void refreshAnsweredCount(Long sessionId, LocalDateTime now) {
        Long count = practiceAnswerMapper.selectCount(Wrappers.<PracticeAnswer>lambdaQuery()
                .eq(PracticeAnswer::getSessionId, sessionId)
                .isNotNull(PracticeAnswer::getStudentAnswer)
                .apply("TRIM(student_answer) <> ''"));
        update(Wrappers.<PracticeSession>lambdaUpdate()
                .eq(PracticeSession::getId, sessionId)
                .set(PracticeSession::getAnsweredCount, count == null ? 0 : count.intValue())
                .set(PracticeSession::getUpdateTime, now));
    }


    /**
     * 将练习会话实体转换为展示对象
     */
    private PracticeSessionVO toVO(PracticeSession session, boolean withQuestions) {
        PracticeSessionVO vo = BeanUtils.toBean(session, PracticeSessionVO.class);
        int objectiveCount = session.getObjectiveCount() == null ? 0 : session.getObjectiveCount();
        int correctCount = session.getCorrectCount() == null ? 0 : session.getCorrectCount();
        vo.setObjectiveAccuracy(objectiveCount == 0 ? 0.0 : (double) correctCount * 100.0 / objectiveCount);
        if (session.getSubjectiveCount() != null && session.getSubjectiveCount() > 0) {
            vo.setAiDisclaimer(AI_DISCLAIMER);
        }
        if (!withQuestions) {
            return vo;
        }
        boolean submitted = !PracticeSessionStatus.IN_PROGRESS.name().equals(session.getStatus());
        Map<Long, PracticeAnswer> answerMap = queryAnswers(session.getId()).stream()
                .collect(Collectors.toMap(PracticeAnswer::getPracticeQuestionId, Function.identity()));
        List<PracticeQuestionVO> questions = queryQuestions(session.getId()).stream()
                .sorted(Comparator.comparing(PracticeQuestion::getSequenceNo))
                .map(question -> toQuestionVO(question, answerMap.get(question.getId()), submitted))
                .toList();
        vo.setQuestions(questions);
        return vo;
    }


    /**
     * 将练习题快照和作答记录转换为展示对象
     */
    private PracticeQuestionVO toQuestionVO(PracticeQuestion question, PracticeAnswer answer, boolean submitted) {
        PracticeQuestionVO vo = BeanUtils.toBean(question, PracticeQuestionVO.class);
        if (!submitted) {
            vo.setStandardAnswer(null);
            vo.setAnalysis(null);
        }
        if (answer != null) {
            vo.setStudentAnswer(answer.getStudentAnswer());
            vo.setAnswerStatus(answer.getStatus());
            vo.setActualScore(answer.getScore());
            vo.setCorrect(answer.getCorrect());
            vo.setAiSuggestedScore(answer.getAiSuggestedScore());
            vo.setMatchedPoints(answer.getMatchedPoints());
            vo.setMissingPoints(answer.getMissingPoints());
            vo.setIncorrectStatements(answer.getIncorrectStatements());
            vo.setImprovementSuggestion(answer.getImprovementSuggestion());
            vo.setConfidence(answer.getConfidence());
            vo.setManualReviewRecommended(answer.getManualReviewRecommended());
            vo.setRubricVersion(answer.getRubricVersion());
            vo.setAiFailureReason(answer.getAiFailureReason());
        }
        return vo;
    }


    /**
     * 校验并规范化练习会话状态筛选条件
     *
     * @param status 原始状态
     * @return 规范化状态；未传时返回 null
     */
    private String normalizeSessionStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        for (PracticeSessionStatus value : PracticeSessionStatus.values()) {
            if (value.name().equals(normalized)) {
                return normalized;
            }
        }
        throw new BadRequestException("练习会话状态不合法，仅支持 IN_PROGRESS、SUBMITTED 或 COMPLETED");
    }


    /**
     * 获取当前登录用户并校验登录状态
     */
    private Long requireCurrentUser() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0L) {
            throw new UnauthorizedException("当前未登录，无法进行练习操作");
        }
        return userId;
    }


    /**
     * 清理字符串并将空白内容转换为空值
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    }



