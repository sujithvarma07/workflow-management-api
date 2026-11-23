package com.relic.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * A dedicated, explicitly bounded pool for background jobs (bulk
     * imports, report generation) - deliberately NOT Spring's default
     * SimpleAsyncTaskExecutor, which creates an unbounded number of threads
     * (one per task, no queue, no cap). A bounded pool plus a bounded queue
     * means a burst of job submissions backs up and eventually rejects
     * (CallerRunsPolicy: the submitting thread itself runs the job rather
     * than being dropped) instead of exhausting memory or the OS thread
     * limit.
     */
    @Bean(name = "jobTaskExecutor")
    public Executor jobTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("job-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
