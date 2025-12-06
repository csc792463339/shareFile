package cn.hellocsc.service;

import lombok.extern.slf4j.Slf4j;
import cn.hellocsc.model.ShareContent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private String storagePath = "file";


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


}