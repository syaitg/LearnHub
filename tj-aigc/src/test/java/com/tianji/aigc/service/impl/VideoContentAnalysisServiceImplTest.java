package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.model.VideoContentAnalysisResult;
import com.tianji.aigc.domain.model.VideoKnowledgePoint;
import com.tianji.aigc.domain.model.VideoSectionSummary;
import com.tianji.common.exceptions.BizIllegalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * 视频内容分析结果校验测试
 */
class VideoContentAnalysisServiceImplTest {

    private VideoContentAnalysisServiceImpl service;

    /**
     * 初始化视频内容分析服务
     */
    @BeforeEach
    void setUp() {
        service = new VideoContentAnalysisServiceImpl(mock(ChatClient.class), new VideoAiProperties());
    }

    /**
     * 验证模型省略可选数组时，服务会统一规范为空数组，避免后续保存和出题阶段出现空指针。
     */
    @Test
    @DisplayName("可选分析数组为空时应统一规范为空数组")
    void shouldNormalizeOptionalListsToEmptyLists() throws Exception {
        VideoContentAnalysisResult result = new VideoContentAnalysisResult()
                .setIntroduction("视频简介")
                .setCoreContent("核心内容")
                .setSectionSummaries(null)
                .setKeyConclusions(null)
                .setSuitableLearners(null)
                .setReviewPoints(null)
                .setKnowledgePoints(null);

        invokeValidate(result, 60_000L);

        assertThat(result.getSectionSummaries()).isNotNull().isEmpty();
        assertThat(result.getKeyConclusions()).isNotNull().isEmpty();
        assertThat(result.getSuitableLearners()).isNotNull().isEmpty();
        assertThat(result.getReviewPoints()).isNotNull().isEmpty();
        assertThat(result.getKnowledgePoints()).isNotNull().isEmpty();
    }

    /**
     * 验证知识点重要度越界时不会进入任务完成状态。
     */
    @Test
    @DisplayName("知识点重要度越界时应拒绝分析结果")
    void shouldRejectKnowledgePointWithInvalidImportance() {
        VideoContentAnalysisResult result = validResult()
                .setKnowledgePoints(List.of(new VideoKnowledgePoint()
                        .setName("知识点")
                        .setDescription("知识点说明")
                        .setImportance(6)
                        .setDifficulty(2)
                        .setStartMs(0L)
                        .setEndMs(10_000L)));

        assertThatThrownBy(() -> invokeValidate(result, 60_000L))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("重要度");
    }

    /**
     * 验证分段摘要时间范围超出视频时长时会被拒绝。
     */
    @Test
    @DisplayName("分段摘要时间超出视频时长时应拒绝分析结果")
    void shouldRejectSectionSummaryOutsideDuration() {
        VideoContentAnalysisResult result = validResult()
                .setSectionSummaries(List.of(new VideoSectionSummary()
                        .setTitle("分段")
                        .setSummary("摘要")
                        .setStartMs(0L)
                        .setEndMs(60_001L)));

        assertThatThrownBy(() -> invokeValidate(result, 60_000L))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("时间范围");
    }

    /**
     * 验证大模型返回 Markdown 或解释文字时不会被当作合法 JSON 接受。
     */
    @Test
    @DisplayName("大模型返回非 JSON 内容时应拒绝")
    void shouldRejectNonJsonModelResponse() {
        assertThatThrownBy(() -> invokeParse("```json\n{}\n```"))
                .isInstanceOf(BizIllegalException.class)
                .hasMessageContaining("合法 JSON");
    }

    /**
     * 构造合法的视频分析结果
     *
     * @return 合法结果
     */
    private VideoContentAnalysisResult validResult() {
        return new VideoContentAnalysisResult()
                .setIntroduction("视频简介")
                .setCoreContent("核心内容")
                .setSectionSummaries(new ArrayList<>())
                .setKeyConclusions(new ArrayList<>())
                .setSuitableLearners(new ArrayList<>())
                .setReviewPoints(new ArrayList<>())
                .setKnowledgePoints(new ArrayList<>());
    }

    /**
     * 通过反射调用结果校验方法，保留业务异常原类型。
     */
    private void invokeValidate(VideoContentAnalysisResult result, Long durationMs) throws Exception {
        Method method = VideoContentAnalysisServiceImpl.class
                .getDeclaredMethod("validateAndNormalize", VideoContentAnalysisResult.class, Long.class);
        method.setAccessible(true);
        try {
            method.invoke(service, result, durationMs);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    /**
     * 通过反射调用模型 JSON 解析方法，保留业务异常原类型。
     */
    private void invokeParse(String content) {
        Method method;
        try {
            method = VideoContentAnalysisServiceImpl.class.getDeclaredMethod("parseResult", String.class);
            method.setAccessible(true);
            method.invoke(service, content);
        } catch (InvocationTargetException e) {
            throw (BizIllegalException) e.getCause();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("调用视频分析 JSON 解析方法失败", e);
        }
    }
}
