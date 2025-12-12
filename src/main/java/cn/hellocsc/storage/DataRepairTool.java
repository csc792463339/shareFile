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
import java.util.HashMap;
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
        TypeReference<Map<String, ShareContent>> typeRef = new TypeReference<>() {};
        Map<String, ShareContent> shareData = objectMapper.readValue(jsonContent, typeRef);

        // 扫描存储目录中的所有文件
        Map<String, Long> filesBySize = scanStorageDirectory();

        int repaired = 0;
        for (ShareContent share : shareData.values()) {
            if (share.isFile() && share.getFilePath() == null) {
                String matchedFile = findMatchingFile(share, filesBySize);
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

    private Map<String, Long> scanStorageDirectory() throws IOException {
        Map<String, Long> filesBySize = new HashMap<>();

        // 扫描主存储目录
        scanDirectory(Paths.get(storagePath), filesBySize);

        // 为了兼容性，也扫描旧的 "file" 目录
        Path oldStorageDir = Paths.get("file");
        if (Files.exists(oldStorageDir)) {
            scanDirectory(oldStorageDir, filesBySize);
        }

        return filesBySize;
    }

    private void scanDirectory(Path dir, Map<String, Long> filesBySize) throws IOException {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                 .forEach(file -> {
                     try {
                         long size = Files.size(file);
                         filesBySize.put(file.getFileName().toString(), size);
                     } catch (IOException e) {
                         log.warn("无法读取文件大小: {}", file);
                     }
                 });
        }
    }

    private String findMatchingFile(ShareContent share, Map<String, Long> filesBySize) {
        // 按文件大小匹配（这是最可靠的方式，因为UUID文件名已经改变）
        for (Map.Entry<String, Long> entry : filesBySize.entrySet()) {
            if (entry.getValue().equals(share.getSize())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
