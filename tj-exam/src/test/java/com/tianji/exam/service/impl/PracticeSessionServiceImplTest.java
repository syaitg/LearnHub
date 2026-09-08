package com.tianji.exam.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.learning.LearningClient;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.exceptions.UnauthorizedException;
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
import com.tianji.exam.domain.vo.PracticeAiReviewItemVO;
import com.tianji.exam.domain.vo.PracticeSessionVO;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.mapper.QuestionBizMapper;
import com.tianji.exam.mapper.QuestionDetailMapper;
import com.tianji.exam.mapper.QuestionMapper;
import com.tianji.exam.service.IWrongQuestionService;
import com.tianji.exam.enums.PracticeAnswerStatus;
import com.tianji.exam.enums.PracticeSessionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生练习会话服务测试
 */
@ExtendWith(MockitoExtension.class)
class PracticeSessionServiceImplTest {

    @Mock
    private CatalogueClient catalogueClient;
    @Mock
    private CourseClient courseClient;
    @Mock
    private LearningClient learningClient;
    @Mock
    private QuestionBizMapper questionBizMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private QuestionDetailMapper questionDetailMapper;
    @Mock
    private PracticeQuestionMapper practiceQuestionMapper;
    @Mock
    private PracticeAnswerMapper practiceAnswerMapper;
    @Mock
    private PracticeSessionMapper practiceSessionMapper;
    @Mock
    private IWrongQuestionService wrongQuestionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PracticeSessionServiceImpl practiceSessionService;

    /**
     * 创建待测试服务并注入 MyBatis-Plus 基础 Mapper
     */
    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(PracticeAnswerMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        practiceSessionService = new PracticeSessionServiceImpl(
                catalogueClient, courseClient, learningClient, questionBizMapper, questionMapper, questionDetailMapper,
                practiceQuestionMapper, practiceAnswerMapper, wrongQuestionService, eventPublisher);
        ReflectionTestUtils.setField(practiceSessionService, "baseMapper", practiceSessionMapper);
        ReflectionTestUtils.setField(practiceSessionService, "entityClass", PracticeSession.class);
        ReflectionTestUtils.setField(practiceSessionService, "mapperClass", PracticeSessionMapper.class);
    }

    /**
     * 每个测试结束后清理线程中的用户上下文
     */
    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    /**
     * 验证未登录学生不能创建练习会话
     */
    @Test
    @DisplayName("未登录时创建练习会话应被拒绝")
    void shouldRejectCreateSessionWhenUserIsNotLoggedIn() {
        assertThatThrownBy(() -> practiceSessionService.createSession(createRequest(null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("未登录");

        verify(catalogueClient, never()).queryCatalogueDetail(anyLong());
    }

    /**
     * 验证非练习目录不能创建学生练习会话
     */
    @Test
    @DisplayName("非练习目录创建会话应被拒绝")
    void shouldRejectNonPracticeCatalogue() {
        UserContext.setUser(10L);
        when(learningClient.isLessonValid(20L)).thenReturn(500L);
        CatalogueDetailDTO catalogue = new CatalogueDetailDTO();
        catalogue.setId(30L);
        catalogue.setCourseId(20L);
        catalogue.setType(1);
        catalogue.setName("章节目录");
        when(catalogueClient.queryCatalogueDetail(30L)).thenReturn(catalogue);

        assertThatThrownBy(() -> practiceSessionService.createSession(createRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("仅练习或测试目录");

        verify(questionBizMapper, never()).selectList(any(Wrapper.class));
    }

    /**
     * 验证旧课程绑定在视频小节上的练习可以进入会话创建流程
     */
    @Test
    @DisplayName("旧版视频小节练习应允许创建会话")
    void shouldAllowLegacySectionPractice() {
        UserContext.setUser(10L);
        when(learningClient.isLessonValid(20L)).thenReturn(500L);
        CatalogueDetailDTO catalogue = new CatalogueDetailDTO();
        catalogue.setId(30L);
        catalogue.setCourseId(20L);
        catalogue.setType(2);
        catalogue.setName("视频小节练习");
        when(catalogueClient.queryCatalogueDetail(30L)).thenReturn(catalogue);
        when(questionBizMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> practiceSessionService.createSession(createRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("暂未关联可练习题目");
    }

    /**
     * 验证创建会话时会保存题目快照和初始答案
     */
    @Test
    @DisplayName("创建练习会话应保存题目快照")
    void shouldCreateQuestionSnapshotsAndAnswers() {
        UserContext.setUser(10L);
        prepareCreateData();
        when(practiceSessionMapper.insert(any(PracticeSession.class))).thenReturn(1);
        when(practiceQuestionMapper.insert(any(PracticeQuestion.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PracticeQuestion.class).setId(100L);
            return 1;
        });
        when(practiceAnswerMapper.insert(any(PracticeAnswer.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, PracticeAnswer.class).setId(200L);
            return 1;
        });

        Long sessionId = practiceSessionService.createSession(createRequest(null));

        assertThat(sessionId).isNotNull();
        ArgumentCaptor<PracticeQuestion> questionCaptor = ArgumentCaptor.forClass(PracticeQuestion.class);
        verify(practiceQuestionMapper).insert(questionCaptor.capture());
        PracticeQuestion snapshot = questionCaptor.getValue();
        assertThat(snapshot.getSessionId()).isEqualTo(sessionId);
        assertThat(snapshot.getQuestionId()).isEqualTo(50L);
        assertThat(snapshot.getStandardAnswer()).isEqualTo("0");
        assertThat(snapshot.getGradingMethod()).isEqualTo("OBJECTIVE_RULE");
        ArgumentCaptor<PracticeAnswer> answerCaptor = ArgumentCaptor.forClass(PracticeAnswer.class);
        verify(practiceAnswerMapper).insert(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getPracticeQuestionId()).isEqualTo(100L);
        assertThat(answerCaptor.getValue().getStatus()).isEqualTo(PracticeAnswerStatus.UNANSWERED.name());
    }

    /**
     * 验证相同学生使用相同幂等键时直接返回原会话
     */
    @Test
    @DisplayName("相同 requestId 应返回已有会话")
    void shouldReturnExistingSessionForSameRequestId() {
        UserContext.setUser(10L);
        PracticeSession existing = new PracticeSession().setId(999L).setUserId(10L)
                .setCourseId(20L).setTargetBizId(30L).setRequestId("req-1");
        when(practiceSessionMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        PracticeSessionCreateDTO request = createRequest("req-1");

        Long sessionId = practiceSessionService.createSession(request);

        assertThat(sessionId).isEqualTo(999L);
        verify(catalogueClient, never()).queryCatalogueDetail(anyLong());
        verify(practiceSessionMapper, never()).insert(any(PracticeSession.class));
    }

    /**
     * 验证同一幂等键不能用于不同的课程或练习目录
     */
    @Test
    @DisplayName("相同 requestId 用于其他练习应被拒绝")
    void shouldRejectReusedRequestIdForDifferentPractice() {
        UserContext.setUser(10L);
        PracticeSession existing = new PracticeSession().setId(999L).setUserId(10L)
                .setCourseId(21L).setTargetBizId(31L).setRequestId("req-1");
        when(practiceSessionMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> practiceSessionService.createSession(createRequest("req-1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("已用于其他练习");

        verify(learningClient, never()).isLessonValid(anyLong());
        verify(catalogueClient, never()).queryCatalogueDetail(anyLong());
    }

    /**
     * 验证没有课程学习资格的学生不能创建练习会话
     */
    @Test
    @DisplayName("没有课程学习资格时创建练习应被拒绝")
    void shouldRejectCreateSessionWithoutLearningQualification() {
        UserContext.setUser(10L);
        when(learningClient.isLessonValid(20L)).thenReturn(null);

        assertThatThrownBy(() -> practiceSessionService.createSession(createRequest(null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("学习资格");

        verify(catalogueClient, never()).queryCatalogueDetail(anyLong());
    }

    /**
     * 验证未提交练习详情不会返回标准答案和解析
     */
    @Test
    @DisplayName("未提交练习详情应隐藏标准答案")
    void shouldHideStandardAnswerBeforeSubmit() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.IN_PROGRESS.name());
        PracticeQuestion question = snapshot(100L, QuestionType.RADIO.getValue(), "1");
        PracticeAnswer answer = new PracticeAnswer().setPracticeQuestionId(100L)
                .setStudentAnswer("").setStatus(PracticeAnswerStatus.UNANSWERED.name());
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question));
        when(practiceAnswerMapper.selectList(any(Wrapper.class))).thenReturn(List.of(answer));

        PracticeSessionVO result = practiceSessionService.querySession(1L);

        assertThat(result.getQuestions()).hasSize(1);
        assertThat(result.getQuestions().get(0).getStandardAnswer()).isNull();
        assertThat(result.getQuestions().get(0).getAnalysis()).isNull();
    }

    /**
     * 验证未作答客观题提交后按错误和零分处理，并记录错题
     */
    @Test
    @DisplayName("未作答客观题提交后应得零分")
    void shouldGiveZeroAndRecordWrongQuestionForUnansweredObjectiveQuestion() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.IN_PROGRESS.name());
        PracticeQuestion question = snapshot(100L, QuestionType.RADIO.getValue(), "1");
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L)
                .setPracticeQuestionId(100L).setQuestionId(50L).setStudentAnswer("")
                .setStatus(PracticeAnswerStatus.UNANSWERED.name());
        prepareSubmit(session, question, answer);
        when(questionMapper.incrementAnswerStatistics(50L, false)).thenReturn(1);
        when(practiceAnswerMapper.updateById(any(PracticeAnswer.class))).thenReturn(1);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.submit(1L);

        ArgumentCaptor<PracticeAnswer> answerCaptor = ArgumentCaptor.forClass(PracticeAnswer.class);
        verify(practiceAnswerMapper).updateById(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getStatus()).isEqualTo(PracticeAnswerStatus.OBJECTIVE_GRADED.name());
        assertThat(answerCaptor.getValue().getScore()).isZero();
        assertThat(answerCaptor.getValue().getCorrect()).isFalse();
        verify(wrongQuestionService).recordWrongQuestion(10L, 20L, 30L, 100L, 50L, 200L);
        verify(eventPublisher, never()).publishEvent(any(PracticeSubjectiveReviewRequestedEvent.class));
    }

    /**
     * 验证已作答客观题提交后会完成规则判分
     */
    @Test
    @DisplayName("客观题提交后应完成自动判分")
    void shouldGradeObjectiveQuestionOnSubmit() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.IN_PROGRESS.name());
        PracticeQuestion question = snapshot(100L, QuestionType.RADIO.getValue(), "1");
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L)
                .setPracticeQuestionId(100L).setQuestionId(50L).setStudentAnswer("1")
                .setStatus(PracticeAnswerStatus.SAVED.name());
        prepareSubmit(session, question, answer);
        when(questionMapper.incrementAnswerStatistics(50L, true)).thenReturn(1);
        when(practiceAnswerMapper.updateById(any(PracticeAnswer.class))).thenReturn(1);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.submit(1L);

        ArgumentCaptor<PracticeAnswer> answerCaptor = ArgumentCaptor.forClass(PracticeAnswer.class);
        verify(practiceAnswerMapper).updateById(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getStatus()).isEqualTo(PracticeAnswerStatus.OBJECTIVE_GRADED.name());
        assertThat(answerCaptor.getValue().getScore()).isEqualTo(5);
        assertThat(answerCaptor.getValue().getCorrect()).isTrue();
        verify(wrongQuestionService, never()).recordWrongQuestion(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    /**
     * 验证已作答主观题提交后发布异步 AI 评估事件
     */
    @Test
    @DisplayName("主观题提交后应发布 AI 评估事件")
    void shouldPublishSubjectiveReviewEventOnSubmit() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.IN_PROGRESS.name())
                .setObjectiveCount(0).setSubjectiveCount(1).setObjectiveFullScore(0);
        PracticeQuestion question = snapshot(100L, QuestionType.SUBJECTIVE.getValue(), "参考答案");
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L)
                .setPracticeQuestionId(100L).setQuestionId(50L).setStudentAnswer("学生答案")
                .setStatus(PracticeAnswerStatus.SAVED.name());
        prepareSubmit(session, question, answer);
        when(practiceAnswerMapper.updateById(any(PracticeAnswer.class))).thenReturn(1);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.submit(1L);

        ArgumentCaptor<PracticeSubjectiveReviewRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PracticeSubjectiveReviewRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().sessionId()).isEqualTo(1L);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(10L);
        assertThat(eventCaptor.getValue().expectedRetryCount()).isZero();
        ArgumentCaptor<PracticeSession> sessionCaptor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(PracticeSessionStatus.SUBMITTED.name());
        assertThat(sessionCaptor.getValue().getAiReviewPendingCount()).isEqualTo(1);
    }

    /**
     * 验证未作答主观题仍进入人工确认闭环，但不会触发无意义的 AI 调用
     */
    @Test
    @DisplayName("未作答主观题应等待人工确认且不触发 AI 评估")
    void shouldKeepUnansweredSubjectiveQuestionPendingManualConfirmation() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.IN_PROGRESS.name())
                .setObjectiveCount(0).setSubjectiveCount(1).setObjectiveFullScore(0);
        PracticeQuestion question = snapshot(100L, QuestionType.SUBJECTIVE.getValue(), "参考答案");
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L)
                .setPracticeQuestionId(100L).setQuestionId(50L).setStudentAnswer("")
                .setStatus(PracticeAnswerStatus.UNANSWERED.name());
        prepareSubmit(session, question, answer);
        when(practiceAnswerMapper.updateById(any(PracticeAnswer.class))).thenReturn(1);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.submit(1L);

        assertThat(answer.getStatus()).isEqualTo(PracticeAnswerStatus.AI_REVIEWED.name());
        assertThat(answer.getAiSuggestedScore()).isZero();
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.SUBMITTED.name());
        assertThat(session.getAiReviewPendingCount()).isEqualTo(1);
        verify(eventPublisher, never()).publishEvent(any(PracticeSubjectiveReviewRequestedEvent.class));
    }

    /**
     * 验证提交后的练习不能再次修改答案
     */
    @Test
    @DisplayName("提交后保存答案应被拒绝")
    void shouldRejectSavingAnswerAfterSubmit() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);

        assertThatThrownBy(() -> practiceSessionService.saveAnswer(1L, 100L, saveAnswer("新答案")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能再修改");

        verify(practiceAnswerMapper, never()).update(isNull(PracticeAnswer.class), any(Wrapper.class));
    }

    /**
     * 验证重复提交已完成练习不会再次判分或累加统计
     */
    @Test
    @DisplayName("重复提交练习不应重复判分")
    void shouldNotGradeAgainWhenSessionWasAlreadySubmitted() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        PracticeQuestion question = snapshot(100L, QuestionType.RADIO.getValue(), "1");
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L)
                .setPracticeQuestionId(100L).setQuestionId(50L).setStudentAnswer("1")
                .setStatus(PracticeAnswerStatus.OBJECTIVE_GRADED.name()).setScore(5).setCorrect(true);
        prepareSubmit(session, question, answer);

        practiceSessionService.submit(1L);

        verify(questionMapper, never()).incrementAnswerStatistics(anyLong(), eq(true));
        verify(questionMapper, never()).incrementAnswerStatistics(anyLong(), eq(false));
        verify(practiceAnswerMapper, never()).updateById(any(PracticeAnswer.class));
        verify(practiceSessionMapper, never()).updateById(any(PracticeSession.class));
        verify(wrongQuestionService, never()).recordWrongQuestion(
                anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    /**
     * 验证主观题 AI 评估重试后会累加次数并重新发布任务
     */
    @Test
    @DisplayName("主观题 AI 评估重试应累加次数")
    void shouldIncreaseRetryCountWhenRetryingAiReview() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name())
                .setAiReviewRetryCount(1)
                .setAiReviewMaxRetryCount(3);
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceAnswerMapper.update(isNull(PracticeAnswer.class), any(Wrapper.class))).thenReturn(2);
        when(practiceAnswerMapper.countUnfinishedAiReview(1L)).thenReturn(2);
        when(practiceAnswerMapper.countAiReviewed(1L)).thenReturn(0);
        when(practiceAnswerMapper.countAiReviewFailed(1L)).thenReturn(0);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.retryAiReview(1L);

        ArgumentCaptor<PracticeSession> sessionCaptor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getAiReviewRetryCount()).isEqualTo(2);
        assertThat(sessionCaptor.getValue().getAiReviewPendingCount()).isEqualTo(2);
        ArgumentCaptor<PracticeSubjectiveReviewRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(PracticeSubjectiveReviewRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().expectedRetryCount()).isEqualTo(2);
    }

    /**
     * 验证失败题重试时不会遗漏已生成 AI 建议但等待人工确认的题目
     */
    @Test
    @DisplayName("重试失败题时应重新汇总所有待处理主观题")
    void shouldRecalculateAllPendingReviewsWhenRetryingFailedAnswers() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.SUBMITTED.name())
                .setAiReviewRetryCount(0)
                .setAiReviewMaxRetryCount(3);
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceAnswerMapper.update(isNull(PracticeAnswer.class), any(Wrapper.class))).thenReturn(1);
        when(practiceAnswerMapper.countUnfinishedAiReview(1L)).thenReturn(1);
        when(practiceAnswerMapper.countAiReviewed(1L)).thenReturn(1);
        when(practiceAnswerMapper.countAiReviewFailed(1L)).thenReturn(0);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.retryAiReview(1L);

        ArgumentCaptor<PracticeSession> sessionCaptor = ArgumentCaptor.forClass(PracticeSession.class);
        verify(practiceSessionMapper).updateById(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getAiReviewPendingCount()).isEqualTo(2);
        assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(PracticeSessionStatus.SUBMITTED.name());
    }

    /**
     * 验证主观题 AI 评估达到次数上限后不能继续重试
     */
    @Test
    @DisplayName("达到最大次数后应拒绝重试主观题 AI 评估")
    void shouldRejectAiReviewRetryAfterMaximumAttempts() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name())
                .setAiReviewRetryCount(3)
                .setAiReviewMaxRetryCount(3);
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);

        assertThatThrownBy(() -> practiceSessionService.retryAiReview(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最大重试次数");

        verify(practiceAnswerMapper, never()).update(isNull(PracticeAnswer.class), any(Wrapper.class));
        verify(practiceSessionMapper, never()).updateById(any(PracticeSession.class));
        verify(eventPublisher, never()).publishEvent(any(PracticeSubjectiveReviewRequestedEvent.class));
    }

    /**
     * 验证不存在失败主观题时不能发起无意义重试
     */
    @Test
    @DisplayName("没有失败主观题时应拒绝重试")
    void shouldRejectRetryWhenNoFailedSubjectiveAnswerExists() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceAnswerMapper.update(isNull(PracticeAnswer.class), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> practiceSessionService.retryAiReview(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("没有可重试");

        verify(practiceSessionMapper, never()).updateById(any(PracticeSession.class));
        verify(eventPublisher, never()).publishEvent(any(PracticeSubjectiveReviewRequestedEvent.class));
    }
    /**
     * 验证课程创建者可以确认主观题 AI 建议分
     */
    @Test
    @DisplayName("课程创建者可以确认主观题 AI 建议分")
    void shouldAllowCourseCreatorToConfirmSubjectiveAiScore() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L).setUserId(10L)
                .setPracticeQuestionId(100L).setStatus(PracticeAnswerStatus.AI_REVIEWED.name())
                .setAiSuggestedScore(3).setAiTotalScore(5);
        PracticeQuestion question = snapshot(100L, QuestionType.SUBJECTIVE.getValue(), "参考答案");
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(courseClient.baseInfo(20L, true)).thenReturn(courseOfCreator(10L));
        when(practiceAnswerMapper.selectOne(any(Wrapper.class))).thenReturn(answer);
        when(practiceQuestionMapper.selectById(100L)).thenReturn(question);
        when(practiceAnswerMapper.updateById(any(PracticeAnswer.class))).thenReturn(1);
        when(practiceAnswerMapper.countUnfinishedAiReview(1L)).thenReturn(0);
        when(practiceAnswerMapper.countAiReviewed(1L)).thenReturn(0);
        when(practiceAnswerMapper.countAiReviewFailed(1L)).thenReturn(0);
        when(practiceAnswerMapper.sumScoredAnswers(1L)).thenReturn(4);
        when(practiceSessionMapper.updateById(any(PracticeSession.class))).thenReturn(1);

        practiceSessionService.confirmAiReview(1L,
                confirmRequest(200L, 4));

        assertThat(answer.getStatus()).isEqualTo(PracticeAnswerStatus.MANUALLY_REVIEWED.name());
        assertThat(answer.getScore()).isEqualTo(4);
        assertThat(session.getStatus()).isEqualTo(PracticeSessionStatus.COMPLETED.name());
        assertThat(session.getFinalScore()).isEqualTo(4);
        assertThat(session.getAiReviewPendingCount()).isZero();
        assertThat(session.getCompletedTime()).isNotNull();
        verify(practiceAnswerMapper).updateById(answer);
        verify(practiceSessionMapper).updateById(session);
    }

    /**
     * 验证非课程创建者不能确认主观题 AI 建议分
     */
    @Test
    @DisplayName("非课程创建者不能确认主观题 AI 建议分")
    void shouldRejectSubjectiveAiConfirmationForNonCourseCreator() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(courseClient.baseInfo(20L, true)).thenReturn(courseOfCreator(99L));

        assertThatThrownBy(() -> practiceSessionService.confirmAiReview(1L,
                confirmRequest(200L, 4)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("课程创建者");

        verify(practiceAnswerMapper, never()).selectOne(any(Wrapper.class));
    }

    /**
     * 验证人工确认分数不能超过题目总分
     */
    @Test
    @DisplayName("人工确认分数不能超过题目总分")
    void shouldRejectSubjectiveAiConfirmationScoreAboveQuestionScore() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L).setUserId(10L)
                .setPracticeQuestionId(100L).setStatus(PracticeAnswerStatus.AI_REVIEWED.name());
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(courseClient.baseInfo(20L, true)).thenReturn(courseOfCreator(10L));
        when(practiceAnswerMapper.selectOne(any(Wrapper.class))).thenReturn(answer);
        when(practiceQuestionMapper.selectById(100L)).thenReturn(
                snapshot(100L, QuestionType.SUBJECTIVE.getValue(), "参考答案"));

        assertThatThrownBy(() -> practiceSessionService.confirmAiReview(1L,
                confirmRequest(200L, 6)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能超过题目总分");

        verify(practiceAnswerMapper, never()).updateById(any(PracticeAnswer.class));
    }
    /**
     * 验证人工确认分数不能小于零
     */
    @Test
    @DisplayName("人工确认分数不能小于零")
    void shouldRejectNegativeSubjectiveAiConfirmationScore() {
        UserContext.setUser(10L);
        PracticeSession session = session(1L, PracticeSessionStatus.COMPLETED.name());
        PracticeAnswer answer = new PracticeAnswer().setId(200L).setSessionId(1L).setUserId(10L)
                .setPracticeQuestionId(100L).setStatus(PracticeAnswerStatus.AI_REVIEWED.name());
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(courseClient.baseInfo(20L, true)).thenReturn(courseOfCreator(10L));
        when(practiceAnswerMapper.selectOne(any(Wrapper.class))).thenReturn(answer);
        when(practiceQuestionMapper.selectById(100L)).thenReturn(
                snapshot(100L, QuestionType.SUBJECTIVE.getValue(), "参考答案"));

        assertThatThrownBy(() -> practiceSessionService.confirmAiReview(1L,
                confirmRequest(200L, -1)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("不能小于 0");

        verify(practiceAnswerMapper, never()).updateById(any(PracticeAnswer.class));
    }

    /**
     * 验证课程创建者可以分页查询待审核主观题，并按置信度与人工复核标记筛选
     */
    @Test
    @DisplayName("课程创建者可以查询主观题 AI 待审核项")
    void shouldQueryPendingAiReviewsForCourseCreator() {
        UserContext.setUser(10L);
        when(courseClient.baseInfo(20L, true)).thenReturn(courseOfCreator(10L));
        PracticeAiReviewPageQuery query = new PracticeAiReviewPageQuery();
        query.setCourseId(20L);
        query.setTargetBizId(30L);
        query.setManualReviewRecommended(true);
        query.setMinConfidence(new java.math.BigDecimal("0.50"));
        query.setMaxConfidence(new java.math.BigDecimal("0.90"));
        Page<PracticeAiReviewItemVO> page = new Page<>(1, 20);
        page.setTotal(1);
        PracticeAiReviewItemVO item = new PracticeAiReviewItemVO();
        item.setAnswerId(200L);
        item.setStudentId(11L);
        item.setAiSuggestedScore(4);
        page.setRecords(List.of(item));
        when(practiceAnswerMapper.selectPendingAiReviewPage(any(Page.class), eq(query))).thenReturn(page);

        var result = practiceSessionService.queryPendingAiReviews(query);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getAnswerId()).isEqualTo(200L);
        verify(courseClient).baseInfo(20L, true);
        verify(practiceAnswerMapper).selectPendingAiReviewPage(any(Page.class), eq(query));
    }

    /**
     * 验证课程创建者之外的用户不能查询课程待审核主观题
     */
    @Test
    @DisplayName("非课程创建者不能查询主观题 AI 待审核项")
    void shouldRejectPendingAiReviewQueryForNonCreator() {
        UserContext.setUser(11L);
        when(courseClient.baseInfo(20L, true)).thenReturn(courseOfCreator(10L));
        PracticeAiReviewPageQuery query = new PracticeAiReviewPageQuery();
        query.setCourseId(20L);

        assertThatThrownBy(() -> practiceSessionService.queryPendingAiReviews(query))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("课程创建者");

        verify(practiceAnswerMapper, never()).selectPendingAiReviewPage(any(Page.class), any());
    }

    /**
     * 验证待审核列表不能使用反向置信度范围
     */
    @Test
    @DisplayName("反向置信度范围应被拒绝")
    void shouldRejectInvalidReviewConfidenceRange() {
        UserContext.setUser(10L);
        PracticeAiReviewPageQuery query = new PracticeAiReviewPageQuery();
        query.setCourseId(20L);
        query.setMinConfidence(new java.math.BigDecimal("0.90"));
        query.setMaxConfidence(new java.math.BigDecimal("0.50"));

        assertThatThrownBy(() -> practiceSessionService.queryPendingAiReviews(query))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最低置信度不能大于最高置信度");

        verify(courseClient, never()).baseInfo(anyLong(), eq(true));
    }
    /**
     * 创建带课程创建者信息的课程对象
     *
     * @param creatorId 课程创建者 ID
     * @return 课程基础信息
     */
    private CourseBaseInfoDTO courseOfCreator(Long creatorId) {
        CourseBaseInfoDTO course = new CourseBaseInfoDTO();
        course.setCreater(creatorId);
        return course;
    }
    /**
     * 创建主观题 AI 评估人工确认参数
     *
     * @param answerId 答案 ID
     * @param finalScore 最终得分
     * @return 人工确认参数
     */
    private PracticeAiReviewConfirmDTO confirmRequest(Long answerId, Integer finalScore) {
        PracticeAiReviewConfirmDTO dto = new PracticeAiReviewConfirmDTO();
        dto.setAnswerId(answerId);
        dto.setFinalScore(finalScore);
        return dto;
    }
    /**
     * 创建练习会话创建参数
     *
     * @param requestId 幂等请求标识
     * @return 创建参数
     */
    private PracticeSessionCreateDTO createRequest(String requestId) {
        PracticeSessionCreateDTO request = new PracticeSessionCreateDTO();
        request.setCourseId(20L);
        request.setTargetBizId(30L);
        request.setRequestId(requestId);
        return request;
    }

    /**
     * 准备创建会话所需的课程、题目和题目详情数据
     */
    private void prepareCreateData() {
        when(learningClient.isLessonValid(20L)).thenReturn(500L);
        CatalogueDetailDTO catalogue = new CatalogueDetailDTO();
        catalogue.setId(30L);
        catalogue.setCourseId(20L);
        catalogue.setType(3);
        catalogue.setName("第一章练习");
        when(catalogueClient.queryCatalogueDetail(30L)).thenReturn(catalogue);
        when(questionBizMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                QuestionBiz.of(1L, 30L, 50L)));
        when(questionMapper.selectBatchIds(anyList())).thenReturn(List.of(new Question()
                .setId(50L).setName("Java 中哪个关键字用于继承？")
                .setType(QuestionType.RADIO.getValue()).setDifficulty(1).setScore(5)));
        when(questionDetailMapper.selectBatchIds(anyList())).thenReturn(List.of(new QuestionDetail()
                .setId(50L).setOptions(List.of("extends", "implements"))
                .setAnswer("0").setAnalysis("extends 用于类继承")));
    }

    /**
     * 创建一个练习题快照
     *
     * @param id 快照 ID
     * @param type 题型
     * @param standardAnswer 标准答案
     * @return 练习题快照
     */
    private PracticeQuestion snapshot(Long id, int type, String standardAnswer) {
        return new PracticeQuestion().setId(id).setSessionId(1L).setQuestionId(50L)
                .setSequenceNo(1).setName("练习题").setType(type).setDifficulty(1)
                .setScore(5).setStandardAnswer(standardAnswer).setAnalysis("解析");
    }

    /**
     * 创建一个练习会话
     *
     * @param id 会话 ID
     * @param status 会话状态
     * @return 练习会话
     */
    private PracticeSession session(Long id, String status) {
        return new PracticeSession().setId(id).setUserId(10L).setCourseId(20L)
                .setTargetBizId(30L).setTargetName("练习").setStatus(status)
                .setTotalQuestions(1).setAnsweredCount(0).setObjectiveCount(1)
                .setSubjectiveCount(0).setTotalScore(5).setObjectiveFullScore(5)
                .setObjectiveScore(0).setCorrectCount(0).setAiReviewPendingCount(0)
                .setAiReviewRetryCount(0).setAiReviewMaxRetryCount(3);
    }

    /**
     * 准备提交练习时的会话、题目和答案查询结果
     *
     * @param session 练习会话
     * @param question 练习题快照
     * @param answer 学生答案
     */
    private void prepareSubmit(PracticeSession session, PracticeQuestion question, PracticeAnswer answer) {
        when(practiceSessionMapper.selectById(1L)).thenReturn(session);
        when(practiceSessionMapper.lockById(1L)).thenReturn(1L);
        when(practiceQuestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(question));
        when(practiceAnswerMapper.selectList(any(Wrapper.class))).thenReturn(List.of(answer));
    }

    /**
     * 创建答案保存参数
     *
     * @param value 学生答案
     * @return 答案保存参数
     */
    private PracticeAnswerSaveDTO saveAnswer(String value) {
        PracticeAnswerSaveDTO dto = new PracticeAnswerSaveDTO();
        dto.setAnswer(value);
        return dto;
    }
}
