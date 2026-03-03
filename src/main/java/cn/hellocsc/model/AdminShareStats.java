package cn.hellocsc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminShareStats {
    private long total;
    private long file;
    private long text;
    private long totalViews;
}
