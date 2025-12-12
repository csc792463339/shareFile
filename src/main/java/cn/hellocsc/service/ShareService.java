package cn.hellocsc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.exception.ShareNotFoundException;
import cn.hellocsc.model.ShareContent;
import cn.hellocsc.storage.PersistentTextStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final PersistentTextStorage persistentTextStorage;
    private final FileStorageService fileStorageService;

    public ShareContent createTextShare(ShareContent request) {
        if (request.getTextContent() == null || request.getTextContent().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        String shareId = generateShareId();
        request.setShareId(shareId);
        request.setFile(false);
        request.setCreateTime(LocalDateTime.now());
        request.setViewCount(0);

        persistentTextStorage.save(request);

        log.info("创建文本分享成功 - ID: {}, 大小: {} 字符", shareId, request.getTextContent().length());
        return request;
    }

    public ShareContent createFileShare(MultipartFile file, ShareContent request) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        ShareContent share = new ShareContent();
        share.setFile(true);
        share.setShareId(generateShareId());
        share.setCreateTime(LocalDateTime.now());
        share.setViewCount(0);
        share.setRichText(request.isRichText());

        ShareContent savedShare = fileStorageService.saveFile(file, share);
        persistentTextStorage.save(savedShare);

        log.info("创建文件分享成功 - ID: {}, 文件名: {}, 大小: {} 字节",
                savedShare.getShareId(), savedShare.getFileName(), savedShare.getSize());

        return savedShare;
    }

    public ShareContent getShareContent(String shareId) {
        Optional<ShareContent> shareOpt = persistentTextStorage.get(shareId);

        if (shareOpt.isPresent()) {
            ShareContent share = shareOpt.get();
            validateShareAccess(share);

            // 简单的计数器更新（非线程安全但足够用）
            share.setViewCount(share.getViewCount() + 1);

            if (share.isFile() && share.getFilePath() != null) {
                Path filePath = fileStorageService.getFile(share.getFilePath());
                if (!Files.exists(filePath)) {
                    persistentTextStorage.invalidate(shareId);
                    throw new ShareNotFoundException("文件不存在或已被删除");
                }
            }

            persistentTextStorage.save(share);
            return share;
        }

        throw new ShareNotFoundException("分享内容不存在或已过期");
    }

    public Path getFileForDownload(ShareContent share) {
        if (!share.isFile() || share.getFilePath() == null) {
            throw new IllegalArgumentException("无效的文件分享");
        }

        Path filePath = fileStorageService.getFile(share.getFilePath());
        if (!Files.exists(filePath)) {
            throw new ShareNotFoundException("文件不存在或已被删除");
        }

        return filePath;
    }

    // 执行清理任务
    public int cleanupExpiredShares() {
        // 1. 清理磁盘上的物理文件 (保留24小时内的文件)
        int cleanedFiles = fileStorageService.deleteExpiredFiles(24);

        // 2. 触发缓存的清理
        persistentTextStorage.cleanUp();

        if (cleanedFiles > 0) {
            log.info("执行清理任务：物理删除了 {} 个过期文件", cleanedFiles);
        }
        return cleanedFiles;
    }

    private void validateShareAccess(ShareContent share) {
        LocalDateTime expiryTime = share.getCreateTime().plusHours(24);
        if (LocalDateTime.now().isAfter(expiryTime)) {
            throw new ShareNotFoundException("分享已过期");
        }
    }

    // 生成 ID (改进版：6位数字字母组合)
    private String generateShareId() {
        // 去除容易混淆的字符 (0, O, 1, I)
        String chars = "0123456789";
        int length = 4;
        StringBuilder sb = new StringBuilder();

        // 尝试生成唯一ID，最多重试5次
        for (int retry = 0; retry < 5; retry++) {
            sb.setLength(0);
            for (int i = 0; i < length; i++) {
                int index = ThreadLocalRandom.current().nextInt(chars.length());
                sb.append(chars.charAt(index));
            }
            String id = sb.toString();
            if (!persistentTextStorage.get(id).isPresent()) {
                return id;
            }
        }
        // 如果极低概率下失败，退回到时间戳+随机数
        return String.valueOf(System.currentTimeMillis() % 1000000);
    }
}