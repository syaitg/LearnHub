package com.tianji.aigc.service.impl;

import com.tianji.aigc.config.SessionProperties;
import com.tianji.aigc.vo.SessionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceImplTest {

    @Mock
    private SessionProperties sessionProperties;

    @Mock
    private ChatMemory chatMemory;

    @InjectMocks
    private ChatSessionServiceImpl chatSessionService;

    @Test
    void shouldReturnEmptyExamplesWhenSessionExamplesAreMissing() {
        when(sessionProperties.getExamples()).thenReturn(null);

        List<SessionVO.Example> result = chatSessionService.hotExamples(3);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldLimitRequestedCountToConfiguredExampleCount() {
        List<SessionVO.Example> examples = List.of(
                new SessionVO.Example("course", "recommend a Java course"),
                new SessionVO.Example("plan", "create a Java study plan")
        );
        when(sessionProperties.getExamples()).thenReturn(examples);

        List<SessionVO.Example> result = chatSessionService.hotExamples(3);

        assertThat(result).containsExactlyInAnyOrderElementsOf(examples);
    }

    @Test
    void shouldReturnEmptyExamplesWhenRequestedCountIsNotPositive() {
        when(sessionProperties.getExamples()).thenReturn(List.of(
                new SessionVO.Example("course", "recommend a Java course")
        ));

        assertThat(chatSessionService.hotExamples(0)).isEmpty();
        assertThat(chatSessionService.hotExamples(-1)).isEmpty();
        assertThat(chatSessionService.hotExamples(null)).isEmpty();
    }
}
