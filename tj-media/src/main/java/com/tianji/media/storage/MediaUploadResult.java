package com.tianji.media.storage;

import lombok.Data;
import com.tianji.media.enums.MediaSource;

@Data
public class MediaUploadResult {

    private String fileId;

    private String mediaUrl;

    private String coverUrl;

    private String requestId;

    private String filename;

    /** 本次上传使用的媒资账号来源。 */
    private MediaSource source;
}
