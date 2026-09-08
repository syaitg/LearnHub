package com.tianji.media.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.media.MediaAiInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.media.domain.dto.MediaDTO;
import com.tianji.media.domain.dto.MediaUploadResultDTO;
import com.tianji.media.domain.po.Media;
import com.tianji.media.domain.query.MediaQuery;
import com.tianji.media.domain.vo.MediaVO;
import com.tianji.media.domain.vo.VideoPlayVO;

import java.util.List;

/**
 * <p>
 * 媒资表，主要是视频文件 服务类
 * </p>
 *
 * @author Sy
 * @since 2026-06-30
 */
public interface IMediaService extends IService<Media> {

    String getUploadSignature();

    VideoPlayVO getPlaySignatureBySectionId(Long fileId);

    MediaDTO save(MediaUploadResultDTO mediaResult);

    void updateMediaProcedureResult(Media media);

    /**
     * 删除单个媒资，并同步删除云端文件。
     *
     * @param mediaId 媒资 ID
     */
    void deleteMedia(Long mediaId);

    /**
     * 批量删除媒资，并同步删除云端文件。
     *
     * @param mediaIds 媒资 ID 集合
     */
    void deleteMedias(List<Long> mediaIds);

    VideoPlayVO getPlaySignatureByMediaId(Long mediaId);

    PageDTO<MediaVO> queryMediaPage(MediaQuery query);

    /**
     * 查询视频 AI 处理所需的媒资信息
     *
     * @param mediaId 媒资 ID
     * @param courseId 课程 ID
     * @param sectionId 小节 ID
     * @return 媒资 AI 信息
     */
    MediaAiInfoDTO queryAiInfo(Long mediaId, Long courseId, Long sectionId);
}
