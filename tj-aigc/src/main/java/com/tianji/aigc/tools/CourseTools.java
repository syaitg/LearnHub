package com.tianji.aigc.tools;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.tianji.aigc.config.ToolResultHolder;
import com.tianji.aigc.constants.Constant;
import com.tianji.aigc.tools.result.CourseInfo;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.constants.CourseStatus;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程工具
 */
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient courseClient;
    private final SearchClient searchClient;
    private static final String FIELD_NAME_FORMAT = "{}_{}";
    private static final int MAX_SEARCH_RESULTS = 6;

    @Tool(description = Constant.Tools.QUERY_COURSE_BY_ID)
    public CourseInfo queryCourseById(@ToolParam(description = Constant.ToolParams.COURSE_ID) Long courseId, ToolContext toolContext) {
        if (courseId == null || courseId <= 0 || !queryPurchasableCourseIds(List.of(courseId)).contains(courseId)) {
            return null;
        }
        return Optional.ofNullable(this.courseClient.baseInfo(courseId, true))
                .map(CourseInfo::of)
                .map(courseInfo -> {
                    // 将结果存储到容器中
                    var requestId = MapUtil.get(toolContext.getContext(), Constant.REQUEST_ID, String.class);
                    var field = StrUtil.format(FIELD_NAME_FORMAT, StrUtil.lowerFirst(CourseInfo.class.getSimpleName()), courseId);
                    ToolResultHolder.put(requestId, field, courseInfo);
                    return courseInfo;
                })
                .orElse(null);
    }

    @Tool(description = Constant.Tools.SEARCH_COURSES_BY_KEYWORD)
    public List<CourseInfo> searchCoursesByKeyword(
            @ToolParam(description = Constant.ToolParams.COURSE_KEYWORD) String keyword,
            ToolContext toolContext) {
        if (StrUtil.isBlank(keyword)) {
            return List.of();
        }

        var requestId = MapUtil.get(toolContext.getContext(), Constant.REQUEST_ID, String.class);
        List<Long> candidateIds = queryCandidateCourseIds(keyword);
        Set<Long> purchasableIds = queryPurchasableCourseIds(candidateIds);
        return candidateIds.stream()
                .filter(purchasableIds::contains)
                .map(id -> this.courseClient.baseInfo(id, true))
                .map(CourseInfo::of)
                .filter(Objects::nonNull)
                .peek(courseInfo -> {
                    var field = StrUtil.format(FIELD_NAME_FORMAT,
                            StrUtil.lowerFirst(CourseInfo.class.getSimpleName()), courseInfo.getId());
                    ToolResultHolder.put(requestId, field, courseInfo);
                })
                .toList();
    }

    /**
     * 先查搜索索引，再使用课程数据库兜底，避免索引延迟导致明明存在的课程无法被AI检索到。
     */
    private List<Long> queryCandidateCourseIds(String keyword) {
        Set<Long> ids = new LinkedHashSet<>();
        try {
            addValidCourseIds(ids, searchClient.queryCoursesIdByName(keyword));
        } catch (Exception e) {
            // 搜索索引异常时继续使用课程数据库查询，不能让课程推荐直接失效。
        }
        try {
            addValidCourseIds(ids, courseClient.queryCourseIdsByName(keyword));
        } catch (Exception e) {
            // 数据库兜底查询异常时保留搜索索引结果。
        }
        return new ArrayList<>(ids).stream()
                .limit(MAX_SEARCH_RESULTS)
                .toList();
    }

    /**
     * 过滤无效课程ID并保持原有搜索顺序。
     */
    private void addValidCourseIds(Set<Long> target, List<Long> source) {
        if (source == null) {
            return;
        }
        source.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .forEach(target::add);
    }

    private Set<Long> queryPurchasableCourseIds(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Set.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return Optional.ofNullable(courseClient.getSimpleInfoList(courseIds)).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                .filter(course -> course.getId() != null)
                .filter(course -> CourseStatus.SHELF.equalsValue(course.getStatus())
                        || CourseStatus.FINISHED.equalsValue(course.getStatus()))
                .filter(course -> course.getPurchaseEndTime() != null && !course.getPurchaseEndTime().isBefore(now))
                .map(CourseSimpleInfoDTO::getId)
                .collect(Collectors.toSet());
    }

}
