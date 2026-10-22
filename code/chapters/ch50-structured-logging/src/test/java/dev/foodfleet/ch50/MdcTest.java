package dev.foodfleet.ch50;

import dev.foodfleet.ch50.mdc.MdcPropagation;
import dev.foodfleet.ch50.mdc.MdcTaskDecorator;
import dev.foodfleet.ch50.mdc.RequestMdc;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTest {

    @Test
    void requestMdcRemovesItsFieldsOnClose() {
        MDC.clear();
        try (RequestMdc ignored = RequestMdc.open("request.id", "r-1", "tenant", "blr")) {
            assertThat(MDC.get("request.id")).isEqualTo("r-1");
            assertThat(MDC.get("tenant")).isEqualTo("blr");
        }
        assertThat(MDC.get("request.id")).isNull();
        assertThat(MDC.get("tenant")).isNull();
    }

    @Test
    void aPlainPoolDoesNotSeeTheCallersMdc() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (RequestMdc ignored = RequestMdc.open("request.id", "r-2")) {
            assertThat(pool.submit(() -> MDC.get("request.id")).get()).isNull();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void wrapCarriesTheMdcAndRestoresTheWorkersOwn() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> MDC.put("worker", "w")).get();
            String seen;
            try (RequestMdc ignored = RequestMdc.open("request.id", "r-3")) {
                seen = pool.submit(MdcPropagation.wrap(() -> MDC.get("request.id") + "/" + MDC.get("worker"))).get();
            }
            assertThat(seen).isEqualTo("r-3/null");
            assertThat(pool.submit(() -> MDC.get("request.id") + "/" + MDC.get("worker")).get()).isEqualTo("null/w");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void wrapClearsWhatTheTaskLeavesBehindOnAPooledThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(MdcPropagation.wrap((Runnable) () -> MDC.put("request.id", "leaked"))).get();
            assertThat(pool.submit(() -> MDC.get("request.id")).get()).isNull();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void taskDecoratorWrapsRunnables() throws Exception {
        String[] seen = new String[1];
        Runnable r;
        try (RequestMdc ignored = RequestMdc.open("request.id", "r-4")) {
            r = new MdcTaskDecorator().decorate(() -> seen[0] = MDC.get("request.id"));
        }
        Thread t = new Thread(r);
        t.start();
        t.join();
        assertThat(seen[0]).isEqualTo("r-4");
    }
}
