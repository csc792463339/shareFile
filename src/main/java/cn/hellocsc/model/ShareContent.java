package cn.hellocsc.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShareContent {
    private String shareId;          // 4位数字分享码
    private boolean file;            // 是否是文件
    private String fileName;         // 文件名 (文件分享时)
    private String contentType;      // MIME类型
    private long size;               // 文件大小/文本长度
    private String textContent;      // 文本内容 (文本分享时)
    private boolean richText;        // 是否富文本
    private LocalDateTime createTime; // 创建时间
    private int viewCount;           // 查看次数

    // 文件存储路径 - 持久化时需要保存
    private String filePath; // 服务器文件路径

    // 仅内存存储使用
    @JsonIgnore
    private transient byte[] fileBytes; // 小文件内容 (内存存储)
}