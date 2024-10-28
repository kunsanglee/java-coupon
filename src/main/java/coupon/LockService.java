package coupon;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class LockService {

    private final RedissonClient redissonClient;

    public void executeWithLock(Long id, Runnable runnable) {
        RLock lock = redissonClient.getLock("lock:coupon:" + id);
        boolean isLocked = lock.tryLock();
        if (isLocked) {
            try {
                runnable.run();
            } finally {
                lock.unlock();
                log.info("Lock released.");
            }
            return;
        }
        log.info("Could not acquire lock.");
    }

    public <T> T executeWithLock(Long id, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock("lock:coupon:" + id);
        boolean isLocked = lock.tryLock();
        if (isLocked) {
            try {
                return supplier.get();
            } finally {
                lock.unlock();
                log.info("Lock released.");
            }
        }
        log.info("Could not acquire lock.");
        return supplier.get();
    }
}
