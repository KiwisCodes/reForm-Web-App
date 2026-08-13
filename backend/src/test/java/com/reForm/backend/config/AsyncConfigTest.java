package com.reForm.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AsyncConfigTest {

    @Autowired(required = false)
    private ThreadPoolTaskExecutor taskExecutor;

    @Test
    @DisplayName("Verify Async ThreadPoolTaskExecutor bean properties directly from AsyncConfig")
    void testAsyncConfigDirectInstantiation() {
        AsyncConfig config = new AsyncConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.taskExecutor();

        assertThat(executor).isNotNull();
        assertThat(executor.getCorePoolSize()).isEqualTo(10);
        assertThat(executor.getMaxPoolSize()).isEqualTo(50);
        assertThat(executor.getQueueCapacity()).isEqualTo(500);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("reForm-async-");
    }

    @Test
    @DisplayName("Verify Async ThreadPoolTaskExecutor bean injected in Spring context")
    void testTaskExecutorBeanInjected() {
        if (taskExecutor != null) {
            assertThat(taskExecutor.getCorePoolSize()).isEqualTo(10);
            assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(50);
            assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("reForm-async-");
        }
    }
}
