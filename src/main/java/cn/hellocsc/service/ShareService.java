package cn.hellocsc.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.exception.ShareNotFoundException;
import cn.hellocsc.model.ShareContent;
import cn.hellocsc.storage.MemoryTextStorage;
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

    private final MemoryTextStorage memoryTextStorage;
    private final FileStorageService fileStorageService;


    public ShareContent createTextShare(ShareContent request) {
        // 验证文本长度
        if (request.getTextContent() == null || request.getTextContent().isEmpty()) {
            throw new IllegalArgumentException("文本内容不能为空");
        }

        // 生成唯一分享ID
        String shareId = generateShareId();
        request.setShareId(shareId);
        request.setFile(false);
        request.setCreateTime(LocalDateTime.now());
        request.setViewCount(0);

        // 保存到内存存储
        memoryTextStorage.save(request);

        log.info("创建文本分享成功 - ID: {}, 大小: {} 字符", shareId, request.getTextContent().length());
        return request;
    }

    // 创建文件分享
    public ShareContent createFileShare(MultipartFile file, ShareContent request) throws IOException {
        // 验证文件
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 创建分享对象
        ShareContent share = new ShareContent();
        share.setFile(true);
        share.setShareId(generateShareId());
        share.setCreateTime(LocalDateTime.now());
        share.setViewCount(0);
        share.setRichText(request.isRichText());

        // 保存文件
        ShareContent savedShare = fileStorageService.saveFile(file, share);
        
        // 将文件分享的元数据也保存到内存存储，以便后续查找
        memoryTextStorage.save(savedShare);
        
        log.info("创建文件分享成功 - ID: {}, 文件名: {}, 大小: {} 字节", 
                savedShare.getShareId(), savedShare.getFileName(), savedShare.getSize());
        
        return savedShare;
    }

    // 获取分享内容
    public ShareContent getShareContent(String shareId) {
        // 从内存存储获取分享（包括文本分享和文件分享）
        Optional<ShareContent> shareOpt = memoryTextStorage.get(shareId);

        if (shareOpt.isPresent()) {
            ShareContent share = shareOpt.get();
            validateShareAccess(share);
            
            // 更新查看次数
            share.setViewCount(share.getViewCount() + 1);
            
            // 如果是文件分享，验证文件是否还存在
            if (share.isFile() && share.getFilePath() != null) {
                Path filePath = fileStorageService.getFile(share.getFilePath());
                if (!Files.exists(filePath)) {
                    // 文件不存在，从缓存中移除
                    memoryTextStorage.invalidate(shareId);
                    throw new ShareNotFoundException("文件不存在或已被删除");
                }
            }
            
            // 更新后的分享信息重新保存到缓存
            memoryTextStorage.save(share);
            
            return share;
        }

        // 分享不存在或已过期
        throw new ShareNotFoundException("分享内容不存在或已过期");
    }

    // 获取文件用于下载
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

    public int cleanupExpiredShares() {
        // 实际项目中，这里应该查询数据库或文件系统
        // 为简化，我们只演示内存存储的清理
        // 在真实实现中，需要维护一个过期分享的索引

        // 由于Caffeine缓存自动过期，我们只需清理文件系统中的过期文件
        // 这里简化实现，返回一个模拟值
        int cleanedFiles = 0;
        log.info("清理了 {} 个过期文件分享", cleanedFiles);

        // 返回总清理数量
        return cleanedFiles;
    }

    // 验证分享访问权限
    private void validateShareAccess(ShareContent share) {
        // 检查是否过期 (24小时)
        LocalDateTime expiryTime = share.getCreateTime().plusHours(24);
        if (LocalDateTime.now().isAfter(expiryTime)) {
            throw new ShareNotFoundException("分享已过期");
        }
    }

    // 生成4位数字分享ID
    private String generateShareId() {
        for (int i = 0; i < 10; i++) {
            String id = String.format("%04d", ThreadLocalRandom.current().nextInt(0, 10000));
            // 检查ID是否已存在（简化实现）
            if (!memoryTextStorage.get(id).isPresent()) {
                return id;
            }
        }
        throw new RuntimeException("无法生成唯一分享ID，请重试");
    }
}