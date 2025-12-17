package app.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 전역 설정 클래스
 * <p>비동기로 통계 데이터를 처리하기 위해 스케줄러의 스레드 풀 크기를 설정한다.</p>
 * @see app.statistics.presentation.DistinctDataCountBatch DistinctDataCountBatch
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(100);
        scheduler.initialize();
        taskRegistrar.setScheduler(scheduler);
    }
}