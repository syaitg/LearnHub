package com.tianji.media.controller;

import com.tianji.common.annotations.NoWrapper;
import com.tianji.common.domain.R;
import com.tianji.common.exceptions.CommonException;
import com.tianji.media.domain.dto.FileDTO;
import com.tianji.media.domain.po.File;
import com.tianji.media.service.IFileService;
import com.tianji.media.enums.MediaSource;
import com.tianji.media.enums.Platform;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件表，可以是普通文件、图片等 前端控制器
 */
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@Tag(name = "媒资管理相关接口")
public class FileController {

    private final IFileService fileService;

    @Operation(summary = "上传文件")
    @PostMapping
    public R<FileDTO> uploadFile(
            @Parameter(description = "文件数据") @RequestParam("file") MultipartFile file) {
        return R.ok(fileService.uploadFile(file));
    }

    @Operation(summary = "获取文件信息")
    @GetMapping("/{id}")
    public R<FileDTO> getFileInfo(
            @Parameter(description = "文件id", example = "1") @PathVariable("id") Long id) {
        return R.ok(fileService.getFileInfo(id));
    }

    /**
     * 访问文件内容。
     *
     * <p>自有 COS 是私有读写，不能直接把 COS 裸地址交给浏览器；该接口由服务端读取对应账号的 COS 文件并返回内容。
     * 课程封面保存的是本接口的稳定地址，因此不会因 COS 临时签名过期而失效。</p>
     */
    @Operation(summary = "访问文件内容")
    @NoWrapper
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> getFileContent(
            @Parameter(description = "文件id", example = "1") @PathVariable("id") Long id) {
        File file = fileService.getById(id);
        if (file == null) {
            throw new CommonException("文件不存在");
        }
        // 该接口是自有 COS 的稳定代理地址。官方历史文件仍应使用其原始地址，
        // 避免把不同腾讯云账号的对象交给错误的 COS 客户端读取。
        if (file.getSource() != MediaSource.OWN_TENCENT) {
            throw new CommonException("文件访问地址无效");
        }
        if (file.getStatus() == null || file.getStatus().getValue() < 2) {
            throw new CommonException("文件尚未上传完成");
        }
        InputStream inputStream = fileService.downloadFile(id);
        String filename = file.getFilename();
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if (filename != null) {
            String lowerName = filename.toLowerCase();
            if (lowerName.endsWith(".png")) {
                contentType = MediaType.IMAGE_PNG_VALUE;
            } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
                contentType = MediaType.IMAGE_JPEG_VALUE;
            } else if (lowerName.endsWith(".gif")) {
                contentType = MediaType.IMAGE_GIF_VALUE;
            }
        }
        String safeFilename = (filename == null || filename.isBlank() ? "文件" : filename)
                .replaceAll("[\\r\\n]", "_");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(safeFilename, StandardCharsets.UTF_8)
                .build());
        // 自有 COS 文件可能包含课程封面等私有内容，不能被公共缓存跨用户复用。
        headers.setCacheControl("private, max-age=3600");
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(inputStream));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public R<Void> deleteFileById(
            @Parameter(description = "文件id", example = "1") @PathVariable("id") Long id) {
        fileService.deleteFile(id);
        return R.ok();
    }
}
