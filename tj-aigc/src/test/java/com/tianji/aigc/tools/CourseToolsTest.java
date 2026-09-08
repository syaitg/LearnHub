package com.tianji.aigc.tools;

import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseToolsTest {

    private static final String REQUEST_ID = "course-search-test";

    @Mock
    private CourseClient courseClient;

    @Mock
    private SearchClient searchClient;

    @Mock
    private ToolContext toolContext;

    @AfterEach
    void clearToolResults() {
        ToolResultHolder.remove(REQUEST_ID);
    }

    @Test
    void shouldSearchRealCoursesAndExposeCardData() {
        long courseId = 1589905661084430337L;
        when(toolContext.getContext()).thenReturn(Map.of("requestId", REQUEST_ID));
        when(searchClient.queryCoursesIdByName("microservices")).thenReturn(List.of(courseId, courseId));
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(purchasableCourse(courseId)));

        CourseBaseInfoDTO dto = new CourseBaseInfoDTO();
        dto.setId(courseId);
        dto.setName("Microservices Technology Stack");
        dto.setPrice(19900);
        when(courseClient.baseInfo(courseId, true)).thenReturn(dto);

        CourseTools tools = new CourseTools(courseClient, searchClient);
        var result = tools.searchCoursesByKeyword("microservices", toolContext);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(courseId);
        assertThat(result.get(0).getName()).isEqualTo("Microservices Technology Stack");
        assertThat(result.get(0).getPrice()).isEqualTo(199.0d);
        assertThat(ToolResultHolder.get(REQUEST_ID, "courseInfo_" + courseId)).isEqualTo(result.get(0));
        verify(courseClient).baseInfo(courseId, true);
    }

    @Test
    void shouldUseCourseDatabaseWhenSearchIndexHasNoResult() {
        long courseId = 2095879957129056258L;
        when(toolContext.getContext()).thenReturn(Map.of("requestId", REQUEST_ID));
        when(searchClient.queryCoursesIdByName("MySQL")).thenReturn(List.of());
        when(courseClient.queryCourseIdsByName("MySQL")).thenReturn(List.of(courseId));
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(purchasableCourse(courseId)));

        CourseBaseInfoDTO dto = new CourseBaseInfoDTO();
        dto.setId(courseId);
        dto.setName("MySQL入门课程");
        when(courseClient.baseInfo(courseId, true)).thenReturn(dto);

        CourseTools tools = new CourseTools(courseClient, searchClient);
        var result = tools.searchCoursesByKeyword("MySQL", toolContext);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(courseId);
        verify(courseClient).queryCourseIdsByName("MySQL");
    }

    @Test
    void shouldIncludeFinishedCoursesInRecommendations() {
        long courseId = 2095879957129056258L;
        when(toolContext.getContext()).thenReturn(Map.of("requestId", REQUEST_ID));
        when(searchClient.queryCoursesIdByName("Java")).thenReturn(List.of(courseId));
        CourseSimpleInfoDTO simpleInfo = purchasableCourse(courseId);
        simpleInfo.setStatus(CourseStatus.FINISHED.getValue());
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of(simpleInfo));

        CourseBaseInfoDTO dto = new CourseBaseInfoDTO();
        dto.setId(courseId);
        dto.setName("Java进阶");
        when(courseClient.baseInfo(courseId, true)).thenReturn(dto);

        CourseTools tools = new CourseTools(courseClient, searchClient);
        assertThat(tools.searchCoursesByKeyword("Java", toolContext)).hasSize(1);
    }

    @Test
    void shouldReturnEmptyWithoutCallingSearchForBlankKeyword() {
        CourseTools tools = new CourseTools(courseClient, searchClient);

        assertThat(tools.searchCoursesByKeyword("  ", toolContext)).isEmpty();
        verifyNoInteractions(searchClient, courseClient, toolContext);
    }

    @Test
    void shouldIgnoreCourseMissingFromAuthoritativeCourseTable() {
        long courseId = 1L;
        when(toolContext.getContext()).thenReturn(Map.of("requestId", REQUEST_ID));
        when(searchClient.queryCoursesIdByName("Java")).thenReturn(List.of(courseId));
        when(courseClient.getSimpleInfoList(List.of(courseId))).thenReturn(List.of());

        CourseTools tools = new CourseTools(courseClient, searchClient);

        assertThat(tools.searchCoursesByKeyword("Java", toolContext)).isEmpty();
        assertThat(ToolResultHolder.get(REQUEST_ID)).isNull();
        verify(courseClient).getSimpleInfoList(List.of(courseId));
    }

    @Test
    void shouldTreatEmptyCourseDtoAsMissing() {
        assertThat(com.tianji.aigc.tools.result.CourseInfo.of(new CourseBaseInfoDTO())).isNull();
    }

    private CourseSimpleInfoDTO purchasableCourse(long courseId) {
        CourseSimpleInfoDTO dto = new CourseSimpleInfoDTO();
        dto.setId(courseId);
        dto.setStatus(CourseStatus.SHELF.getValue());
        dto.setPurchaseEndTime(LocalDateTime.now().plusDays(1));
        return dto;
    }
}
