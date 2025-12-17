package app.statistics.application;

import app.statistics.infra.TaskMementoRepository;
import app.statistics.model.TaskMemento;
import app.statistics.model.enums.TaskType;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
public abstract class AbstractDataStreamService implements DataStreamService {
    private static final int MAX_BATCH_SIZE = 1_000_000_000;

    private final TaskMementoRepository taskMementoRepository;
    private final ELLStreamProcessor ellStreamProcessor;
    private final BlockingQueue<Long> eventQueue;

    public AbstractDataStreamService(int P, int T, int D, int NUM_THREADS,
                                     TaskMementoRepository taskMementoRepository,
                                     BlockingQueue<Long> eventQueue) {
        this.taskMementoRepository = taskMementoRepository;
        this.ellStreamProcessor = new ELLStreamProcessor(P, T, D, NUM_THREADS);
        this.eventQueue = eventQueue;
    }

    @Override
    public void addHashList(List<Long> hashList) {
        eventQueue.addAll(hashList);
    }

    @Override
    public abstract void processStreamData();

    /**
     * 하위 클래스에서 taskType을 받아 배치 처리를 수행하는 메서드
     * @implNote {@link #processStreamData()}에서 호출한다.
     * @param taskType 처리할 작업 유형
     */
    protected void process(TaskType taskType) {
        log.info("ELL_:BTCH:STRT::: Starting {} batch processing", taskType);
        if (eventQueue.isEmpty()) {
            log.info("ELL_:BTCH:CMPL::: No events to process");
            return;
        }
        List<Long> batch = new ArrayList<>();
        int elementsDrained = eventQueue.drainTo(batch, MAX_BATCH_SIZE);

        if (elementsDrained > 0) {
            try {
                long processingTime = processBatchAndMerge(batch);
                TaskMemento taskMemento = ellStreamProcessor.rollWindow(taskType, processingTime);
                if (taskMemento == null) {
                    log.warn("ELL_:WARN:BTCH::: TaskMemento is null after rolling window for {}", taskType);
                    return;
                }
                taskMementoRepository.save(taskMemento);
                log.info("ELL_:BTCH:CMPL::: Completed processing batch for ip address. Processed {} events.", elementsDrained);
            } catch (Exception e) {
                log.error("ELL_:ERR_:BTCH::: Error processing batch for ip address: {}", e.getMessage());
                throw new IllegalStateException("Failed to process batch", e);
            }
        }
    }

    /**
     * 배치를 처리하고 병합하는 메서드
     * @implNote 호출 메서드에서 예외를 처리해야 한다.
     * @param batch 처리할 데이터 배치
     * @return 처리 시간 (밀리초)
     */
    private long processBatchAndMerge(List<Long> batch) throws Exception {
        long startTime = System.currentTimeMillis(); // 시작 시간 측정
        ellStreamProcessor.processBatchAndMerge(batch);
        long endTime = System.currentTimeMillis();   // 종료 시간 측정
        return endTime - startTime;
    }
}
