package com.tianji.api.client.media;

import com.tianji.api.dto.media.MediaAiInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 媒资服务远程调用接口
 */
@FeignClient(value = "media-service", contextId = "mediaAi")
public interface MediaClient {

    /**
     * 查询视频 AI 处理所需的媒资信息
     *
     * @param mediaId 媒资 ID
     * @param courseId 课程 ID
     * @param sectionId 小节 ID
     * @return 媒资 AI 信息
     */
    @GetMapping("/medias/{id}/ai-info")
    MediaAiInfoDTO queryAiInfo(@PathVariable("id") Long mediaId,
                               @RequestParam("courseId") Long courseId,
                               @RequestParam("sectionId") Long sectionId);
}
