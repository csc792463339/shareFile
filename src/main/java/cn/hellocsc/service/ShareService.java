package cn.hellocsc.service;

import cn.hellocsc.exception.ShareIdExhaustedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.exception.ShareNotFoundException;
import cn.hellocsc.model.AdminSharesPageResponse;
import cn.hellocsc.model.ShareContent;
import cn.hellocsc.storage.ShareRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private static final int SHARE_ID_LENGTH = 5;
    private static final int SHARE_ID_SPACE = 100_000;
    private static final int SHARE_ID_GENERATE_ATTEMPTS = 500;
    private static final Duration SHARE_ID_RESERVE_TTL = Duration.ofSeconds(5);

    private final ShareRepository shareRepository;
    private final FileStorageService fileStorageService;

    @Value("${app.share.retention-days:30}")
    private int retentionDays;

    public ShareContent createTextShare(ShareContent request) {
        if (request.getTextContent() == null || request.getTextContent().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusDays(getRetentionDays());
        String shareId = generateShareId();
        request.setShareId(shareId);
        request.setFile(false);
        request.setCreateTime(now);
        request.setExpireTime(expireTime);
        request.setRetentionPolicy("D30");
        request.setSize(request.getTextContent().length());
        request.setViewCount(0);

        shareRepository.saveShare(request);

        log.info("创建文本分享成功 - ID: {}, 大小: {} 字符, 到期: {}",
                shareId, request.getTextContent().length(), expireTime);
        return request;
    }

    public ShareContent createFileShare(MultipartFile file, ShareContent request) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        ShareContent share = new ShareContent();
        share.setFile(true);
        share.setShareId(generateShareId());
        share.setCreateTime(now);
        share.setExpireTime(now.plusDays(getRetentionDays()));
        share.setRetentionPolicy("D30");
        share.setViewCount(0);
        share.setRichText(request.isRichText());

        ShareContent savedShare = fileStorageService.saveFile(file, share);
        shareRepository.saveShare(savedShare);

        log.info("创建文件分享成功 - ID: {}, 文件名: {}, 大小: {} 字节, 到期: {}",
                savedShare.getShareId(), savedShare.getFileName(), savedShare.getSize(), savedShare.getExpireTime());

        return savedShare;
    }

    public ShareContent getShareContent(String shareId) {
        ShareContent share = shareRepository.getShare(shareId, true)
                .orElseThrow(() -> new ShareNotFoundException("分享内容不存在或已过期"));

        if (isExpired(share)) {
            shareRepository.deleteShare(shareId);
            throw new ShareNotFoundException("分享已过期");
        }

        if (share.isFile() && share.getFilePath() != null) {
            Path filePath = fileStorageService.getFile(share.getFilePath());
            if (!Files.exists(filePath)) {
                shareRepository.deleteShare(shareId);
                throw new ShareNotFoundException("文件不存在或已被删除");
            }
        }

        return share;
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
        int cleanedFiles = fileStorageService.deleteExpiredFiles(getRetentionDays() * 24);
        shareRepository.flushDirtyViewCountsToBackup();

        if (cleanedFiles > 0) {
            log.info("执行清理任务：物理删除了 {} 个过期文件", cleanedFiles);
        }
        return cleanedFiles;
    }

    public AdminSharesPageResponse getAdminSharesPage(int page, int size, String type, String sort) {
        return shareRepository.queryShares(page, size, type, sort);
    }

    public void deleteShare(String shareId) {
        shareRepository.deleteShare(shareId);
    }

    public int getRetentionDays() {
        return Math.max(retentionDays, 1);
    }

    private boolean isExpired(ShareContent share) {
        LocalDateTime expireTime = share.getExpireTime();
        if (expireTime == null && share.getCreateTime() != null) {
            expireTime = share.getCreateTime().plusHours(24);
            share.setExpireTime(expireTime);
        }
        return expireTime == null || LocalDateTime.now().isAfter(expireTime);
    }

    // 生成 5 位数字分享码，达到阈值快速失败
    private String generateShareId() {
        int attempts = Math.min(SHARE_ID_GENERATE_ATTEMPTS, SHARE_ID_SPACE);
        for (int retry = 0; retry < attempts; retry++) {
            int num = ThreadLocalRandom.current().nextInt(SHARE_ID_SPACE);
            String id = String.format("%0" + SHARE_ID_LENGTH + "d", num);
            if (shareRepository.reserveShareId(id, SHARE_ID_RESERVE_TTL)) {
                return id;
            }
        }
        throw new ShareIdExhaustedException("分享码资源暂时耗尽，请稍后重试");
    }
}
