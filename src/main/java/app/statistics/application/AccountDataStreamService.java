package app.statistics.application;

import app.statistics.infra.TaskMementoRepository;
import app.statistics.model.enums.TaskType;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * 접속자 수 통계를 위한 {@code DataStreamService} 구현체
 * <p>ELL(Exponential Log Log) 알고리즘을 사용하여 고유 계정 ID 수를 추정한다.</p>
 */
@Service
public class AccountDataStreamService extends AbstractDataStreamService {
    private static final int P = 20;
    private static final int T = 4;
    private static final int D = 5;
    private static final int NUM_THREADS = 4;

    public AccountDataStreamService(TaskMementoRepository taskMementoRepository) {
        super(P, T, D, NUM_THREADS, taskMementoRepository, new LinkedBlockingQueue<>());
    }

    @Override
    public void processStreamData() {
        process(TaskType.DISTINCT_ACCOUNT_ID_COUNT);
    }
}
