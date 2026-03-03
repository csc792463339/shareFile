package cn.hellocsc.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBootstrapMigrationRunner implements ApplicationRunner {

    private final ShareRepository shareRepository;

    @Override
    public void run(ApplicationArguments args) {
        log.info("执行 Redis 冷启动迁移检查...");
        shareRepository.bootstrapFromBackupIfEmpty();
    }
}
