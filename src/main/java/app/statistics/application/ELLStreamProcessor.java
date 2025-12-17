package app.statistics.application;

import app.statistics.model.ELLTask;
import app.statistics.model.TaskMemento;
import app.statistics.model.enums.TaskType;
import exaloglog.ExaLogLog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * 실시간 데이터 스트림에서 활성 사용자 수를 병렬로 처리하고 집계하는 클래스
 * @implSpec 내부적으로 {@link ThreadPoolExecutor}를 사용하여 멀티 스레드로 데이터를 처리한다.
 * 각 배치의 해시 값을 분할하여 여러 스레드에서 독립적인 {@link ExaLogLog} 스케치를 생성한 후 최종적으로 마스터 스케치에 병합한다.
 */
public class ELLStreamProcessor {
    private final ThreadPoolExecutor executor;
    private ExaLogLog currentWindowSketch;        // 현재 윈도우의 마스터 스케치
    private final int numThreads;

    private final int p;
    private final int t;
    private final int d;

    public ELLStreamProcessor(int p, int t, int d, int numThreads) {
        this.p = p;
        this.t = t;
        this.d = d;
        this.numThreads = numThreads;
        this.currentWindowSketch = ExaLogLog.create(t, d, p);
        this.executor = new ThreadPoolExecutor(
                numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()
        );
    }

    /**
     * 현재 스케치를 종료하고 새로운 1분 윈도우를 시작하는 메서드
     * @return 완료된 윈도우의 태스크 결과
     */
    public TaskMemento rollWindow(TaskType taskType, long processingTimeMs) {
        if (currentWindowSketch == null) {
            return null;
        }
        final double finalCount = currentWindowSketch.getDistinctCount();
        resetNewWindow();
        return TaskMemento.of(taskType, LocalDateTime.now(), finalCount, processingTimeMs);
    }

    /**
     * 새로운 윈도우를 위해 현재 마스터 스케치를 초기화하는 메서드
     * @implSpec {@link ExaLogLog}의 create 메서드를 사용하여 새로운 스케치를 생성한다.
     */
    private void resetNewWindow() {
        currentWindowSketch = ExaLogLog.create(t, d, p);
    }

    /**
     * 실시간으로 들어오는 사용자 해시 값 배치를 처리하고 마스터 스케치에 병합하는 메서드
     * 마스터 스케치 업데이트 시 동기화 블록을 사용한다.
     * @param incomingHashes 실시간으로 유입되는 해시 값 리스트
     */
    public void processBatchAndMerge(List<Long> incomingHashes) throws InterruptedException, ExecutionException {

        // 데이터 분할
        List<List<Long>> partitions = partitionData(incomingHashes, numThreads);

        // 작업 생성
        List<Callable<ExaLogLog>> tasks = partitions.stream()
                .map(partition -> new ELLTask(partition, p, t, d))
                .collect(Collectors.toList());

        // 작업 실행
        List<Future<ExaLogLog>> results = executor.invokeAll(tasks);

        // 결과 수집 및 병합
        ExaLogLog batchMergedSketch = null;

        for (Future<ExaLogLog> future : results) {
            ExaLogLog localSketch = future.get();
            if (batchMergedSketch == null) {
                batchMergedSketch = localSketch;
            } else {
                batchMergedSketch = ExaLogLog.merge(batchMergedSketch, localSketch);
            }
        }

        // 마스터 스케치 업데이트
        if (batchMergedSketch != null) {
            synchronized (this) {
                currentWindowSketch = ExaLogLog.merge(currentWindowSketch, batchMergedSketch);
            }
        }
    }

    /**
     * 데이터를 균등한 파티션으로 분할하는 유틸리티 메서드
     * @param data 해시 값 리스트
     * @param numPartitions 분할할 파티션 수
     * @return 분할된 데이터 파티션 리스트
     */
    private List<List<Long>> partitionData(List<Long> data, int numPartitions) {
        int totalSize = data.size();
        int partitionSize = (int) Math.ceil((double) totalSize / numPartitions);

        List<List<Long>> partitions = new ArrayList<>();
        for (int i = 0; i < totalSize; i += partitionSize) {
            int endIndex = Math.min(i + partitionSize, totalSize);
            partitions.add(data.subList(i, endIndex));
        }
        return partitions;
    }

    public void shutdown() {
        executor.shutdown();
    }
}