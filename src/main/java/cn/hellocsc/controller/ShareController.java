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
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
                "expiresIn", 86400
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
                "expiresIn", 86400
        );
    }

    // 获取分享内容
    @GetMapping
    public ShareContent getShareContent(@RequestParam String shareId) {
        return shareService.getShareContent(shareId);
    }

    // 获取分享内容（兼容旧路径格式）
    @GetMapping("/{shareId}")
    public ShareContent getShareContentByPath(@PathVariable String shareId) {
        return shareService.getShareContent(shareId);
    }

    // 下载文件 (优化版：零拷贝)
    @GetMapping("/download")
    public void downloadFile(
            @RequestParam String shareId,
            HttpServletResponse response) throws IOException {

        ShareContent share = shareService.getShareContent(shareId);

        if (!share.isFile()) {
            throw new IllegalArgumentException("分享内容不是文件");
        }

        Path filePath = shareService.getFileForDownload(share);

        // 1. 设置 Content-Type
        String contentType = share.getContentType() != null ? share.getContentType() : "application/octet-stream";
        response.setContentType(contentType);

        String fileName = share.getFileName();
        long fileSize = Files.size(filePath);

        // 2. 设置响应头
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize));
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString());

        log.info("开始下载文件 (Zero-Copy) - ID: {}, 文件名: {}, 大小: {}", shareId, fileName, fileSize);

        // 3. 执行零拷贝下载 (Zero-Copy Transfer)
        // 使用 FileChannel.transferTo 直接将文件数据传输到 Socket Channel
        // 这避免了将数据读入用户态内存（Java Heap），极大降低 CPU 占用并提升速度
        try (FileChannel fileChannel = FileChannel.open(filePath, StandardOpenOption.READ);
             WritableByteChannel outputChannel = Channels.newChannel(response.getOutputStream())) {

            // 循环传输，防止大文件一次传输不完 (transferTo 在某些系统有 2GB 限制)
            long position = 0;
            long count = fileSize;
            while (position < count) {
                long transferred = fileChannel.transferTo(position, count - position, outputChannel);
                if (transferred == 0) {
                    break; // 防止死循环
                }
                position += transferred;
            }

            log.info("文件下载成功 - ID: {}", shareId);

        } catch (IOException e) {
            handleDownloadError(shareId, fileName, response, e);
        }
    }

    // 错误处理逻辑提取
    private void handleDownloadError(String shareId, String fileName, HttpServletResponse response, IOException e) {
        boolean isClientDisconnect = isClientDisconnect(e);

        if (isClientDisconnect) {
            log.info("下载中断（客户端断开） - ID: {}", shareId);
        } else {
            log.error("下载失败 - ID: {}, 错误: {}", shareId, e.getMessage());
            if (!response.isCommitted()) {
                try {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "下载失败");
                } catch (IOException ex) {
                    // ignore
                }
            }
        }
    }

    private boolean isClientDisconnect(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return e instanceof java.nio.channels.ClosedChannelException ||
                msg.contains("Broken pipe") ||
                msg.contains("Connection reset") ||
                msg.contains("ClientAbortException");
    }
}