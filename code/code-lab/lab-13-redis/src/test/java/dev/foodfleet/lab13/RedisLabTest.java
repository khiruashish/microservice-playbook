package dev.foodfleet.lab13;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The rate limiter and the lock against a real Redis: the same Lua files verify/redis_lab.py runs. */
@SpringBootTest
@Testcontainers
class RedisLabTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4").withExposedPorts(6379);

    @Autowired
    TenantRateLimiter limiter;

    @Autowired
    RedisLock lock;

    @Test
    void aTenantGetsItsBurstThenWaits() {
        TenantRateLimiter.Limit limit = new TenantRateLimiter.Limit(5, 1);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("tenant-a", "menus", limit).allowed()).isTrue();
        }
        TenantRateLimiter.Decision sixth = limiter.tryAcquire("tenant-a", "menus", limit);

        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfterMillis()).isBetween(1L, 1000L);
        assertThat(limiter.tryAcquire("tenant-b", "menus", limit).allowed()).isTrue();     // its own bucket
    }

    @Test
    void aLockIsHeldOnceAndReleasedOnlyByItsHolder() {
        Optional<RedisLock.Lease> first = lock.tryAcquire("menu-index", Duration.ofSeconds(5));
        assertThat(first).isPresent();
        assertThat(lock.tryAcquire("menu-index", Duration.ofSeconds(5))).isEmpty();

        RedisLock.Lease impostor = new RedisLock.Lease(first.get().key(), "not-the-token", 0);
        assertThat(lock.release(impostor)).isFalse();
        assertThat(lock.release(first.get())).isTrue();

        Optional<RedisLock.Lease> second = lock.tryAcquire("menu-index", Duration.ofSeconds(5));
        assertThat(second).isPresent();
        assertThat(second.get().fence()).isGreaterThan(first.get().fence());
    }

    @Test
    void anExpiredLeaseCannotDeleteTheNextHoldersLock() throws InterruptedException {
        RedisLock.Lease paused = lock.tryAcquire("payout-batch", Duration.ofMillis(100)).orElseThrow();
        Thread.sleep(200);                                                        // a GC pause longer than the TTL
        RedisLock.Lease next = lock.tryAcquire("payout-batch", Duration.ofSeconds(5)).orElseThrow();

        assertThat(lock.release(paused)).isFalse();
        assertThat(lock.tryAcquire("payout-batch", Duration.ofSeconds(5))).isEmpty();   // still next's
        assertThat(lock.release(next)).isTrue();
    }
}
