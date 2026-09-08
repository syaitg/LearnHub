package com.tianji.exam.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.constants.QuestionType;
import com.tianji.exam.domain.dto.WrongQuestionReviewDTO;
import com.tianji.exam.domain.po.PracticeAnswer;
import com.tianji.exam.domain.po.PracticeQuestion;
import com.tianji.exam.domain.po.PracticeSession;
import com.tianji.exam.domain.po.WrongQuestion;
import com.tianji.exam.domain.po.WrongQuestionReview;
import com.tianji.exam.domain.vo.WrongQuestionReviewVO;
import com.tianji.exam.enums.WrongQuestionStatus;
import com.tianji.exam.mapper.PracticeAnswerMapper;
import com.tianji.exam.mapper.PracticeQuestionMapper;
import com.tianji.exam.mapper.PracticeSessionMapper;
import com.tianji.exam.mapper.WrongQuestionMapper;
import com.tianji.exam.mapper.WrongQuestionReviewMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生错题服务测试
 */
@ExtendWith(MockitoExtension.class)
class WrongQuestionServiceImplTest {

    @Mock
    private WrongQuestionMapper wrongQuestionMapper;
    @Mock
    private PracticeQuestionMapper practiceQuestionMapper;
    @Mock
    private PracticeSessionMapper practiceSessionMapper;
    @Mock
    private PracticeAnswerMapper practiceAnswerMapper;
    @Mock
    private WrongQuestionReviewMapper reviewMapper;

    private WrongQuestionServiceImpl wrongQuestionService;

    /**
     * 创建待测试服务并注入 MyBatis-Plus 基础 Mapper
     */
    @BeforeEach
    void setUp() {
        wrongQuestionService = new WrongQuestionServiceImpl(practiceQuestionMapper, practiceSessionMapper, practiceAnswerMapper, reviewMapper);
        ReflectionTestUtils.setField(wrongQuestionService, "baseMapper", wrongQuestionMapper);
        UserContext.setUser(10L);
    }

    /**
     * 每个测试结束后清理线程中的用户上下文
     */
    @AfterEach
    void clearUserContext() {
        UserContext.removeUser();
    }

    /**
     * 验证记录错题时使用数据库唯一键完成新增或累加
     */
    @Test
    @DisplayName("记录错题时应执行错题 upsert")
    void shouldUpsertWrongQuestion() {
        when(wrongQuestionMapper.upsertWrongQuestion(any())).thenReturn(1);

        wrongQuestionService.recordWrongQuestion(10L, 20L, 30L, 40L, 50L, 60L);

        ArgumentCaptor<WrongQuestion> captor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionMapper).upsertWrongQuestion(captor.capture());
        WrongQuestion saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getCourseId()).isEqualTo(20L);
        assertThat(saved.getTargetBizId()).isEqualTo(30L);
        assertThat(saved.getLatestPracticeQuestionId()).isEqualTo(40L);
        assertThat(saved.getQuestionId()).isEqualTo(50L);
        assertThat(saved.getLatestAnswerId()).isEqualTo(60L);
        assertThat(saved.getLastWrongTime()).isNotNull();
    }

    /**
     * 验证重做正确后错题进入已掌握状态
     */
    @Test
    @DisplayName("错题重做正确后应进入已掌握状态")
    void shouldMarkWrongQuestionAsMasteredAfterCorrectReview() {
        prepareOwnedWrongQuestion();
        when(practiceQuestionMapper.selectById(40L)).thenReturn(questionSnapshot());
        when(practiceSessionMapper.selectById(70L)).thenReturn(practiceSession());
        when(practiceAnswerMapper.selectById(60L)).thenReturn(practiceAnswer());
        when(reviewMapper.insert(any(WrongQuestionReview.class))).thenReturn(1);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);
        WrongQuestionReviewDTO dto = answer("1");

        WrongQuestionReviewVO result = wrongQuestionService.review(1L, dto);

        ArgumentCaptor<WrongQuestion> wrongCaptor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionMapper).updateById(wrongCaptor.capture());
        assertThat(wrongCaptor.getValue().getStatus()).isEqualTo(WrongQuestionStatus.MASTERED.name());
        assertThat(wrongCaptor.getValue().getMasteredTime()).isNotNull();
        assertThat(wrongCaptor.getValue().getReviewCount()).isEqualTo(1);
        assertThat(result.getCorrect()).isTrue();
        assertThat(result.getScore()).isEqualTo(5);
    }

    /**
     * 验证重做错误后错题保持待掌握状态
     */
    @Test
    @DisplayName("错题重做错误后应保持待掌握状态")
    void shouldKeepWrongQuestionActiveAfterWrongReview() {
        WrongQuestion wrongQuestion = prepareOwnedWrongQuestion();
        wrongQuestion.setStatus(WrongQuestionStatus.MASTERED.name()).setReviewCount(2);
        when(practiceQuestionMapper.selectById(40L)).thenReturn(questionSnapshot());
        when(practiceSessionMapper.selectById(70L)).thenReturn(practiceSession());
        when(practiceAnswerMapper.selectById(60L)).thenReturn(practiceAnswer());
        when(reviewMapper.insert(any(WrongQuestionReview.class))).thenReturn(1);
        when(wrongQuestionMapper.updateById(any(WrongQuestion.class))).thenReturn(1);

        WrongQuestionReviewVO result = wrongQuestionService.review(1L, answer("0"));

        ArgumentCaptor<WrongQuestion> wrongCaptor = ArgumentCaptor.forClass(WrongQuestion.class);
        verify(wrongQuestionMapper).updateById(wrongCaptor.capture());
        assertThat(wrongCaptor.getValue().getStatus()).isEqualTo(WrongQuestionStatus.ACTIVE.name());
        assertThat(wrongCaptor.getValue().getMasteredTime()).isNull();
        assertThat(wrongCaptor.getValue().getReviewCount()).isEqualTo(3);
        assertThat(result.getCorrect()).isFalse();
        assertThat(result.getScore()).isZero();
    }

    /**
     * 验证错题快照所属会话与错题记录不一致时拒绝访问
     */
    @Test
    @DisplayName("错题快照所属会话不匹配时应拒绝访问")
    void shouldRejectMismatchedPracticeSession() {
        prepareOwnedWrongQuestion();
        when(practiceQuestionMapper.selectById(40L)).thenReturn(questionSnapshot());
        when(practiceSessionMapper.selectById(70L)).thenReturn(practiceSession().setCourseId(999L));

        assertThatThrownBy(() -> wrongQuestionService.review(1L, answer("1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("练习会话信息不匹配");

        verify(reviewMapper, never()).insert(any(WrongQuestionReview.class));
    }

    /**
     * 验证学生不能访问其他学生的错题记录
     */
    @Test
    @DisplayName("学生不能重做其他学生的错题")
    void shouldRejectReviewingAnotherUsersWrongQuestion() {
        when(wrongQuestionMapper.selectById(1L)).thenReturn(new WrongQuestion()
                .setId(1L)
                .setUserId(99L));

        assertThatThrownBy(() -> wrongQuestionService.review(1L, answer("1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("无权访问");

        verify(wrongQuestionMapper, never()).lockById(any());
        verify(reviewMapper, never()).insert(any(WrongQuestionReview.class));
    }

    /**
     * 准备当前学生拥有的错题记录及行锁行为
     *
     * @return 错题记录
     */
    private WrongQuestion prepareOwnedWrongQuestion() {
        WrongQuestion wrongQuestion = new WrongQuestion()
                .setId(1L)
                .setUserId(10L)
                .setQuestionId(50L)
                .setCourseId(20L)
                .setTargetBizId(30L)
                .setLatestPracticeQuestionId(40L)
                .setLatestAnswerId(60L)
                .setWrongCount(1)
                .setReviewCount(0)
                .setStatus(WrongQuestionStatus.ACTIVE.name());
        when(wrongQuestionMapper.selectById(1L)).thenReturn(wrongQuestion);
        when(wrongQuestionMapper.lockById(1L)).thenReturn(1L);
        return wrongQuestion;
    }

    /**
     * 创建一个单选题快照
     *
     * @return 练习题快照
     */
    private PracticeQuestion questionSnapshot() {
        return new PracticeQuestion()
                .setId(40L)
                .setSessionId(70L)
                .setQuestionId(50L)
                .setType(QuestionType.RADIO.getValue())
                .setScore(5)
                .setStandardAnswer("1");
    }

    /**
     * 创建与错题记录一致的练习会话
     *
     * @return 练习会话
     */
    private PracticeSession practiceSession() {
        return new PracticeSession()
                .setId(70L)
                .setUserId(10L)
                .setCourseId(20L)
                .setTargetBizId(30L);
    }

    /**
     * 创建与错题快照一致的最近一次答案
     *
     * @return 练习答案
     */
    private PracticeAnswer practiceAnswer() {
        return new PracticeAnswer()
                .setId(60L)
                .setSessionId(70L)
                .setPracticeQuestionId(40L)
                .setQuestionId(50L)
                .setUserId(10L);
    }

    /**
     * 创建错题重做参数
     *
     * @param value 学生答案
     * @return 错题重做参数
     */
    private WrongQuestionReviewDTO answer(String value) {
        WrongQuestionReviewDTO dto = new WrongQuestionReviewDTO();
        dto.setAnswer(value);
        return dto;
    }
}