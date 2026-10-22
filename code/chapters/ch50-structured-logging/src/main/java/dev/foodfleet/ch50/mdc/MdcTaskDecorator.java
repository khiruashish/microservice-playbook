package dev.foodfleet.ch50.mdc;

import org.springframework.core.task.TaskDecorator;

/**
 * The same, for Spring's executors: Spring Boot applies a {@code TaskDecorator} bean to the auto-configured
 * {@code ThreadPoolTaskExecutor} (used by {@code @Async}) and {@code SimpleAsyncTaskExecutor}.
 */
public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcPropagation.wrap(runnable);
    }
}
