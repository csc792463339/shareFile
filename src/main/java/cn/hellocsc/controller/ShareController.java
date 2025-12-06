package cn.hellocsc.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.model.ShareContent;
import cn.hellocsc.service.ShareService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/share")
public class ShareController {

    private final ShareService shareService;

    // 创建文本分享
    @PostMapping("/text")
    public Map<String, Object> createTextShare(@RequestBody ShareContent request) {
        ShareContent saved = shareService.createTextShare(request);
        return Map.of(
                "shareId", saved.getShareId(),
                "url", "/view.html?id=" + saved.getShareId(),
                "expiresIn", 86400 // 24小时
        );
    }

    // 创建文件分享
    @PostMapping("/file")
    public Map<String, Object> createFileShare(
            @RequestParam("file") MultipartFile file,
            @RequestParam("isRichText") boolean isRichText) throws IOException {

        ShareContent request = new ShareContent();
        request.setRichText(isRichText);

        ShareContent saved = shareService.createFileShare(file, request);
        return Map.of(
                "shareId", saved.getShareId(),
                "url", "/view.html?id=" + saved.getShareId(),
                "expiresIn", 86400 // 24小时
        );
    }

    // 获取分享内容
    @GetMapping
    public ShareContent getShareContent(
            @RequestParam String shareId) {
        return shareService.getShareContent(shareId);
    }
    
    // 获取分享内容（兼容旧路径格式）
    @GetMapping("/{shareId}")
    public ShareContent getShareContentByPath(
            @PathVariable String shareId) {
        return shareService.getShareContent(shareId);
    }

    // 下载文件
    @GetMapping("/download")
    public void downloadFile(
            @RequestParam String shareId,
            HttpServletResponse response) throws IOException {

        ShareContent share = shareService.getShareContent(shareId);

        if (!share.isFile()) {
            throw new IllegalArgumentException("分享内容不是文件");
        }

        Path filePath = shareService.getFileForDownload(share);
        response.setContentType(share.getContentType() != null ? share.getContentType() : "application/octet-stream");
        
        // 获取文件名
        String fileName = share.getFileName();
        
        try {
            // 添加Content-Length头，让客户端知道文件大小
            long fileSize = Files.size(filePath);
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
            
            // 确保响应没有缓存问题
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
            
            // 使用Spring的ContentDisposition构建正确的响应头，支持中文文件名
            ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                    .filename(fileName, StandardCharsets.UTF_8)
                    .build();
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

            // 记录下载日志
            log.info("开始下载文件 - ID: {}, 文件名: {}, 路径: {}, 大小: {}", shareId, fileName, filePath, fileSize);
            
            // 使用高效的方式复制文件，Files.copy会处理好所有IO细节，包括缓冲区管理
            // 不使用try-with-resources关闭outputStream，由Servlet容器负责关闭
            Files.copy(filePath, response.getOutputStream());
            
            // 记录下载成功日志
            log.info("文件下载成功 - ID: {}, 文件名: {}", shareId, fileName);
        } catch (IOException e) {
            // 检查是否是客户端中断异常
            boolean isClientDisconnect = false;
            
            // 检查异常类型和消息
            if (e instanceof java.nio.channels.ClosedChannelException ||
                e instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
                isClientDisconnect = true;
            } else if (e.getMessage() != null) {
                isClientDisconnect = e.getMessage().contains("Broken pipe") || 
                                     e.getMessage().contains("Connection reset by peer") ||
                                     e.getMessage().contains("ClientAbortException") ||
                                     e.getMessage().contains("failed to write") ||
                                     e.getMessage().contains("ClosedChannelException");
            }
            
            // 检查嵌套异常
            if (!isClientDisconnect && e.getCause() != null && e.getCause() instanceof IOException) {
                IOException cause = (IOException) e.getCause();
                isClientDisconnect = cause instanceof java.nio.channels.ClosedChannelException || 
                                    (cause.getMessage() != null && 
                                     (cause.getMessage().contains("Broken pipe") ||
                                      cause.getMessage().contains("Connection reset by peer") ||
                                      cause.getMessage().contains("ClientAbortException") ||
                                      cause.getMessage().contains("failed to write") ||
                                      cause.getMessage().contains("ClosedChannelException")));
            }
            
            if (isClientDisconnect) {
                // 客户端主动中断连接，这是正常情况，仅记录 INFO 日志
                log.info("文件下载中断（客户端断开连接） - ID: {}, 文件名: {}", shareId, fileName);
            } else {
                // 真正的 IO 错误，记录错误日志
                log.error("文件下载失败 - ID: {}, 错误: {}", shareId, e.getMessage(), e);
                // 如果响应尚未提交，返回 500 错误
                if (!response.isCommitted()) {
                    try {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "文件下载失败: " + e.getMessage());
                    } catch (IOException ex) {
                        // 记录发送错误响应失败的日志
                        log.error("发送错误响应失败 - ID: {}, 错误: {}", shareId, ex.getMessage(), ex);
                    }
                }
            }
        } catch (Exception e) {
            // 记录其他错误日志
            log.error("下载过程中发生意外错误 - ID: {}, 错误: {}", shareId, e.getMessage(), e);
            // 如果响应尚未提交，返回 500 错误
            if (!response.isCommitted()) {
                try {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "下载过程中发生意外错误: " + e.getMessage());
                } catch (IOException ex) {
                    // 记录发送错误响应失败的日志
                    log.error("发送错误响应失败 - ID: {}, 错误: {}", shareId, ex.getMessage(), ex);
                }
            }
        }
    }
}