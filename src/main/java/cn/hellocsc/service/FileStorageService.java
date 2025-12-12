package cn.hellocsc.service;

import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.model.ShareContent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
public class FileStorageService {

    @Value("${storage.path:file}")
    private String storagePath;

    public ShareContent saveFile(MultipartFile file, ShareContent share) throws IOException {
        // 创建唯一文件名
        String originalName = file.getOriginalFilename();
        String extension = (originalName != null && originalName.contains("."))
                ? originalName.substring(originalName.lastIndexOf(".")) : "";
        String uniqueName = UUID.randomUUID() + extension;

        // 获取绝对路径
        Path storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
        Path filePath = storageDir.resolve(uniqueName);

        // 确保存储目录存在
        Files.createDirectories(storageDir);

        // --- 修复点开始 ---
        // 1. 先获取所有元数据！(因为 transferTo 可能会移动文件导致源文件丢失)
        long fileSize = file.getSize();
        String contentType = file.getContentType();

        // 2. 执行传输 (这一步之后，file 对象可能就不可用了)
        log.info("开始保存文件到: {}", filePath);
        file.transferTo(filePath.toFile());
        // --- 修复点结束 ---

        // 设置文件信息
        share.setFileName(originalName);
        share.setContentType(contentType);
        share.setSize(fileSize); // 使用之前保存的变量，而不是 file.getSize()
        // 存储相对于存储根目录的路径，便于跨环境使用
        share.setFilePath(uniqueName);

        return share;
    }

    public Path getFile(String fileName) {
        // 如果是绝对路径，直接使用（兼容旧数据）
        Path path = Paths.get(fileName);
        if (path.isAbsolute()) {
            return path;
        }
        // 否则，从存储根目录解析
        return Paths.get(storagePath).toAbsolutePath().normalize().resolve(fileName);
    }

    /**
     * 清理过期文件
     */
    public int deleteExpiredFiles(int hours) {
        AtomicInteger deletedCount = new AtomicInteger(0);
        try {
            Path dir = Paths.get(storagePath).toAbsolutePath().normalize();
            if (!Files.exists(dir)) {
                return 0;
            }
            long cutOff = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
            try (Stream<Path> files = Files.list(dir)) {
                files.forEach(path -> {
                    File file = path.toFile();
                    if (file.isFile() && file.lastModified() < cutOff) {
                        if (file.delete()) {
                            deletedCount.incrementAndGet();
                            log.debug("已删除过期文件: {}", file.getName());
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