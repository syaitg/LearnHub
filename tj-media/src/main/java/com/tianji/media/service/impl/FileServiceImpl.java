package com.tianji.media.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.exceptions.CommonException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.StringUtils;
import com.tianji.media.config.PlatformProperties;
import com.tianji.media.domain.dto.FileDTO;
import com.tianji.media.domain.po.File;
import com.tianji.media.enums.FileErrorInfo;
import com.tianji.media.enums.FileStatus;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.enums.Platform;
import com.tianji.media.mapper.FileMapper;
import com.tianji.media.service.IFileService;
import com.tianji.media.storage.IFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>
 * 文件表，可以是普通文件、图片等 服务实现类
 * </p>
 *
 * @author Sy
 * @since 2026-06-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl extends ServiceImpl<FileMapper, File> implements IFileService {

    private final IFileStorage fileStorage;
    private final PlatformProperties properties;

    @Override
    public FileDTO uploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CommonException("上传文件不能为空");
        }
        // 1.获取原始文件名
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new CommonException("文件名不能为空");
        }
        // 2.生成新的文件名
        String filename = generateNewFileName(originalFilename);
        // 3.获取文件流并上传到自有腾讯云 COS
        MediaSource source = MediaSource.OWN_TENCENT;
        String fileUrl;
        try (InputStream inputStream = file.getInputStream()) {
            fileUrl = fileStorage.uploadFile(filename, inputStream, file.getSize(), source);
        } catch (IOException e) {
            throw new CommonException("文件读取异常", e);
        }
        if (StringUtils.isBlank(fileUrl)) {
            // 上传成功但没有返回地址时，先清理云端文件，避免产生无法访问的孤儿文件。
            try {
                fileStorage.deleteFile(filename, source);
            } catch (Exception cleanupException) {
                log.error("清理未返回地址的云端文件失败：{}", filename, cleanupException);
            }
            throw new CommonException("文件上传成功但未返回文件地址");
        }
        // 4.写入数据库
        File fileInfo = new File();
        fileInfo.setFilename(originalFilename);
        fileInfo.setKey(filename);
        fileInfo.setStatus(FileStatus.UPLOADED);
        fileInfo.setPlatform(platformFor(source));
        fileInfo.setSource(source);
        try {
            boolean saved = save(fileInfo);
            if (!saved || fileInfo.getId() == null) {
                throw new DbException(FileErrorInfo.Msg.FILE_UPLOAD_ERROR);
            }
        } catch (Exception e) {
            log.error("保存文件信息失败，已清理云端文件：{}", filename, e);
            try {
                fileStorage.deleteFile(filename, source);
            } catch (Exception cleanupException) {
                log.error("清理上传失败的云端文件异常：{}", filename, cleanupException);
            }
            if (e instanceof DbException dbException) {
                throw dbException;
            }
            throw new DbException(FileErrorInfo.Msg.FILE_UPLOAD_ERROR);
        }
        // 5.返回上传结果
        FileDTO fileDTO = new FileDTO();
        fileDTO.setId(fileInfo.getId());
        // 自有 COS 为私有读写，返回系统稳定访问地址，避免把会失效的临时签名或私有裸地址写入课程表。
        fileDTO.setPath(buildFileAccessPath(fileInfo, fileUrl));
        fileDTO.setFilename(originalFilename);
        return fileDTO;
    }

    @Override
    public void deleteFile(Long id) {
        File file = getById(id);
        if (file == null) {
            return;
        }
        // 腾讯云文件根据来源选择官方 COS 或自有 COS
        if (file.getPlatform() == Platform.TENCENT) {
            MediaSource source = file.getSource() == null
                    ? MediaSource.OFFICIAL_TENCENT : file.getSource();
            fileStorage.deleteFile(file.getKey(), source);
        }
        boolean removed = removeById(id);
        if (!removed) {
            log.error("删除文件数据库记录未生效：id={}, key={}, source={}",
                    id, file.getKey(), file.getSource());
            throw new CommonException(FileErrorInfo.Msg.MEDIA_DELETE_ERROR);
        }
    }

    @Override
    public FileDTO getFileInfo(Long id) {
        File file = getById(id);
        if (file == null) {
            return null;
        }
        String path;
        if (file.getPlatform() == Platform.TENCENT) {
            // 历史文件来源为空时按黑马官方 COS 读取，避免误删或误读历史资源。
            MediaSource source = file.getSource() == null
                    ? MediaSource.OFFICIAL_TENCENT : file.getSource();
            path = buildFileAccessPath(file, fileStorage.getFileUrl(file.getKey(), source));
        } else if (file.getPlatform() != null) {
            path = file.getPlatform().getPath() + file.getKey();
        } else {
            throw new CommonException("文件存储平台未配置");
        }
        return FileDTO.of(file.getId(), file.getFilename(), path);
    }

    @Override
    public InputStream downloadFile(Long id) {
        File file = getById(id);
        if (file == null) {
            throw new CommonException("文件不存在");
        }
        if (file.getPlatform() == Platform.TENCENT) {
            // 历史文件来源为空时按黑马官方 COS 读取，避免误删或误读历史资源。
            MediaSource source = file.getSource() == null
                    ? MediaSource.OFFICIAL_TENCENT : file.getSource();
            return fileStorage.downloadFile(file.getKey(), source);
        }
        if (file.getPlatform() == null) {
            throw new CommonException("文件存储平台未配置");
        }
        return fileStorage.downloadFile(file.getKey());
    }

    /**
     * 私有 COS 文件不能把云端裸地址直接返回给浏览器。
     * 使用经过网关的稳定地址，由服务端按文件来源读取 COS，课程表中保存的地址不会过期。
     */
    private Platform platformFor(MediaSource source) {
        if (source == MediaSource.OWN_TENCENT || source == MediaSource.OFFICIAL_TENCENT) {
            return Platform.TENCENT;
        }
        return properties.getFile();
    }

    private String buildFileAccessPath(File file, String uploadedPath) {
        if (file.getPlatform() == Platform.TENCENT
                && file.getSource() == MediaSource.OWN_TENCENT) {
            return "/ms/files/" + file.getId() + "/content";
        }
        return uploadedPath;
    }

    private String generateNewFileName(String originalFilename) {
        // 1.获取文件后缀
        String suffix = StringUtils.subAfter(originalFilename, ".", true);
        // 2.生成新的文件名
        return StringUtils.isBlank(suffix)
                ? UUID.randomUUID().toString(true)
                : UUID.randomUUID().toString(true) + "." + suffix;
    }
}
