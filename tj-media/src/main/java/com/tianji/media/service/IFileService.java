package com.tianji.media.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.media.domain.dto.FileDTO;
import com.tianji.media.domain.po.File;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * <p>
 * 文件表，可以是普通文件、图片等 服务类
 * </p>
 *
 * @author Sy
 * @since 2026-06-30
 */
public interface IFileService extends IService<File> {

    FileDTO uploadFile(MultipartFile file);

    FileDTO getFileInfo(Long id);

    /** 根据文件编号读取文件内容，供浏览器访问私有 COS 文件。 */
    InputStream downloadFile(Long id);

    /** 根据文件来源删除云端文件和数据库记录。 */
    void deleteFile(Long id);
}
