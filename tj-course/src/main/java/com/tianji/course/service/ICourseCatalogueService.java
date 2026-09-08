package com.tianji.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.course.CatalogueDTO;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import com.tianji.api.dto.course.MediaQuoteDTO;
import com.tianji.api.dto.course.SectionInfoDTO;
import com.tianji.course.domain.po.CourseCatalogue;
import com.tianji.course.domain.vo.CataSimpleInfoVO;
import com.tianji.course.domain.vo.CataVO;

import java.util.List;

/**
 * 课程目录服务
 *
 * @author wusongsong
 * @since 2026-07-19
 */
public interface ICourseCatalogueService extends IService<CourseCatalogue> {

    /**
     * 查询线上课程目录
     *
     * @param courseId 课程 ID
     * @param withPractice 是否包含练习目录
     * @return 课程目录
     */
    List<CatalogueDTO> queryCourseCatalogues(Long courseId, Boolean withPractice);

    /**
     * 批量统计媒资引用次数
     *
     * @param mediaIds 媒资 ID 列表
     * @return 媒资引用次数
     */
    List<MediaQuoteDTO> countMediaUserInfo(List<Long> mediaIds);

    /**
     * 查询小节基础信息
     *
     * @param sectionId 小节 ID
     * @return 小节基础信息
     */
    SectionInfoDTO getSimpleSectionInfo(Long sectionId);

    /**
     * 查询课程目录索引列表
     *
     * @param courseId 课程 ID
     * @return 课程目录索引列表
     */
    List<CataSimpleInfoVO> getCatasIndexList(Long courseId);

    /**
     * 批量查询目录基础信息
     *
     * @param ids 目录 ID 列表
     * @return 目录基础信息
     */
    List<CataSimpleInfoVO> getManyCataSimpleInfo(List<Long> ids);

    /**
     * 查询小节信息
     *
     * @param id 小节 ID
     * @return 小节信息
     */
    CataSimpleInfoVO querySectionInfoById(Long id);

    /**
     * 查询课程目录业务详情
     *
     * @param id 目录 ID
     * @return 课程目录业务详情
     */
    CatalogueDetailDTO queryCatalogueDetail(Long id);

    /**
     * 批量查询课程目录业务详情
     *
     * @param ids 目录 ID 列表
     * @return 课程目录业务详情列表
     */
    List<CatalogueDetailDTO> queryCatalogueDetails(List<Long> ids);

    /**
     * 查询课程目录视图
     *
     * @param courseId 课程 ID
     * @param withPractice 是否包含练习目录
     * @return 课程目录视图
     */
    List<CataVO> queryCourseCataloguesVO(Long courseId, Boolean withPractice);
}