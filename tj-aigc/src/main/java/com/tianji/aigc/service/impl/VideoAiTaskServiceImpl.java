package com.tianji.aigc.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.aigc.config.VideoAiProperties;
import com.tianji.aigc.domain.dto.ChapterVideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.dto.VideoAiTaskCreateDTO;
import com.tianji.aigc.domain.dto.VideoQuizDraftCreateDTO;
import com.tianji.aigc.domain.event.VideoAiTaskRequestedEvent;
import com.tianji.aigc.domain.model.TranscriptSegment;
import com.tianji.aigc.domain.model.VideoKnowledgePoint;
import com.tianji.aigc.domain.model.VideoSectionSummary;
import com.tianji.aigc.domain.po.VideoAiTask;
import com.tianji.aigc.domain.query.VideoAiTaskPageQuery;
import com.tianji.aigc.domain.vo.VideoAiTaskVO;
import com.tianji.aigc.enums.VideoAiTaskStatus;
import com.tianji.aigc.mapper.VideoAiTaskMapper;
import com.tianji.aigc.service.VideoAiTaskService;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.exam.ExamClient;
import com.tianji.api.client.media.MediaClient;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.api.dto.exam.AiQuestionBatchCreateRequestDTO;
import com.tianji.api.dto.media.MediaAiInfoDTO;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 视频 AI 处理任务服务实现
 */
@Service
@RequiredArgsConstructor
public class VideoAiTaskServiceImpl extends ServiceImpl<VideoAiTaskMapper, VideoAiTask>
        implements VideoAiTaskService {

    private static final int CATALOGUE_CHAPTER = 1;
    private static final int CATALOGUE_SECTION = 2;
    private static final int CATALOGUE_PRACTICE = 3;
    private static final int MEDIA_UPLOADED = 2;
    private static final int MEDIA_PROCESSED = 3;
    private static final String QUESTION_SCOPE_SECTION = "SECTION";
    private static final String QUESTION_SCOPE_CHAPTER = "CHAPTER";
    private static final String QUESTION_SOURCE_VIDEO_SUMMARY = "VIDEO_SUMMARY";
    private static final int MAX_CHAPTER_VIDEO_TASKS = 20;
    private static final int MAX_KNOWLEDGE_POINTS = 30;
    private static final int MAX_SOURCE_VERSION_LENGTH = 64;

    private final CatalogueClient catalogueClient;
    private final CourseClient courseClient;
    private final MediaClient mediaClient;
    private final ExamClient examClient;
    private final VideoAiProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建视频 AI 任务，并在事务提交后触发异步处理
     *
     * @param dto 创建参数
     * @return 任务 ID
     */
    @Override
    @Transactional
    @Lock(name = "'lock:video-ai:create:' + T(com.tianji.common.utils.UserContext).getUser() + ':' + #dto.mediaId")
    public Long createTask(VideoAiTaskCreateDTO dto) {
        Long userId = requireCurrentUser();
        requireOperableCourse(dto.getCourseId(), userId);
        String requestId = trimToNull(dto.getRequestId());
        VideoAiTask existing = findExistingTask(userId, dto.getMediaId(), requestId);
        if (existing != null) {
            validateExistingScope(existing, dto);
            return existing.getId();
        }

        CatalogueDetailDTO section = catalogueClient.queryCatalogueDetail(dto.getSectionId());
        MediaAiInfoDTO media = mediaClient.queryAiInfo(dto.getMediaId(), dto.getCourseId(), dto.getSectionId());
        validateSection(dto, section);
        validateMedia(dto.getMediaId(), media);

        LocalDateTime now = LocalDateTime.now();
        VideoAiTask task = new VideoAiTask()
                .setCourseId(dto.getCourseId())
                .setSectionId(dto.getSectionId())
                .setSectionName(section.getName())
                .setMediaId(dto.getMediaId())
                .setMediaName(media.getFilename())
                .setMediaUrl(media.getMediaUrl())
                .setMediaSize(media.getSize())
                .setDurationMs(toDurationMillis(media.getDuration()))
                .setTranscriptionProvider(properties.getProvider())
                .setProviderVersion(properties.getProviderVersion())
                .setConfigVersion(properties.getConfigVersion())
                .setLanguage(properties.getLanguage())
                .setStatus(VideoAiTaskStatus.CREATED.name())
                .setProgress(0)
                .setRetryCount(0)
                .setMaxRetryCount(properties.getMaxRetryCount())
                .setRequestId(requestId)
                .setCreateTime(now)
                .setUpdateTime(now)
                .setCreater(userId)
                .setUpdater(userId);
        if (!save(task)) {
            throw new DbException("视频 AI 任务创建失败");
        }
        eventPublisher.publishEvent(new VideoAiTaskRequestedEvent(task.getId()));
        return task.getId();
    }

    /**
     * 分页查询当前用户的视频 AI 任务
     *
     * @param query 查询参数
     * @return 分页任务信息
     */
    @Override
    public PageDTO<VideoAiTaskVO> queryPage(VideoAiTaskPageQuery query) {
        Long userId = requireCurrentUser();
        Page<VideoAiTask> page = query.toMpPageDefaultSortByCreateTimeDesc();
        String status = normalizeTaskStatus(query.getStatus());
        lambdaQuery()
                .eq(VideoAiTask::getCreater, userId)
                .eq(query.getCourseId() != null, VideoAiTask::getCourseId, query.getCourseId())
                .eq(query.getSectionId() != null, VideoAiTask::getSectionId, query.getSectionId())
                .eq(query.getMediaId() != null, VideoAiTask::getMediaId, query.getMediaId())
                .eq(status != null, VideoAiTask::getStatus, status)
                .page(page);
        return PageDTO.of(page, VideoAiTaskVO.class);
    }

    /**
     * 查询当前用户的视频 AI 任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @Override
    public VideoAiTaskVO queryDetail(Long id) {
        return BeanUtils.copyBean(requireOwnedTask(id), VideoAiTaskVO.class);
    }

    /**
     * 将失败任务恢复到转写或分析阶段并重新触发执行
     *
     * @param id 任务 ID
     */
    @Override
    @Transactional
    public void retry(Long id) {
        Long userId = requireCurrentUser();
        VideoAiTask task = requireOwnedTask(id);
        requireOperableCourse(task.getCourseId(), userId);
        if (!VideoAiTaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BadRequestException("只有失败的视频 AI 任务可以重试");
        }
        if (task.getRetryCount() >= task.getMaxRetryCount()) {
            throw new BadRequestException("视频 AI 任务已达到最大重试次数");
        }
        // 媒资服务返回的是带有效期的临时授权地址。失败任务可能是在旧地址过期、
        // 或旧版本仍保存原始地址时创建的，重试前必须重新获取媒资快照。
        MediaAiInfoDTO media = mediaClient.queryAiInfo(task.getMediaId(), task.getCourseId(), task.getSectionId());
        validateMedia(task.getMediaId(), media);
        LocalDateTime now = LocalDateTime.now();
        if (baseMapper.refreshMediaSnapshot(
                id, media.getFilename(), media.getMediaUrl(), media.getSize(),
                toDurationMillis(media.getDuration()), now) != 1) {
            throw new BadRequestException("视频 AI 任务媒资状态已变化，请刷新后重试");
        }
        if (baseMapper.resetForRetry(id, userId, now) != 1) {
            throw new BadRequestException("视频 AI 任务状态已变化，请刷新后重试");
        }
        eventPublisher.publishEvent(new VideoAiTaskRequestedEvent(id));
    }

    /**
     * 将视频处理结果转换为 P1-A 请求并创建测验草稿批次
     *
     * @param id 视频 AI 任务 ID
     * @param dto 测验草稿参数
     * @return AI 出题批次 ID
     */
    @Override
    public Long createQuizDrafts(Long id, VideoQuizDraftCreateDTO dto) {
        Long userId = requireCurrentUser();
        VideoAiTask task = requireOwnedTask(id);
        requireOperableCourse(task.getCourseId(), userId);
        if (!VideoAiTaskStatus.COMPLETED.name().equals(task.getStatus())) {
            throw new BadRequestException("视频 AI 任务完成后才能生成测验草稿");
        }
        validateQuizTarget(task, dto.getTargetBizId());
        AiQuestionBatchCreateRequestDTO request = new AiQuestionBatchCreateRequestDTO();
        request.setCourseId(task.getCourseId());
        request.setScopeType(QUESTION_SCOPE_SECTION);
        request.setScopeId(task.getSectionId());
        request.setSourceType(QUESTION_SOURCE_VIDEO_SUMMARY);
        request.setSourceId(String.valueOf(task.getId()));
        request.setSourceVersion(task.getResultVersion());
        request.setTargetBizId(dto.getTargetBizId());
        request.setKnowledgePoints(extractKnowledgePointNames(task.getKnowledgePoints()));
        request.setMaterialText(buildQuizMaterial(task));
        request.setQuestionTypes(dto.getQuestionTypes());
        request.setQuestionCount(dto.getQuestionCount());
        request.setDifficulty(dto.getDifficulty());
        request.setScore(dto.getScore());
        request.setRequestId(buildQuizRequestId(task, dto));
        return examClient.createAiQuestionBatch(request);
    }

    /**
     * 聚合多个已完成的视频分析结果并创建章级综合测试草稿批次
     *
     * @param dto 章级综合测试草稿参数
     * @return AI 出题批次 ID
     */
    @Override
    public Long createChapterQuizDrafts(ChapterVideoQuizDraftCreateDTO dto) {
        Long userId = requireCurrentUser();
        requireOperableCourse(dto.getCourseId(), userId);
        List<Long> taskIds = normalizeChapterTaskIds(dto.getVideoTaskIds());
        List<VideoAiTask> tasks = requireChapterQuizTasks(taskIds, dto.getCourseId(), userId);
        CatalogueDetailDTO chapter = catalogueClient.queryCatalogueDetail(dto.getChapterId());
        CatalogueDetailDTO target = catalogueClient.queryCatalogueDetail(dto.getTargetBizId());
        validateChapterQuizCatalogues(dto, chapter, target);
        Map<Long, CatalogueDetailDTO> sectionMap = requireChapterSections(tasks, dto.getCourseId(), dto.getChapterId());
        List<VideoAiTask> orderedTasks = orderChapterTasks(tasks, sectionMap);
        List<Long> scopeSectionIds = orderedTasks.stream()
                .map(VideoAiTask::getSectionId)
                .distinct()
                .toList();
        String sourceVersion = buildChapterQuizSourceVersion(orderedTasks);

        AiQuestionBatchCreateRequestDTO request = new AiQuestionBatchCreateRequestDTO();
        request.setCourseId(dto.getCourseId());
        request.setScopeType(QUESTION_SCOPE_CHAPTER);
        request.setScopeId(dto.getChapterId());
        request.setScopeSectionIds(scopeSectionIds);
        request.setSourceType(QUESTION_SOURCE_VIDEO_SUMMARY);
        request.setSourceId(buildChapterQuizSourceId(dto.getChapterId(), orderedTasks));
        request.setSourceVersion(sourceVersion);
        request.setTargetBizId(dto.getTargetBizId());
        request.setKnowledgePoints(extractChapterKnowledgePointNames(orderedTasks));
        request.setMaterialText(buildChapterQuizMaterial(orderedTasks, sectionMap));
        request.setQuestionTypes(dto.getQuestionTypes());
        request.setQuestionCount(dto.getQuestionCount());
        request.setDifficulty(dto.getDifficulty());
        request.setScore(dto.getScore());
        request.setRequestId(buildChapterQuizRequestId(dto, orderedTasks, sourceVersion));
        return examClient.createAiQuestionBatch(request);
    }

    /**
     * 规范化章级出题的视频任务 ID
     *
     * @param taskIds 原始任务 ID 列表
     * @return 去重并排序后的任务 ID
     */
    private List<Long> normalizeChapterTaskIds(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BadRequestException("至少选择一个已完成的视频 AI 任务");
        }
        if (taskIds.size() > MAX_CHAPTER_VIDEO_TASKS) {
            throw new BadRequestException("章级综合测试最多聚合二十个视频 AI 任务");
        }
        if (taskIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("视频 AI 任务 ID 不能为空");
        }
        return taskIds.stream().distinct().sorted().toList();
    }

    /**
     * 查询并校验章级综合测试所使用的视频任务
     *
     * @param taskIds 规范化后的视频任务 ID
     * @param courseId 课程 ID
     * @param userId 当前用户 ID
     * @return 已完成且属于当前用户和课程的视频任务
     */
    private List<VideoAiTask> requireChapterQuizTasks(List<Long> taskIds, Long courseId, Long userId) {
        List<VideoAiTask> queriedTasks = baseMapper.selectByIds(taskIds);
        Map<Long, VideoAiTask> taskMap = new HashMap<>();
        if (queriedTasks != null) {
            for (VideoAiTask task : queriedTasks) {
                if (task != null && task.getId() != null) {
                    taskMap.put(task.getId(), task);
                }
            }
        }
        if (taskMap.size() != taskIds.size()) {
            throw new BadRequestException("部分视频 AI 任务不存在");
        }
        List<VideoAiTask> tasks = new ArrayList<>(taskIds.size());
        for (Long taskId : taskIds) {
            VideoAiTask task = taskMap.get(taskId);
            if (!Objects.equals(task.getCreater(), userId)) {
                throw new ForbiddenException("无权使用其他用户的视频 AI 任务");
            }
            if (!Objects.equals(task.getCourseId(), courseId)) {
                throw new BadRequestException("视频 AI 任务不属于指定课程");
            }
            if (!VideoAiTaskStatus.COMPLETED.name().equals(task.getStatus())) {
                throw new BadRequestException("只有已完成的视频 AI 任务才能生成章级综合测试");
            }
            if (StringUtils.isBlank(task.getResultVersion())) {
                throw new BadRequestException("已完成的视频 AI 任务缺少结果版本");
            }
            tasks.add(task);
        }
        return tasks;
    }

    /**
     * 校验章节和目标综合测试目录的关系
     *
     * @param dto 章级综合测试草稿参数
     * @param chapter 章节目录
     * @param target 目标测试目录
     */
    private void validateChapterQuizCatalogues(ChapterVideoQuizDraftCreateDTO dto,
                                               CatalogueDetailDTO chapter,
                                               CatalogueDetailDTO target) {
        if (chapter == null || !Objects.equals(chapter.getType(), CATALOGUE_CHAPTER)) {
            throw new BadRequestException("章级综合测试范围必须是章节目录");
        }
        if (!Objects.equals(chapter.getCourseId(), dto.getCourseId())) {
            throw new BadRequestException("章节不属于指定课程");
        }
        if (target == null || !Objects.equals(target.getType(), CATALOGUE_PRACTICE)) {
            throw new BadRequestException("目标目录必须是练习或测验目录");
        }
        if (!Objects.equals(target.getCourseId(), dto.getCourseId())
                || !Objects.equals(target.getParentCatalogueId(), dto.getChapterId())) {
            throw new BadRequestException("章级综合测试目录必须挂在指定章节下");
        }
    }

    /**
     * 批量查询并校验视频任务对应的小节
     *
     * @param tasks 视频 AI 任务
     * @param courseId 课程 ID
     * @param chapterId 章节 ID
     * @return 小节 ID与目录详情的映射
     */
    private Map<Long, CatalogueDetailDTO> requireChapterSections(List<VideoAiTask> tasks,
                                                                  Long courseId,
                                                                  Long chapterId) {
        if (tasks.stream().anyMatch(task -> task.getSectionId() == null)) {
            throw new BadRequestException("视频 AI 任务缺少有效的小节目录");
        }
        List<Long> sectionIds = tasks.stream()
                .map(VideoAiTask::getSectionId)
                .distinct()
                .toList();
        List<CatalogueDetailDTO> details = catalogueClient.queryCatalogueDetails(sectionIds);
        Map<Long, CatalogueDetailDTO> sectionMap = new HashMap<>();
        if (details != null) {
            for (CatalogueDetailDTO detail : details) {
                if (detail != null && detail.getId() != null) {
                    sectionMap.put(detail.getId(), detail);
                }
            }
        }
        if (sectionMap.size() != sectionIds.size()) {
            throw new BadRequestException("部分视频 AI 任务对应的小节不存在");
        }
        for (Long sectionId : sectionIds) {
            CatalogueDetailDTO section = sectionMap.get(sectionId);
            if (!Objects.equals(section.getCourseId(), courseId)) {
                throw new BadRequestException("视频 AI 任务对应的小节不属于指定课程");
            }
            if (!Objects.equals(section.getType(), CATALOGUE_SECTION)) {
                throw new BadRequestException("章级综合测试只能聚合小节目录的视频结果");
            }
            if (!Objects.equals(section.getParentCatalogueId(), chapterId)) {
                throw new BadRequestException("视频 AI 任务对应的小节不属于指定章节");
            }
        }
        return sectionMap;
    }

    /**
     * 按章节内小节顺序排列视频任务
     *
     * @param tasks 视频 AI 任务
     * @param sectionMap 小节目录映射
     * @return 排序后的视频任务
     */
    private List<VideoAiTask> orderChapterTasks(List<VideoAiTask> tasks,
                                                 Map<Long, CatalogueDetailDTO> sectionMap) {
        return tasks.stream()
                .sorted(Comparator
                        .comparing((VideoAiTask task) -> sectionMap.get(task.getSectionId()).getCIndex(),
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(VideoAiTask::getSectionId)
                        .thenComparing(VideoAiTask::getId))
                .toList();
    }

    /**
     * 聚合章内多个视频的摘要材料
     *
     * @param tasks 排序后的视频 AI 任务
     * @param sectionMap 小节目录映射
     * @return 长度受限的章级出题材料
     */
    private String buildChapterQuizMaterial(List<VideoAiTask> tasks,
                                            Map<Long, CatalogueDetailDTO> sectionMap) {
        if (tasks == null || tasks.isEmpty()) {
            throw new BadRequestException("至少需要一个视频 AI 任务才能生成章级综合测试草稿");
        }
        StringBuilder material = new StringBuilder();
        int maxLength = Math.max(1, Math.min(properties.getMaterialMaxChars(), 20000));
        int separatorLength = Math.max(0, (tasks.size() - 1) * 2);
        int availableLength = maxLength - separatorLength;
        if (availableLength < tasks.size()) {
            throw new BadRequestException("视频出题材料长度配置过小，无法覆盖全部视频");
        }
        int perTaskLength = availableLength / tasks.size();
        for (VideoAiTask task : tasks) {
            CatalogueDetailDTO section = sectionMap.get(task.getSectionId());
            String taskMaterial = buildChapterTaskMaterial(task, section);
            if (taskMaterial.length() > perTaskLength) {
                taskMaterial = safeSubstring(taskMaterial, perTaskLength);
            }
            appendMaterial(material, taskMaterial);
        }
        if (material.isEmpty()) {
            throw new BadRequestException("视频分析结果不足，无法生成章级综合测试草稿");
        }
        return material.toString();
    }

    /**
     * 构造单个视频在章级综合测试中的摘要材料
     *
     * @param task 视频 AI 任务
     * @param section 小节目录
     * @return 单个视频摘要材料
     */
    private String buildChapterTaskMaterial(VideoAiTask task, CatalogueDetailDTO section) {
        StringBuilder body = new StringBuilder();
        appendTitledText(body, "视频简介", task.getVideoIntroduction());
        appendTitledText(body, "核心内容", task.getCoreContent());
        appendStringList(body, "关键结论", task.getKeyConclusions());
        appendKnowledgePoints(body, task.getKnowledgePoints());
        appendSectionSummaries(body, task.getSectionSummaries());
        if (body.isEmpty()) {
            throw new BadRequestException("视频 AI 任务 " + task.getId() + " 的分析结果不足");
        }
        String sectionName = section.getName() == null ? "未命名小节" : section.getName();
        String mediaName = task.getMediaName() == null ? "未命名视频" : task.getMediaName();
        return "【小节：" + sectionName + "；视频：" + mediaName + "】\n" + body;
    }

    /**
     * 提取章级聚合材料中的不重复知识点名称
     *
     * @param tasks 排序后的视频 AI 任务
     * @return 最多三十个知识点名称
     */
    private List<String> extractChapterKnowledgePointNames(List<VideoAiTask> tasks) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (VideoAiTask task : tasks) {
            if (task.getKnowledgePoints() == null) {
                continue;
            }
            task.getKnowledgePoints().stream()
                    .map(VideoKnowledgePoint::getName)
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .forEach(names::add);
            if (names.size() >= MAX_KNOWLEDGE_POINTS) {
                break;
            }
        }
        return names.stream().limit(MAX_KNOWLEDGE_POINTS).toList();
    }

    /**
     * 构造章级视频摘要来源 ID
     *
     * @param chapterId 章节 ID
     * @param tasks 排序后的视频 AI 任务
     * @return 确定性的来源 ID
     */
    private String buildChapterQuizSourceId(Long chapterId, List<VideoAiTask> tasks) {
        String taskIds = tasks.stream()
                .sorted(Comparator.comparing(VideoAiTask::getId))
                .map(VideoAiTask::getId)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return "chapter-" + chapterId + "-videos-" + DigestUtil.sha256Hex(taskIds).substring(0, 32);
    }

    /**
     * 构造章级视频摘要来源版本
     *
     * @param tasks 排序后的视频 AI 任务
     * @return 包含任务结果版本的来源版本
     */
    private String buildChapterQuizSourceVersion(List<VideoAiTask> tasks) {
        String versions = tasks.stream()
                .sorted(Comparator.comparing(VideoAiTask::getId))
                .map(task -> task.getId() + "=" + task.getResultVersion())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        String prefix = "chapter-video-v1-";
        return prefix + DigestUtil.sha256Hex(versions)
                .substring(0, MAX_SOURCE_VERSION_LENGTH - prefix.length());
    }

    /**
     * 构造章级综合测试草稿的幂等键
     *
     * @param dto 章级综合测试草稿参数
     * @param tasks 排序后的视频 AI 任务
     * @param sourceVersion 聚合结果版本
     * @return 长度不超过六十四的幂等键
     */
    private String buildChapterQuizRequestId(ChapterVideoQuizDraftCreateDTO dto,
                                              List<VideoAiTask> tasks,
                                              String sourceVersion) {
        String requestId = trimToNull(dto.getRequestId());
        if (requestId != null) {
            return requestId;
        }
        String taskVersions = tasks.stream()
                .sorted(Comparator.comparing(VideoAiTask::getId))
                .map(task -> task.getId() + "=" + task.getResultVersion())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        String fingerprintSource = dto.getCourseId() + "|" + dto.getChapterId() + "|" + taskVersions + "|"
                + sourceVersion + "|" + dto.getTargetBizId() + "|" + JsonUtils.toJsonStr(dto.getQuestionTypes()) + "|"
                + dto.getQuestionCount() + "|" + dto.getDifficulty() + "|" + dto.getScore();
        String prefix = "chapter-video-quiz-";
        return prefix + DigestUtil.sha256Hex(fingerprintSource).substring(0, 64 - prefix.length());
    }

    /**
     * 按客户端请求 ID 或媒资配置查询已有任务
     *
     * @param userId 当前用户 ID
     * @param mediaId 媒资 ID
     * @param requestId 客户端幂等键
     * @return 已有任务，不存在时返回 null
     */
    private VideoAiTask findExistingTask(Long userId, Long mediaId, String requestId) {
        if (requestId != null) {
            VideoAiTask byRequest = baseMapper.selectOne(Wrappers.<VideoAiTask>lambdaQuery()
                    .eq(VideoAiTask::getCreater, userId)
                    .eq(VideoAiTask::getRequestId, requestId));
            if (byRequest != null) {
                return byRequest;
            }
        }
        return baseMapper.selectOne(Wrappers.<VideoAiTask>lambdaQuery()
                .eq(VideoAiTask::getCreater, userId)
                .eq(VideoAiTask::getMediaId, mediaId)
                .eq(VideoAiTask::getConfigVersion, properties.getConfigVersion()));
    }

    /**
     * 校验幂等命中的任务与本次范围一致
     *
     * @param existing 已有任务
     * @param dto 本次创建参数
     */
    private void validateExistingScope(VideoAiTask existing, VideoAiTaskCreateDTO dto) {
        if (!Objects.equals(existing.getCourseId(), dto.getCourseId())
                || !Objects.equals(existing.getSectionId(), dto.getSectionId())
                || !Objects.equals(existing.getMediaId(), dto.getMediaId())) {
            throw new BadRequestException("幂等键或媒资已关联其他课程小节，不能重复创建任务");
        }
    }

    /**
     * 校验课程存在且当前用户是课程创建者
     *
     * @param courseId 课程 ID
     * @param userId 当前用户 ID
     * @return 课程基础信息
     */
    private CourseBaseInfoDTO requireOperableCourse(Long courseId, Long userId) {
        CourseBaseInfoDTO course = courseClient.baseInfo(courseId, true);
        if (course == null || course.getId() == null) {
            throw new BadRequestException("课程不存在");
        }
        if (!Objects.equals(course.getCreater(), userId)) {
            throw new ForbiddenException("无权对该课程执行视频 AI 处理");
        }
        return course;
    }

    /**
     * 校验小节与课程、媒资的关联关系
     *
     * @param dto 创建参数
     * @param section 小节目录信息
     */
    private void validateSection(VideoAiTaskCreateDTO dto, CatalogueDetailDTO section) {
        if (section == null || !Objects.equals(section.getType(), CATALOGUE_SECTION)) {
            throw new BadRequestException("视频 AI 任务必须关联课程小节");
        }
        if (!Objects.equals(section.getCourseId(), dto.getCourseId())) {
            throw new BadRequestException("小节不属于指定课程");
        }
        Long sectionMediaId = section.getMediaId() != null ? section.getMediaId() : section.getVideoId();
        if (!Objects.equals(sectionMediaId, dto.getMediaId())) {
            throw new BadRequestException("媒资与指定课程小节不匹配");
        }
    }

    /**
     * 校验媒资状态、格式、时长、大小和访问地址
     *
     * @param mediaId 媒资 ID
     * @param media 媒资信息
     */
    private void validateMedia(Long mediaId, MediaAiInfoDTO media) {
        if (media == null || !Objects.equals(media.getMediaId(), mediaId)) {
            throw new BadRequestException("媒资不存在");
        }
        if (!Objects.equals(media.getStatus(), MEDIA_UPLOADED)
                && !Objects.equals(media.getStatus(), MEDIA_PROCESSED)) {
            throw new BadRequestException("媒资尚未上传完成，不能执行视频 AI 处理");
        }
        if (media.getMediaUrl() == null || media.getMediaUrl().isBlank()) {
            throw new BadRequestException("媒资缺少可供视频转写访问的地址");
        }
        if (media.getSize() == null || media.getSize() <= 0 || media.getSize() > properties.getMaxSizeBytes()) {
            throw new BadRequestException("媒资大小不符合视频 AI 处理限制");
        }
        if (media.getDuration() == null) {
            throw new BadRequestException("媒资时长不符合视频 AI 处理限制");
        }
        double durationSeconds = media.getDuration().doubleValue();
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0
                || durationSeconds > properties.getMaxDurationSeconds()) {
            throw new BadRequestException("媒资时长不符合视频 AI 处理限制");
        }
        String extension = resolveMediaExtension(media.getFilename(), media.getMediaUrl());
        if (!isAcceptedExtension(extension)) {
            throw new BadRequestException("暂不支持该媒资格式：" + extension);
        }
    }

    /**
     * 将媒资时长转换为毫秒，并避免浮点异常值或整数溢出进入任务表。
     *
     * @param durationSeconds 媒资时长，单位秒
     * @return 媒资时长，单位毫秒
     */
    private long toDurationMillis(Float durationSeconds) {
        if (durationSeconds == null || !Float.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new BadRequestException("媒资时长不符合视频 AI 处理限制");
        }
        double durationMillis = durationSeconds.doubleValue() * 1000D;
        if (!Double.isFinite(durationMillis) || durationMillis < 1D
                || durationMillis > Long.MAX_VALUE) {
            throw new BadRequestException("媒资时长超出视频 AI 处理范围");
        }
        long result = Math.round(durationMillis);
        if (result <= 0) {
            throw new BadRequestException("媒资时长不符合视频 AI 处理限制");
        }
        return result;
    }

    /**
     * 判断文件扩展名是否在配置允许范围内。
     *
     * @param extension 文件扩展名
     * @return 是否允许
     */
    private boolean isAcceptedExtension(String extension) {
        if (extension == null || extension.isBlank() || properties.getAcceptedExtensions() == null) {
            return false;
        }
        return properties.getAcceptedExtensions().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(value -> value.startsWith(".") ? value.substring(1) : value)
                .anyMatch(value -> value.equalsIgnoreCase(extension));
    }

    /**
     * 校验测验目录与视频小节位于同一课程章节
     *
     * @param task 视频 AI 任务
     * @param targetBizId 测验目录 ID
     */
    private void validateQuizTarget(VideoAiTask task, Long targetBizId) {
        CatalogueDetailDTO section = catalogueClient.queryCatalogueDetail(task.getSectionId());
        CatalogueDetailDTO target = catalogueClient.queryCatalogueDetail(targetBizId);
        if (target == null || !Objects.equals(target.getType(), CATALOGUE_PRACTICE)) {
            throw new BadRequestException("目标目录必须是练习或测验目录");
        }
        if (section == null || !Objects.equals(section.getCourseId(), task.getCourseId())
                || !Objects.equals(target.getCourseId(), task.getCourseId())
                || !Objects.equals(section.getParentCatalogueId(), target.getParentCatalogueId())) {
            throw new BadRequestException("测验目录必须与视频小节位于同一课程章节");
        }
    }

    /**
     * 构造视频测验所使用的确定性材料文本
     *
     * @param task 视频 AI 任务
     * @return 长度受限的出题材料
     */
    private String buildQuizMaterial(VideoAiTask task) {
        StringBuilder material = new StringBuilder();
        appendTitledText(material, "视频简介", task.getVideoIntroduction());
        appendTitledText(material, "核心内容", task.getCoreContent());
        appendStringList(material, "关键结论", task.getKeyConclusions());
        appendKnowledgePoints(material, task.getKnowledgePoints());
        appendSectionSummaries(material, task.getSectionSummaries());
        appendTranscriptSegments(material, task.getTranscriptSegments());
        if (material.isEmpty()) {
            throw new BadRequestException("视频分析结果不足，无法生成测验草稿");
        }
        return material.toString();
    }

    /**
     * 追加带标题的非空文本到出题材料。
     *
     * @param material 材料缓冲区
     * @param title 材料标题
     * @param value 材料正文
     */
    private void appendTitledText(StringBuilder material, String title, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        appendMaterial(material, "【" + title + "】\n" + value.trim());
    }
    /**
     * 追加普通字符串列表到出题材料
     *
     * @param material 材料缓冲区
     * @param title 标题
     * @param values 字符串列表
     */
    private void appendStringList(StringBuilder material, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        StringBuilder section = new StringBuilder("【").append(title).append("】\n");
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                section.append("- ").append(value.trim()).append('\n');
            }
        }
        if (section.length() > title.length() + 3) {
            appendMaterial(material, section.toString());
        }
    }
    /**
     * 追加知识点到出题材料
     *
     * @param material 材料缓冲区
     * @param points 视频知识点
     */
    private void appendKnowledgePoints(StringBuilder material, List<VideoKnowledgePoint> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        StringBuilder section = new StringBuilder("【知识点】\n");
        for (VideoKnowledgePoint point : points) {
            if (point == null || point.getName() == null || point.getName().isBlank()) {
                continue;
            }
            section.append("- ").append(point.getName().trim());
            if (point.getDescription() != null && !point.getDescription().isBlank()) {
                section.append("：").append(point.getDescription().trim());
            }
            if (point.getStartMs() != null && point.getEndMs() != null) {
                section.append("（").append(point.getStartMs()).append('-')
                        .append(point.getEndMs()).append("毫秒）");
            }
            section.append('\n');
        }
        if (section.length() > 7) {
            appendMaterial(material, section.toString());
        }
    }
    /**
     * 追加分段摘要到出题材料
     *
     * @param material 材料缓冲区
     * @param summaries 分段摘要
     */
    private void appendSectionSummaries(StringBuilder material, List<VideoSectionSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        StringBuilder section = new StringBuilder("【分段摘要】\n");
        for (VideoSectionSummary summary : summaries) {
            if (summary == null || summary.getSummary() == null || summary.getSummary().isBlank()) {
                continue;
            }
            section.append("- ");
            if (summary.getStartMs() != null && summary.getEndMs() != null) {
                section.append('[').append(summary.getStartMs()).append('-').append(summary.getEndMs())
                        .append("毫秒] ");
            }
            if (summary.getTitle() != null && !summary.getTitle().isBlank()) {
                section.append(summary.getTitle().trim()).append("：");
            }
            section.append(summary.getSummary().trim()).append('\n');
        }
        if (section.length() > 9) {
            appendMaterial(material, section.toString());
        }
    }
    /**
     * 追加代表性转写句段到出题材料
     *
     * @param material 材料缓冲区
     * @param segments 转写句段
     */
    private void appendTranscriptSegments(StringBuilder material, List<TranscriptSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }
        boolean titleAdded = false;
        for (TranscriptSegment segment : segments) {
            if (segment == null || segment.getText() == null || segment.getText().isBlank()) {
                continue;
            }
            if (!titleAdded) {
                appendMaterial(material, "【转写片段】");
                titleAdded = true;
            }
            String timeRange = segment.getStartMs() == null || segment.getEndMs() == null
                    ? ""
                    : "[" + segment.getStartMs() + "-" + segment.getEndMs() + "毫秒] ";
            if (!appendMaterial(material, timeRange + segment.getText().trim())) {
                break;
            }
        }
    }
    /**
     * 在总长度限制内追加一段材料
     *
     * @param material 材料缓冲区
     * @param section 待追加内容
     * @return 是否完整追加
     */
    private boolean appendMaterial(StringBuilder material, String section) {
        if (section == null || section.isBlank()) {
            return true;
        }
        int maxLength = Math.max(1, Math.min(properties.getMaterialMaxChars(), 20000));
        int separatorLength = material.isEmpty() ? 0 : 2;
        int remaining = maxLength - material.length() - separatorLength;
        if (remaining <= 0) {
            return false;
        }
        if (separatorLength > 0) {
            material.append("\n\n");
        }
        String normalized = section.trim();
        if (normalized.length() <= remaining) {
            material.append(normalized);
            return true;
        }
        material.append(safeSubstring(normalized, remaining));
        return false;
    }

    /**
     * 在指定长度内安全截断文本，避免拆分 Unicode 代理字符
     *
     * @param value 原始文本
     * @param maxLength 最大 UTF-16 字符长度
     * @return 截断后的文本
     */
    private String safeSubstring(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        int endIndex = Math.min(value.length(), maxLength);
        if (endIndex > 0 && endIndex < value.length()
                && Character.isHighSurrogate(value.charAt(endIndex - 1))
                && Character.isLowSurrogate(value.charAt(endIndex))) {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }

    /**
     * 提取不重复的知识点名称
     *
     * @param points 视频知识点
     * @return 最多三十个知识点名称
     */
    private List<String> extractKnowledgePointNames(List<VideoKnowledgePoint> points) {
        if (points == null) {
            return new ArrayList<>();
        }
        return points.stream()
                .map(VideoKnowledgePoint::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .limit(30)
                .toList();
    }

    /**
     * 构造视频测验创建请求的幂等键
     *
     * @param task 视频 AI 任务
     * @param dto 测验创建参数
     * @return 长度不超过 64 的幂等键
     */
    private String buildQuizRequestId(VideoAiTask task, VideoQuizDraftCreateDTO dto) {
        String requestId = trimToNull(dto.getRequestId());
        if (requestId != null) {
            return requestId;
        }
        String fingerprintSource = task.getId() + "|" + task.getResultVersion() + "|"
                + dto.getTargetBizId() + "|" + JsonUtils.toJsonStr(dto.getQuestionTypes()) + "|"
                + dto.getQuestionCount() + "|" + dto.getDifficulty() + "|" + dto.getScore();
        return "video-quiz-" + DigestUtil.sha256Hex(fingerprintSource).substring(0, 53);
    }

    /**
     * 规范化并校验视频 AI 任务状态筛选条件
     *
     * @param status 原始状态
     * @return 规范化状态；未传入时返回 null
     */
    private String normalizeTaskStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        String upperStatus = normalized.toUpperCase(Locale.ROOT);
        boolean valid = Arrays.stream(VideoAiTaskStatus.values())
                .anyMatch(item -> item.name().equals(upperStatus));
        if (!valid) {
            throw new BadRequestException("视频 AI 任务状态不合法");
        }
        return upperStatus;
    }

    /**
     * 查询并校验当前用户拥有的任务
     *
     * @param id 任务 ID
     * @return 视频 AI 任务
     */
    private VideoAiTask requireOwnedTask(Long id) {
        Long userId = requireCurrentUser();
        VideoAiTask task = getById(id);
        if (task == null) {
            throw new BadRequestException("视频 AI 任务不存在");
        }
        if (!userId.equals(task.getCreater())) {
            throw new ForbiddenException("无权操作该视频 AI 任务");
        }
        return task;
    }

    /**
     * 获取当前登录用户
     *
     * @return 当前用户 ID
     */
    private Long requireCurrentUser() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }

    /**
     * 提取小写文件扩展名
     *
     * @param filename 文件名
     * @return 文件扩展名
     */
    private String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "";
        }
        String normalized = filename.trim();
        int slashIndex = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= slashIndex || dotIndex == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 去除字符串首尾空格，并将空白字符串转换为 null
     *
     * @param value 原始字符串
     * @return 规范化字符串
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    /**
     * 获取媒资文件扩展名。
     * 文件名没有后缀时，从媒资地址的 URL path 中提取扩展名，避免域名中的点号被误识别为后缀。
     *
     * @param filename 文件名
     * @param mediaUrl 媒资访问地址
     * @return 小写文件扩展名
     */
    private String resolveMediaExtension(String filename, String mediaUrl) {
        String extension = extractExtension(filename);
        if (!extension.isBlank() || mediaUrl == null || mediaUrl.isBlank()) {
            return extension;
        }
        try {
            URI uri = URI.create(mediaUrl.trim());
            return extractExtension(uri.getPath());
        } catch (IllegalArgumentException e) {
            // 媒资地址格式异常时返回空扩展名，继续由统一的格式校验返回业务提示。
            return "";
        }
    }

}
