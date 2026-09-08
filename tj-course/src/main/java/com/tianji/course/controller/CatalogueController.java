package com.tianji.course.controller;

import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.common.exceptions.ForbiddenException;
import com.tianji.common.utils.WebUtils;
import com.tianji.course.domain.vo.CataSimpleInfoVO;
import com.tianji.course.service.ICourseCatalogueService;
import com.tianji.course.service.ICourseCatalogueDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 目录课程相关接口
 */
@Tag(name = "章节目录相关接口")
@RestController
@RequestMapping("catalogues")
public class CatalogueController {

    @Autowired
    private ICourseCatalogueService courseCatalogueService;

    @Autowired
    private ICourseCatalogueDraftService courseCatalogueDraftService;
    /**
     * 批量查询目录基础信息。
     */
    @GetMapping("batchQuery")
    @Operation(summary = "根据章节目录批量查询基础信息")
    public List<CataSimpleInfoVO> batchQuery(@RequestParam("ids") List<Long> ids) {
        return courseCatalogueService.getManyCataSimpleInfo(ids);
    }
    /**
     * 查询小节基础信息。
     */
    @GetMapping("querySectionInfoById/{id}")
    @Operation(summary = "获取小节信息")
    public CataSimpleInfoVO querySectionInfoById(@PathVariable("id") Long id) {
        return courseCatalogueService.querySectionInfoById(id);
    }
    /**
     * 查询目录业务详情。
     */
    @GetMapping("{id}/detail")
    @Operation(summary = "查询课程目录业务校验详情")
    public CatalogueDetailDTO queryCatalogueDetail(@PathVariable("id") Long id) {
        checkInternalRequest();
        return courseCatalogueService.queryCatalogueDetail(id);
    }

    /**
     * 批量查询目录业务详情。
     *
     * @param ids 目录 ID 列表
     * @return 目录业务详情列表
     */
    @GetMapping("details")
    @Operation(summary = "批量查询课程目录业务校验详情")
    public List<CatalogueDetailDTO> queryCatalogueDetails(@RequestParam("ids") List<Long> ids) {
        checkInternalRequest();
        return courseCatalogueService.queryCatalogueDetails(ids);
    }

    /**
     * 查询课程目录草稿业务详情，仅供内部服务校验未上架目录。
     */
    @GetMapping("draft/{id}/detail")
    public CatalogueDetailDTO queryDraftCatalogueDetail(@PathVariable("id") Long id) {
        checkInternalRequest();
        return courseCatalogueDraftService.queryCatalogueDetail(id);
    }

    /**
     * 批量查询课程目录草稿业务详情，仅供内部服务校验未上架目录。
     */
    @GetMapping("draft/details")
    public List<CatalogueDetailDTO> queryDraftCatalogueDetails(@RequestParam("ids") List<Long> ids) {
        checkInternalRequest();
        return courseCatalogueDraftService.queryCatalogueDetails(ids);
    }

    /**
     * 校验当前请求是否为内部服务调用。
     */
    private void checkInternalRequest() {
        if (!WebUtils.isFeignRequest()) {
            throw new ForbiddenException("课程目录业务详情仅允许内部服务调用");
        }
    }

}
