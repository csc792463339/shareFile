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

        // --- 修复点开始 ---
        // 1. 获取绝对路径，防止不同组件对相对路径解析不一致
        Path filePath = Paths.get(storagePath, uniqueName).toAbsolutePath().normalize();

        // 2. 打印日志方便调试（可选）
        log.info("保存文件到: {}", filePath);

        // 3. 确保存储目录存在
        Files.createDirectories(filePath.getParent());

        // 4. 使用 transferTo (零拷贝)
        // 现在传入的是绝对路径 File 对象，Undertow 也就不会去临时目录找了
        file.transferTo(filePath.toFile());
        // --- 修复点结束 ---

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
     */
    public int deleteExpiredFiles(int hours) {
        AtomicInteger deletedCount = new AtomicInteger(0);
        try {
            // 同样使用绝对路径进行查找清理
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