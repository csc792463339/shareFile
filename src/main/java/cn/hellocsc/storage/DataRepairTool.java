package cn.hellocsc.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cn.hellocsc.model.ShareContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
public class DataRepairTool implements CommandLineRunner {

    @Value("${app.storage.metadata-file:./data/shares_metadata.json}")
    private String metadataFilePath;

    @Value("${storage.path:files}")
    private String storagePath;

    @Value("${app.storage.repair:false}")
    private boolean enableRepair;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void run(String... args) throws Exception {
        log.info("DataRepairTool.run() 被调用，enableRepair = {}", enableRepair);

        if (!enableRepair) {
            log.info("数据修复功能未启用，跳过修复");
            return;
        }

        log.info("开始执行数据修复...");
        objectMapper.registerModule(new JavaTimeModule());

        repairMissingFilePaths();
        log.info("数据修复完成");
    }

    private void repairMissingFilePaths() throws IOException {
        Path metadataFile = Paths.get(metadataFilePath);
        if (!Files.exists(metadataFile)) {
            log.info("元数据文件不存在，无需修复");
            return;
        }

        // 读取现有数据
        String jsonContent = Files.readString(metadataFile, StandardCharsets.UTF_8);
        if (jsonContent.trim().isEmpty()) {
            log.info("元数据文件为空，无需修复");
            return;
        }
        TypeReference<Map<String, ShareContent>> typeRef = new TypeReference<>() {};
        Map<String, ShareContent> shareData = objectMapper.readValue(jsonContent, typeRef);

        // 扫描存储目录中的所有文件
        List<StorageFileInfo> storageFiles = scanStorageDirectory();

        int repaired = 0;
        for (ShareContent share : shareData.values()) {
            if (share.isFile() && share.getFilePath() == null) {
                String matchedFile = findMatchingFile(share, storageFiles);
                if (matchedFile != null) {
                    share.setFilePath(matchedFile);
                    repaired++;
                    log.info("修复文件路径 - 分享ID: {}, 文件: {}", share.getShareId(), matchedFile);
                } else {
                    log.warn("无法找到匹配的文件 - 分享ID: {}, 文件名: {}, 大小: {}",
                            share.getShareId(), share.getFileName(), share.getSize());
                }
            }
        }

        if (repaired > 0) {
            // 备份原文件
            Path backupFile = Paths.get(metadataFilePath + ".backup." + System.currentTimeMillis());
            Files.copy(metadataFile, backupFile);
            log.info("已备份原数据文件到: {}", backupFile);

            // 写入修复后的数据
            objectMapper.writeValue(metadataFile.toFile(), shareData);
            log.info("成功修复 {} 个文件记录的路径信息", repaired);
        } else {
            log.info("没有需要修复的记录");
        }
    }

    private List<StorageFileInfo> scanStorageDirectory() throws IOException {
        List<StorageFileInfo> storageFiles = new ArrayList<>();

        // 扫描主存储目录
        scanDirectory(Paths.get(storagePath).toAbsolutePath().normalize(), false, storageFiles);

        // 为了兼容性，也扫描旧的 "file" 目录
        Path oldStorageDir = Paths.get("file").toAbsolutePath().normalize();
        if (Files.exists(oldStorageDir)) {
            scanDirectory(oldStorageDir, true, storageFiles);
        }

        return storageFiles;
    }

    private void scanDirectory(Path dir, boolean useAbsolutePath, List<StorageFileInfo> storageFiles) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                 .forEach(file -> {
                     try {
                         String fileName = file.getFileName().toString();
                         long size = Files.size(file);
                         String storedPath = useAbsolutePath ? file.toAbsolutePath().toString() : fileName;
                         storageFiles.add(new StorageFileInfo(
                                 storedPath,
                                 size,
                                 extractExtension(fileName)));
                     } catch (IOException e) {
                         log.warn("无法读取文件大小: {}", file);
                     }
                 });
        }
    }

    private String findMatchingFile(ShareContent share, List<StorageFileInfo> storageFiles) {
        List<StorageFileInfo> sizeMatches = storageFiles.stream()
                .filter(file -> file.size == share.getSize())
                .toList();

        if (sizeMatches.isEmpty()) {
            return null;
        }

        if (sizeMatches.size() == 1) {
            return sizeMatches.get(0).storedPath;
        }

        String expectedExtension = extractExtension(share.getFileName());
        if (!expectedExtension.isEmpty()) {
            List<StorageFileInfo> extensionMatches = sizeMatches.stream()
                    .filter(file -> expectedExtension.equalsIgnoreCase(file.extension))
                    .toList();

            if (extensionMatches.size() == 1) {
                return extensionMatches.get(0).storedPath;
            }

            if (extensionMatches.size() > 1) {
                log.warn("按大小+扩展名匹配到多个候选，跳过修复 - 分享ID: {}, 候选数: {}",
                        share.getShareId(), extensionMatches.size());
                return null;
            }
        }

        log.warn("按大小匹配到多个候选，跳过修复 - 分享ID: {}, 候选数: {}",
                share.getShareId(), sizeMatches.size());
        return null;
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private static class StorageFileInfo {
        private final String storedPath;
        private final long size;
        private final String extension;

        private StorageFileInfo(String storedPath, long size, String extension) {
            this.storedPath = storedPath;
            this.size = size;
            this.extension = extension;
        }
    }
}
