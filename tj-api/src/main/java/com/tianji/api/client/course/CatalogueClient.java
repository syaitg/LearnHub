package com.tianji.api.client.course;

import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CatalogueDetailDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 课程目录远程调用接口
 */
@FeignClient(contextId = "catalogue", value = "course-service", path = "catalogues")
public interface CatalogueClient {


    /**
     * 根据目录id列表查询目录信息
     *
     * @param ids 目录id列表
     * @return id列表中对应的目录基础信息
     */
    @GetMapping("/batchQuery")
    List<CataSimpleInfoDTO> batchQueryCatalogue(@RequestParam("ids") Iterable<Long> ids);


    /**
     * 根据目录 ID 查询课程目录业务详情
     *
     * @param id 目录 ID
     * @return 课程目录业务详情
     */
    @GetMapping("/{id}/detail")
    CatalogueDetailDTO queryCatalogueDetail(@PathVariable("id") Long id);

    /**
     * 根据目录 ID 列表批量查询课程目录业务详情
     *
     * @param ids 目录 ID 列表
     * @return 课程目录业务详情列表
     */
    @GetMapping("/details")
    List<CatalogueDetailDTO> queryCatalogueDetails(@RequestParam("ids") Iterable<Long> ids);

    /**
     * 根据目录 ID 查询课程目录草稿业务详情。
     */
    @GetMapping("/draft/{id}/detail")
    CatalogueDetailDTO queryDraftCatalogueDetail(@PathVariable("id") Long id);

    /**
     * 根据目录 ID 列表批量查询课程目录草稿业务详情。
     */
    @GetMapping("/draft/details")
    List<CatalogueDetailDTO> queryDraftCatalogueDetails(@RequestParam("ids") Iterable<Long> ids);

}
