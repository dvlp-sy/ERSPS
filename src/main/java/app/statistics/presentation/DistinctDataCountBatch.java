package app.statistics.presentation;

import app.statistics.application.DataStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 고유 데이터 수를 집계하기 위한 배치 작업 클래스
 * <p>
 *     일정 시간 간격으로 {@link DataStreamService} 구현체를 병렬로 실행하여 고유 데이터 수를 집계한다.
 *     스케줄러 구성은 {@link app.config.SchedulerConfig SchedulerConfig}에서 확인할 수 있다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistinctDataCountBatch {
    private final List<DataStreamService> dataStreamServiceList;
    private final ExecutorService executorService =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    @Scheduled(cron = "0/5 * * * * *")
    public void process() {
        log.info("ELL_:BTCH:STRT::: Starting batch processing");
        try {
            List<CompletableFuture<Void>> futures = dataStreamServiceList.stream()
                    .map(service ->
                            CompletableFuture.runAsync(service::processStreamData, executorService)
                    )
                    .toList();
            // 모든 비동기 작업이 완료될 때까지 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(1, TimeUnit.MINUTES);

            log.info("ELL_:BTCH:CMPL::: Completed batch processing");
        } catch (Exception e) {
            log.error("ELL_:BTCH:ERR_::: Error during batch processing: {}", e.getMessage());
        }
    }
}
