package cn.hellocsc.storage;

import cn.hellocsc.model.AdminSharesPageResponse;
import cn.hellocsc.model.ShareContent;

import java.time.Duration;
import java.util.Optional;

public interface ShareRepository {
    void saveShare(ShareContent share);

    Optional<ShareContent> getShare(String shareId, boolean incrementViewCount);

    void deleteShare(String shareId);

    boolean reserveShareId(String shareId, Duration ttl);

    boolean exists(String shareId);

    AdminSharesPageResponse queryShares(int page, int size, String type, String sort);

    void flushDirtyViewCountsToBackup();

    void bootstrapFromBackupIfEmpty();
}
