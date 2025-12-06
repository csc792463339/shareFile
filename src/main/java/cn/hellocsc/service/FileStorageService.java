package cn.hellocsc.service;

import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.model.ShareContent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileStorageService {

    private String storagePath="file";

    public ShareContent saveFile(MultipartFile file, ShareContent share) throws IOException {
        // 创建唯一文件名
        String originalName = file.getOriginalFilename();
        String extension = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String uniqueName = UUID.randomUUID() + extension;

        // 保存文件
        Path filePath = Paths.get(storagePath, uniqueName);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 设置文件信息
        share.setFileName(originalName);
        share.setContentType(file.getContentType());
        share.setSize(file.getSize());
        share.setFilePath(filePath.toString());

        return share;
    }

    public Path getFile(String filePath) {
        return Paths.get(filePath);
    }

    /**
     * 清理过期文件
     * @param hours 文件过期时间（小时）
     * @return 删除的文件数量
     */
    public int deleteExpiredFiles(int hours) {
        AtomicInteger deletedCount = new AtomicInteger(0);
        try {
            Path dir = Paths.get(storagePath);
            if (!Files.exists(dir)) {
                return 0;
            }

            // 计算截止时间戳
            long cutOff = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);

            try (Stream<Path> files = Files.list(dir)) {
                files.forEach(path -> {
                    File file = path.toFile();
                    // 如果是普通文件且最后修改时间早于截止时间
                    if (file.isFile() && file.lastModified() < cutOff) {
                        if (file.delete()) {
                            deletedCount.incrementAndGet();
                            log.debug("已删除过期文件: {}", file.getName());
                        } else {
                            log.warn("无法删除过期文件: {}", file.getName());
                        }
                    }
                });
            }
        } catch (IOException e) {
            log.error("清理过期文件失败", e);
        }
        return deletedCount.get();
    }
}