package cn.hellocsc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminSharesPageResponse {
    private List<ShareContent> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
    private AdminShareStats stats;
}
