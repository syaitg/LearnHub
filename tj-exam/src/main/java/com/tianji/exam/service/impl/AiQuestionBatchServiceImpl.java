package com.tianji.exam.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.exceptions.UnauthorizedException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.exam.domain.dto.AiQuestionBatchConfirmDTO;
import com.tianji.exam.domain.dto.AiQuestionBatchCreateDTO;
import com.tianji.exam.domain.event.AiQuestionGenerationRequestedEvent;
import com.tianji.exam.domain.po.AiQuestionBatch;
import com.tianji.exam.domain.po.AiQuestionDraft;
import com.tianji.exam.domain.po.AiQuestionOrigin;
import com.tianji.exam.domain.po.Question;
import com.tianji.exam.domain.po.QuestionBiz;
import com.tianji.exam.domain.po.QuestionDetail;
import com.tianji.exam.domain.query.AiQuestionBatchPageQuery;
import com.tianji.exam.domain.vo.AiQuestionBatchVO;
import com.tianji.exam.domain.vo.AiQuestionDraftVO;
import com.tianji.exam.enums.AiQuestionBatchStatus;
import com.tianji.exam.enums.AiQuestionDraftStatus;
import com.tianji.exam.enums.AiQuestionScopeType;
import com.tianji.exam.enums.AiQuestionSourceType;
import com.tianji.exam.mapper.AiQuestionBatchMapper;
import com.tianji.exam.service.IAiQuestionBatchService;
import com.tianji.exam.service.IAiQuestionDraftService;
import com.tianji.exam.service.IAiQuestionOriginService;
import com.tianji.exam.service.IQuestionBizService;
import com.tianji.exam.service.IQuestionDetailService;
import com.tianji.exam.service.IQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 出题批次服务实现
 */
@Service
@RequiredArgsConstructor
public class AiQuestionBatchServiceImpl extends ServiceImpl<AiQuestionBatchMapper, AiQuestionBatch>
        implements IAiQuestionBatchService {

    private static final int CATALOGUE_CHAPTER = 1;
    private static final int CATALOGUE_SECTION = 2;
    private static final int CATALOGUE_PRACTICE = 3;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final CourseClient courseClient;
    private final CatalogueClient catalogueClient;
    private final IAiQuestionDraftService draftService;
    private final IQuestionService questionService;
    private final IQuestionDetailService questionDetailService;
    private final IQuestionBizService questionBizService;
    private final IAiQuestionOriginService originService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建 AI 出题批次并异步生成题目草稿
     *
     * @param dto 创建参数
     * @return 批次 ID
     */
    @Override
    @Transactional
    public Long createBatch(AiQuestionBatchCreateDTO dto) {
        Long userId = requireCurrentUser();
        normalizeCreateRequest(dto);
        AiQuestionScopeType scopeType = requireScopeType(dto.getScopeType());
        AiQuestionSourceType sourceType = requireSourceType(dto.getSourceType());
        validateSource(dto, sourceType);
        validateQuestionTypes(dto.getQuestionTypes());

        String requestFingerprint = buildRequestFingerprint(dto);
        AiQuestionBatch existing = findByRequestId(userId, dto.getRequestId());
        if (existing != null) {
            validateIdempotentRequest(existing, requestFingerprint);
            return existing.getId();
        }

        CourseBaseInfoDTO course = requireOperableCourse(dto.getCourseId(), userId);
        CatalogueDetailDTO scope = requireCatalogue(dto.getScopeId(), dto.getCourseId(), "题目范围目录不存在");
        CatalogueDetailDTO target = requireCatalogue(dto.getTargetBizId(), dto.getCourseId(), "目标练习目录不存在");
        validateCatalogueRelation(scopeType, scope, target);
        List<CatalogueDetailDTO> scopeSections = requireScopeSections(
                scopeType, scope, dto.getScopeSectionIds(), dto.getCourseId());

        LocalDateTime now = LocalDateTime.now();
        AiQuestionBatch batch = new AiQuestionBatch()
                .setId(IdWorker.getId())
                .setCourseId(course.getId())
                .setCourseName(course.getName())
                .setScopeType(scopeType.name())
                .setScopeId(scope.getId())
                .setScopeName(scope.getName())
                .setScopeSectionIds(scopeSections.stream().map(CatalogueDetailDTO::getId).toList())
                .setScopeSectionNames(scopeSections.stream().map(CatalogueDetailDTO::getName).toList())
                .setSourceType(sourceType.name())
                .setSourceId(dto.getSourceId())
                .setSourceVersion(dto.getSourceVersion())
                .setTargetBizId(target.getId())
                .setKnowledgePoints(dto.getKnowledgePoints())
                .setMaterialText(dto.getMaterialText())
                .setQuestionTypes(dto.getQuestionTypes())
                .setQuestionCount(dto.getQuestionCount())
                .setDifficulty(dto.getDifficulty())
                .setScore(dto.getScore())
                .setCateId1(course.getFirstCateId())
                .setCateId2(course.getSecondCateId())
                .setCateId3(course.getThirdCateId())
                .setStatus(AiQuestionBatchStatus.CREATED.name())
                .setTotalCount(0)
                .setValidCount(0)
                .setDuplicateCount(0)
                .setPublishedCount(0)
                .setRetryCount(0)
                .setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT)
                .setRequestId(dto.getRequestId())
                .setRequestFingerprint(requestFingerprint)
                .setCreateTime(now)
                .setUpdateTime(now)
                .setCreater(userId)
                .setUpdater(userId);

        int inserted = dto.getRequestId() == null ? baseMapper.insert(batch) : baseMapper.insertIgnore(batch);
        if (inserted != 1) {
            AiQuestionBatch concurrent = findByRequestId(userId, dto.getRequestId());
            if (concurrent == null) {
                throw new DbException("AI 出题批次创建失败");
            }
            validateIdempotentRequest(concurrent, requestFingerprint);
            return concurrent.getId();
        }
        eventPublisher.publishEvent(new AiQuestionGenerationRequestedEvent(batch.getId()));
        return batch.getId();
    }

    /**
     * 分页查询 AI 出题批次
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Override
    public PageDTO<AiQuestionBatchVO> queryPage(AiQuestionBatchPageQuery query) {
        Long userId = requireCurrentUser();
        normalizePageQuery(query);
        Page<AiQuestionBatch> page = query.toMpPageDefaultSortByCreateTimeDesc();
        lambdaQuery()
                .eq(AiQuestionBatch::getCreater, userId)
                .eq(query.getCourseId() != null, AiQuestionBatch::getCourseId, query.getCourseId())
                .eq(query.getScopeId() != null, AiQuestionBatch::getScopeId, query.getScopeId())
                .eq(query.getTargetBizId() != null, AiQuestionBatch::getTargetBizId, query.getTargetBizId())
                .eq(StringUtils.isNotBlank(query.getScopeType()), AiQuestionBatch::getScopeType, query.getScopeType())
                .eq(StringUtils.isNotBlank(query.getSourceType()), AiQuestionBatch::getSourceType, query.getSourceType())
                .eq(StringUtils.isNotBlank(query.getStatus()), AiQuestionBatch::getStatus, query.getStatus())
                .page(page);
        return PageDTO.of(page, AiQuestionBatchVO.class);
    }

    /**
     * 查询 AI 出题批次详情
     *
     * @param id 批次 ID
     * @return 批次详情
     */
    @Override
    public AiQuestionBatchVO queryDetail(Long id) {
        AiQuestionBatch batch = requireOwnedBatch(id, requireCurrentUser());
        AiQuestionBatchVO vo = BeanUtils.copyBean(batch, AiQuestionBatchVO.class);
        List<AiQuestionDraft> drafts = draftService.lambdaQuery()
                .eq(AiQuestionDraft::getBatchId, id)
                .orderByAsc(AiQuestionDraft::getSequenceNo)
                .list();
        vo.setDrafts(BeanUtils.copyList(drafts, AiQuestionDraftVO.class));
        return vo;
    }

    /**
     * 确认 AI 题目草稿
     *
     * @param batchId 批次 ID
     * @param dto 确认参数
     */
    @Override
    @Transactional
    public void confirmDrafts(Long batchId, AiQuestionBatchConfirmDTO dto) {
        Long userId = requireCurrentUser();
        AiQuestionBatch batch = lockOwnedBatch(batchId, userId);
        requireBatchStatus(batch, "当前批次状态不允许确认草稿",
                AiQuestionBatchStatus.PENDING_CONFIRMATION,
                AiQuestionBatchStatus.PARTIALLY_PUBLISHED);

        List<AiQuestionDraft> drafts = draftService.lambdaQuery()
                .eq(AiQuestionDraft::getBatchId, batchId)
                .orderByAsc(AiQuestionDraft::getSequenceNo)
                .list();
        List<Long> requestedIds = dto == null ? null : dto.getDraftIds();
        List<AiQuestionDraft> selected = selectDraftsForConfirmation(drafts, requestedIds);
        LocalDateTime now = LocalDateTime.now();
        List<AiQuestionDraft> updates = new ArrayList<>();
        for (AiQuestionDraft draft : selected) {
            if (AiQuestionDraftStatus.CONFIRMED.name().equals(draft.getStatus())) {
                continue;
            }
            if (!AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(draft.getStatus())) {
                throw new BadRequestException("题目草稿 " + draft.getId() + " 当前状态不允许确认");
            }
            updates.add(new AiQuestionDraft()
                    .setId(draft.getId())
                    .setStatus(AiQuestionDraftStatus.CONFIRMED.name())
                    .setConfirmedBy(userId)
                    .setConfirmedTime(now)
                    .setUpdater(userId)
                    .setUpdateTime(now));
        }
        if (!updates.isEmpty() && !draftService.updateBatchById(updates)) {
            throw new DbException("AI 题目草稿确认失败");
        }
        refreshBatchStatistics(batchId, userId);
    }

    /**
     * 发布已确认的 AI 题目
     *
     * @param batchId 批次 ID
     * @return 已发布题目 ID 列表
     */
    @Override
    @Transactional
    public List<Long> publish(Long batchId) {
        Long userId = requireCurrentUser();
        AiQuestionBatch batch = lockOwnedBatch(batchId, userId);
        requireBatchStatus(batch, "当前批次状态不允许发布题目",
                AiQuestionBatchStatus.PENDING_CONFIRMATION,
                AiQuestionBatchStatus.PARTIALLY_PUBLISHED,
                AiQuestionBatchStatus.PUBLISHED);

        List<AiQuestionDraft> confirmedDrafts = draftService.lambdaQuery()
                .eq(AiQuestionDraft::getBatchId, batchId)
                .eq(AiQuestionDraft::getStatus, AiQuestionDraftStatus.CONFIRMED.name())
                .orderByAsc(AiQuestionDraft::getSequenceNo)
                .list();
        if (!confirmedDrafts.isEmpty()) {
            revalidatePublishContext(batch, userId);
            publishConfirmedDrafts(batch, confirmedDrafts, userId);
            refreshBatchStatistics(batchId, userId);
        }
        List<Long> publishedIds = draftService.lambdaQuery()
                .eq(AiQuestionDraft::getBatchId, batchId)
                .eq(AiQuestionDraft::getStatus, AiQuestionDraftStatus.PUBLISHED.name())
                .orderByAsc(AiQuestionDraft::getSequenceNo)
                .list()
                .stream()
                .map(AiQuestionDraft::getQuestionId)
                .filter(Objects::nonNull)
                .toList();
        if (publishedIds.isEmpty()) {
            throw new BadRequestException("没有可发布的已确认题目");
        }
        return publishedIds;
    }

    /**
     * 重试生成失败的 AI 出题批次
     *
     * @param batchId 批次 ID
     */
    @Override
    @Transactional
    public void retry(Long batchId) {
        Long userId = requireCurrentUser();
        AiQuestionBatch batch = requireOwnedBatch(batchId, userId);
        if (!AiQuestionBatchStatus.GENERATION_FAILED.name().equals(batch.getStatus())) {
            throw new BadRequestException("当前批次不是生成失败状态，不能重试");
        }
        if (batch.getRetryCount() == null || batch.getMaxRetryCount() == null
                || batch.getRetryCount() >= batch.getMaxRetryCount()) {
            throw new BadRequestException("AI 出题批次已达到最大重试次数");
        }
        if (baseMapper.resetForRetry(batchId, userId, LocalDateTime.now()) != 1) {
            throw new BadRequestException("AI 出题批次重试失败，请稍后重试");
        }
        eventPublisher.publishEvent(new AiQuestionGenerationRequestedEvent(batchId));
    }


    /**
     * 规范化创建参数
     *
     * @param dto 创建参数
     */
    private void normalizeCreateRequest(AiQuestionBatchCreateDTO dto) {
        dto.setScopeType(trimToNull(dto.getScopeType()));
        dto.setSourceType(trimToNull(dto.getSourceType()));
        dto.setSourceId(trimToNull(dto.getSourceId()));
        dto.setSourceVersion(trimToNull(dto.getSourceVersion()));
        dto.setMaterialText(trimToNull(dto.getMaterialText()));
        dto.setRequestId(trimToNull(dto.getRequestId()));
        if (dto.getScopeSectionIds() != null) {
            if (dto.getScopeSectionIds().stream().anyMatch(Objects::isNull)) {
                throw new BadRequestException("章级出题选择的小节 ID 不能为空");
            }
            List<Long> sectionIds = dto.getScopeSectionIds().stream()
                    .distinct()
                    .sorted()
                    .toList();
            if (sectionIds.size() > 50) {
                throw new BadRequestException("章级出题选择的小节不能超过 50 个");
            }
            dto.setScopeSectionIds(sectionIds.isEmpty() ? null : sectionIds);
        }
        if (dto.getKnowledgePoints() != null) {
            dto.setKnowledgePoints(dto.getKnowledgePoints().stream()
                    .map(this::trimToNull)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());
        }
        if (dto.getQuestionTypes() != null) {
            dto.setQuestionTypes(new ArrayList<>(new LinkedHashSet<>(dto.getQuestionTypes())));
        }
    }

    /**
     * 规范化分页查询参数
     *
     * @param query 查询参数
     */
    private void normalizePageQuery(AiQuestionBatchPageQuery query) {
        if (StringUtils.isNotBlank(query.getScopeType())) {
            query.setScopeType(requireScopeType(query.getScopeType()).name());
        }
        if (StringUtils.isNotBlank(query.getSourceType())) {
            query.setSourceType(requireSourceType(query.getSourceType()).name());
        }
        if (StringUtils.isNotBlank(query.getStatus())) {
            query.setStatus(requireBatchStatusValue(query.getStatus()).name());
        }
    }

    /**
     * 校验题目范围类型
     *
     * @param value 范围类型值
     * @return 范围类型
     */
    private AiQuestionScopeType requireScopeType(String value) {
        AiQuestionScopeType type = AiQuestionScopeType.of(value);
        if (type == null) {
            throw new BadRequestException("题目范围类型必须是 SECTION 或 CHAPTER");
        }
        return type;
    }

    /**
     * 校验题目来源类型
     *
     * @param value 来源类型值
     * @return 来源类型
     */
    private AiQuestionSourceType requireSourceType(String value) {
        AiQuestionSourceType type = AiQuestionSourceType.of(value);
        if (type == null) {
            throw new BadRequestException("不支持的 AI 出题来源类型");
        }
        return type;
    }

    /**
     * 校验批次状态值
     *
     * @param value 状态值
     * @return 批次状态
     */
    private AiQuestionBatchStatus requireBatchStatusValue(String value) {
        for (AiQuestionBatchStatus status : AiQuestionBatchStatus.values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new BadRequestException("不支持的 AI 出题批次状态");
    }

    /**
     * 校验出题素材
     *
     * @param dto 创建参数
     * @param sourceType 出题来源类型
     */
    private void validateSource(AiQuestionBatchCreateDTO dto, AiQuestionSourceType sourceType) {
        if (sourceType == AiQuestionSourceType.MANUAL_TOPIC
                && (dto.getKnowledgePoints() == null || dto.getKnowledgePoints().isEmpty())) {
            throw new BadRequestException("手动主题出题必须提供知识点");
        }
        if (sourceType != AiQuestionSourceType.MANUAL_TOPIC && dto.getMaterialText() == null) {
            throw new BadRequestException("非手动主题出题必须提供素材文本");
        }
        if ((sourceType == AiQuestionSourceType.VIDEO_TRANSCRIPT
                || sourceType == AiQuestionSourceType.VIDEO_SUMMARY)
                && (dto.getSourceId() == null || dto.getSourceVersion() == null)) {
            throw new BadRequestException("视频来源必须提供 sourceId 和 sourceVersion");
        }
    }

    /**
     * 校验题型列表
     *
     * @param questionTypes 题型列表
     */
    private void validateQuestionTypes(List<Integer> questionTypes) {
        if (questionTypes == null || questionTypes.isEmpty()) {
            throw new BadRequestException("题型列表不能为空");
        }
        boolean invalid = questionTypes.stream().anyMatch(type -> type == null || type < 1 || type > 5);
        if (invalid) {
            throw new BadRequestException("题型必须在 1 到 5 范围内");
        }
    }

    /**
     * 校验课程是否允许创建 AI 题目
     *
     * @param courseId 课程 ID
     * @param userId 当前用户 ID
     * @return 课程信息
     */
    private CourseBaseInfoDTO requireOperableCourse(Long courseId, Long userId) {
        CourseBaseInfoDTO course = courseClient.baseInfo(courseId, true);
        if (course == null || course.getId() == null) {
            throw new BadRequestException("课程不存在");
        }
        if (!Objects.equals(course.getCreater(), userId)) {
            throw new ForbiddenException("无权为该课程创建 AI 题目");
        }
        if (course.getFirstCateId() == null || course.getSecondCateId() == null
                || course.getThirdCateId() == null) {
            throw new BadRequestException("课程分类信息不完整，无法创建题目");
        }
        return course;
    }

    /**
     * 查询并校验目录信息
     *
     * @param catalogueId 目录 ID
     * @param courseId 课程 ID
     * @param missingMessage 目录不存在时的提示
     * @return 目录信息
     */
    private CatalogueDetailDTO requireCatalogue(Long catalogueId, Long courseId, String missingMessage) {
        CatalogueDetailDTO catalogue = catalogueClient.queryCatalogueDetail(catalogueId);
        // 未上架课程的目录只存在于草稿表，AI 工作台仍需支持使用这些目录出题。
        if (catalogue == null || catalogue.getId() == null) {
            catalogue = catalogueClient.queryDraftCatalogueDetail(catalogueId);
        }
        if (catalogue == null || catalogue.getId() == null) {
            throw new BadRequestException(missingMessage);
        }
        if (!Objects.equals(catalogue.getCourseId(), courseId)) {
            throw new BadRequestException("目录不属于指定课程");
        }
        return catalogue;
    }

    /**
     * 校验出题范围与目标练习的目录关系
     *
     * @param scopeType 出题范围类型
     * @param scope 范围目录
     * @param target 目标练习目录
     */
    private void validateCatalogueRelation(AiQuestionScopeType scopeType,
                                           CatalogueDetailDTO scope,
                                           CatalogueDetailDTO target) {
        if (!Objects.equals(target.getType(), CATALOGUE_PRACTICE)) {
            throw new BadRequestException("目标目录必须是练习目录");
        }
        if (scopeType == AiQuestionScopeType.SECTION) {
            if (!Objects.equals(scope.getType(), CATALOGUE_SECTION)) {
                throw new BadRequestException("SECTION 范围必须选择小节目录");
            }
            if (!Objects.equals(scope.getParentCatalogueId(), target.getParentCatalogueId())) {
                throw new BadRequestException("小节与练习必须属于同一章节");
            }
            return;
        }
        if (!Objects.equals(scope.getType(), CATALOGUE_CHAPTER)) {
            throw new BadRequestException("CHAPTER 范围必须选择章节目录");
        }
        if (!Objects.equals(scope.getId(), target.getParentCatalogueId())) {
            throw new BadRequestException("章节与练习目录不匹配");
        }
    }

    /**
     * 校验章级出题所选择的小节，并按请求顺序返回目录详情。
     *
     * @param scopeType 出题范围类型
     * @param scope 出题范围目录
     * @param sectionIds 章内小节 ID 列表
     * @param courseId 课程 ID
     * @return 经过校验的小节目录列表；未限定小节时返回空列表
     */
    private List<CatalogueDetailDTO> requireScopeSections(AiQuestionScopeType scopeType,
                                                          CatalogueDetailDTO scope,
                                                          List<Long> sectionIds,
                                                          Long courseId) {
        if (scopeType == AiQuestionScopeType.SECTION) {
            if (sectionIds != null && !sectionIds.isEmpty()) {
                throw new BadRequestException("SECTION 范围不能额外指定章内小节");
            }
            return List.of();
        }
        if (sectionIds == null || sectionIds.isEmpty()) {
            return List.of();
        }
        List<CatalogueDetailDTO> details = catalogueClient.queryCatalogueDetails(sectionIds);
        Map<Long, CatalogueDetailDTO> detailMap = details == null
                ? new LinkedHashMap<>()
                : details.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(CatalogueDetailDTO::getId, Function.identity(), (left, right) -> left));
        // 章级出题的小节也可能尚未上架，只补查正式目录中缺失的小节，避免混合场景丢失已上架小节。
        List<Long> missingSectionIds = sectionIds.stream()
                .filter(sectionId -> !detailMap.containsKey(sectionId))
                .toList();
        if (!missingSectionIds.isEmpty()) {
            List<CatalogueDetailDTO> draftDetails = catalogueClient.queryDraftCatalogueDetails(missingSectionIds);
            if (draftDetails != null) {
                draftDetails.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> item.getId() != null)
                        .forEach(item -> detailMap.putIfAbsent(item.getId(), item));
            }
        }
        if (detailMap.size() != sectionIds.size()) {
            throw new BadRequestException("部分章内小节不存在");
        }
        List<CatalogueDetailDTO> result = new ArrayList<>(sectionIds.size());
        for (Long sectionId : sectionIds) {
            CatalogueDetailDTO section = detailMap.get(sectionId);
            if (!Objects.equals(section.getCourseId(), courseId)) {
                throw new BadRequestException("所选小节不属于指定课程");
            }
            if (!Objects.equals(section.getType(), CATALOGUE_SECTION)) {
                throw new BadRequestException("章级出题只能选择小节目录");
            }
            if (!Objects.equals(section.getParentCatalogueId(), scope.getId())) {
                throw new BadRequestException("所选小节不属于当前章节");
            }
            result.add(section);
        }
        return result;
    }

    /**
     * 按幂等请求 ID 查询批次
     *
     * @param userId 用户 ID
     * @param requestId 请求 ID
     * @return 匹配的批次，不存在时返回 null
     */
    private AiQuestionBatch findByRequestId(Long userId, String requestId) {
        if (requestId == null) {
            return null;
        }
        return lambdaQuery()
                .eq(AiQuestionBatch::getCreater, userId)
                .eq(AiQuestionBatch::getRequestId, requestId)
                .one();
    }

    /**
     * 校验幂等请求参数是否一致
     *
     * @param existing 已存在的批次
     * @param requestFingerprint 当前请求指纹
     */
    private void validateIdempotentRequest(AiQuestionBatch existing, String requestFingerprint) {
        if (!Objects.equals(existing.getRequestFingerprint(), requestFingerprint)) {
            throw new BadRequestException("requestId 已被其他 AI 出题请求使用");
        }
    }

    /**
     * 构建请求指纹
     *
     * @param dto 创建参数
     * @return SHA-256 请求指纹
     */
    private String buildRequestFingerprint(AiQuestionBatchCreateDTO dto) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("courseId", dto.getCourseId());
        values.put("scopeType", dto.getScopeType().toUpperCase());
        values.put("scopeId", dto.getScopeId());
        values.put("scopeSectionIds", dto.getScopeSectionIds());
        values.put("sourceType", dto.getSourceType().toUpperCase());
        values.put("sourceId", dto.getSourceId());
        values.put("sourceVersion", dto.getSourceVersion());
        values.put("targetBizId", dto.getTargetBizId());
        values.put("knowledgePoints", dto.getKnowledgePoints());
        values.put("materialText", dto.getMaterialText());
        values.put("questionTypes", dto.getQuestionTypes());
        values.put("questionCount", dto.getQuestionCount());
        values.put("difficulty", dto.getDifficulty());
        values.put("score", dto.getScore());
        return DigestUtil.sha256Hex(JsonUtils.toJsonStr(values));
    }


    /**
     * 选择需要确认的草稿 ID
     *
     * @param drafts 全部草稿
     * @param requestedIds 指定的草稿 ID；为空时选择全部待确认草稿
     * @return 待确认草稿
     */
    private List<AiQuestionDraft> selectDraftsForConfirmation(List<AiQuestionDraft> drafts,
                                                               List<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return drafts.stream()
                    .filter(draft -> AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(draft.getStatus()))
                    .toList();
        }
        Set<Long> distinctIds = new LinkedHashSet<>(requestedIds);
        if (distinctIds.contains(null)) {
            throw new BadRequestException("草稿 ID 不能为空");
        }
        Map<Long, AiQuestionDraft> draftMap = drafts.stream()
                .collect(Collectors.toMap(AiQuestionDraft::getId, Function.identity()));
        List<AiQuestionDraft> selected = new ArrayList<>(distinctIds.size());
        for (Long draftId : distinctIds) {
            AiQuestionDraft draft = draftMap.get(draftId);
            if (draft == null) {
                throw new BadRequestException("指定的 AI 题目草稿不存在");
            }
            selected.add(draft);
        }
        return selected;
    }

    /**
     * 在正式发布前重新校验课程和目录上下文，避免旧批次绕过最新权限与归属校验
     *
     * @param batch 出题批次
     * @param userId 当前操作用户 ID
     */
    private void revalidatePublishContext(AiQuestionBatch batch, Long userId) {
        CourseBaseInfoDTO course = requireOperableCourse(batch.getCourseId(), userId);
        CatalogueDetailDTO scope = requireCatalogue(batch.getScopeId(), batch.getCourseId(), "题目范围目录不存在");
        CatalogueDetailDTO target = requireCatalogue(batch.getTargetBizId(), batch.getCourseId(), "目标练习目录不存在");
        AiQuestionScopeType scopeType = requireScopeType(batch.getScopeType());
        validateCatalogueRelation(scopeType, scope, target);
        requireScopeSections(scopeType, scope, batch.getScopeSectionIds(), batch.getCourseId());

        // 课程分类可能在批次创建后发生变化，正式题目使用当前课程分类，避免写入过期分类。
        batch.setCourseName(course.getName())
                .setCateId1(course.getFirstCateId())
                .setCateId2(course.getSecondCateId())
                .setCateId3(course.getThirdCateId());
    }
    /**
     * 将已确认草稿发布为正式题目
     *
     * @param batch 批次信息
     * @param drafts 草稿列表
     * @param userId 操作用户 ID
     */
    private void publishConfirmedDrafts(AiQuestionBatch batch,
                                        List<AiQuestionDraft> drafts,
                                        Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Question> questions = new ArrayList<>(drafts.size());
        for (AiQuestionDraft draft : drafts) {
            questions.add(new Question()
                    .setName(draft.getName())
                    .setType(draft.getType())
                    .setCateId1(batch.getCateId1())
                    .setCateId2(batch.getCateId2())
                    .setCateId3(batch.getCateId3())
                    .setDifficulty(draft.getDifficulty())
                    .setCorrectTimes(0)
                    .setAnswerTimes(0)
                    .setScore(draft.getScore())
                    .setCreateTime(now)
                    .setUpdateTime(now)
                    .setCreater(userId)
                    .setUpdater(userId));
        }
        if (!questionService.saveBatch(questions)) {
            throw new DbException("正式题目保存失败");
        }

        List<QuestionDetail> details = new ArrayList<>(drafts.size());
        List<QuestionBiz> relations = new ArrayList<>(drafts.size());
        List<AiQuestionOrigin> origins = new ArrayList<>(drafts.size());
        List<AiQuestionDraft> updates = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            AiQuestionDraft draft = drafts.get(index);
            Long questionId = questions.get(index).getId();
            details.add(new QuestionDetail()
                    .setId(questionId)
                    .setOptions(draft.getOptions())
                    .setAnswer(draft.getAnswer())
                    .setAnalysis(draft.getAnalysis()));
            relations.add(QuestionBiz.of(null, batch.getTargetBizId(), questionId));
            origins.add(new AiQuestionOrigin()
                    .setQuestionId(questionId)
                    .setBatchId(batch.getId())
                    .setDraftId(draft.getId())
                    .setCourseId(batch.getCourseId())
                    .setScopeType(batch.getScopeType())
                    .setScopeId(batch.getScopeId())
                    .setSourceType(batch.getSourceType())
                    .setSourceId(batch.getSourceId())
                    .setSourceVersion(batch.getSourceVersion())
                    .setTargetBizId(batch.getTargetBizId())
                    .setContentFingerprint(draft.getContentFingerprint())
                    .setCreateTime(now)
                    .setCreater(userId));
            updates.add(new AiQuestionDraft()
                    .setId(draft.getId())
                    .setStatus(AiQuestionDraftStatus.PUBLISHED.name())
                    .setQuestionId(questionId)
                    .setPublishedTime(now)
                    .setUpdater(userId)
                    .setUpdateTime(now));
        }
        if (!questionDetailService.saveBatch(details)) {
            throw new DbException("题目详情保存失败");
        }
        if (!questionBizService.saveBatch(relations)) {
            throw new DbException("题目业务关联保存失败");
        }
        if (!originService.saveBatch(origins)) {
            throw new DbException("AI 题目来源保存失败");
        }
        if (!draftService.updateBatchById(updates)) {
            throw new DbException("AI 题目草稿发布状态更新失败");
        }
    }

    /**
     * 刷新批次统计数据
     *
     * @param batchId 批次 ID
     * @param userId 操作用户 ID
     */
    private void refreshBatchStatistics(Long batchId, Long userId) {
        List<AiQuestionDraft> drafts = draftService.lambdaQuery()
                .eq(AiQuestionDraft::getBatchId, batchId)
                .list();
        int validCount = 0;
        int duplicateCount = 0;
        int publishedCount = 0;
        int unpublishedValidCount = 0;
        for (AiQuestionDraft draft : drafts) {
            String status = draft.getStatus();
            if (AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(status)
                    || AiQuestionDraftStatus.CONFIRMED.name().equals(status)
                    || AiQuestionDraftStatus.PUBLISHED.name().equals(status)) {
                validCount++;
            }
            if (AiQuestionDraftStatus.DUPLICATE.name().equals(status)) {
                duplicateCount++;
            }
            if (AiQuestionDraftStatus.PUBLISHED.name().equals(status)) {
                publishedCount++;
            }
            if (AiQuestionDraftStatus.PENDING_CONFIRMATION.name().equals(status)
                    || AiQuestionDraftStatus.CONFIRMED.name().equals(status)) {
                unpublishedValidCount++;
            }
        }

        String status;
        if (publishedCount > 0 && unpublishedValidCount == 0) {
            status = AiQuestionBatchStatus.PUBLISHED.name();
        } else if (publishedCount > 0) {
            status = AiQuestionBatchStatus.PARTIALLY_PUBLISHED.name();
        } else if (validCount > 0) {
            status = AiQuestionBatchStatus.PENDING_CONFIRMATION.name();
        } else {
            status = AiQuestionBatchStatus.INVALID.name();
        }
        int updated = baseMapper.updateById(new AiQuestionBatch()
                .setId(batchId)
                .setStatus(status)
                .setTotalCount(drafts.size())
                .setValidCount(validCount)
                .setDuplicateCount(duplicateCount)
                .setPublishedCount(publishedCount)
                .setUpdater(userId)
                .setUpdateTime(LocalDateTime.now()));
        if (updated != 1) {
            throw new DbException("AI 出题批次统计更新失败");
        }
    }

    /**
     * 锁定并校验批次归属
     *
     * @param batchId 批次 ID
     * @param userId 操作用户 ID
     * @return 批次信息
     */
    private AiQuestionBatch lockOwnedBatch(Long batchId, Long userId) {
        if (baseMapper.lockById(batchId) == null) {
            throw new BadRequestException("AI 出题批次不存在");
        }
        return requireOwnedBatch(batchId, userId);
    }

    /**
     * 校验批次归属
     *
     * @param batchId 批次 ID
     * @param userId 用户 ID
     * @return 批次信息
     */
    private AiQuestionBatch requireOwnedBatch(Long batchId, Long userId) {
        AiQuestionBatch batch = getById(batchId);
        if (batch == null) {
            throw new BadRequestException("AI 出题批次不存在");
        }
        if (!Objects.equals(batch.getCreater(), userId)) {
            throw new ForbiddenException("无权操作该 AI 出题批次");
        }
        return batch;
    }

    /**
     * 校验批次状态
     *
     * @param batch 批次信息
     * @param message 状态不满足要求时的提示
     * @param allowed 允许的状态
     */
    private void requireBatchStatus(AiQuestionBatch batch,
                                    String message,
                                    AiQuestionBatchStatus... allowed) {
        for (AiQuestionBatchStatus status : allowed) {
            if (status.name().equals(batch.getStatus())) {
                return;
            }
        }
        throw new BadRequestException(message);
    }

    /**
     * 获取当前用户 ID
     *
     * @return 当前用户 ID
     */
    private Long requireCurrentUser() {
        Long userId = UserContext.getUser();
        if (userId == null || userId <= 0) {
            throw new UnauthorizedException("请先登录后再使用 AI 出题功能");
        }
        return userId;
    }

    /**
     * 将空白字符串转换为 null
     *
     * @param value 待处理字符串
     * @return 处理后的字符串
     */
    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
