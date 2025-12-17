package app.statistics.application;

import app.statistics.infra.TaskMementoRepository;
import app.statistics.model.enums.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * IP 주소 수 통계를 위한 {@code DataStreamService} 구현체
 * <p>
 *     ELL(Exponential Log Log) 알고리즘을 사용하여 고유 IP 주소 수를 추정한다.
 *     스트림으로 전달된 IP 주소의 해시 값을 Long 타입으로 변환한 후 처리한다.
 *     전통적인 Volume-based DDoS 공격 탐지에 활용할 수 있다.
 * </p>
 */
@Slf4j
@Service
public class IpDataStreamService extends AbstractDataStreamService {
    private static final int P = 20;
    private static final int T = 4;
    private static final int D = 5;
    private static final int NUM_THREADS = 4;

    public IpDataStreamService(TaskMementoRepository taskMementoRepository) {
        super(P, T, D, NUM_THREADS, taskMementoRepository, new LinkedBlockingQueue<>());
    }

    @Override
    public void processStreamData() {
        process(TaskType.DISTINCT_IP_ADDRESS_COUNT);
    }
}
