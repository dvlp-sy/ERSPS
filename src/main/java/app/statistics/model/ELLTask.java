package app.statistics.model;

import exaloglog.ExaLogLog;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * 독립적인 로컬 ExaLogLog 스케치를 생성하여 반환하는 Callable 작업
 * @implSpec 주어진 파티션에 대한 로컬 ExaLogLog 스케치를 생성하고, 모든 해시 값을 스케치에 추가하여 반환한다.
 * {@link app.statistics.application.ELLStreamProcessor ELLStreamProcessor}에서 병렬로 수행된다.
 */
public class ELLTask implements Callable<ExaLogLog> {
    private final List<Long> hashPartition;
    private final int p;
    private final int t;
    private final int d;

    public ELLTask(List<Long> hashPartition, int p, int t, int d) {
        this.hashPartition = hashPartition;
        this.p = p;
        this.t = t;
        this.d = d;
    }

    @Override
    public ExaLogLog call() throws Exception {
        ExaLogLog localSketch = ExaLogLog.create(t, d, p);
        // 파티션의 모든 해시 값을 스케치에 add
        for (long hash : hashPartition) {
            localSketch.add(hash);
        }
        return localSketch;
    }
}