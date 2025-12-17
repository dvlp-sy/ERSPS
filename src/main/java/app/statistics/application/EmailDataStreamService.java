package app.statistics.application;

import app.statistics.infra.TaskMementoRepository;
import app.statistics.model.enums.TaskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * 회원가입 이메일 도메인 수 통계를 위한 {@code DataStreamService} 구현체
 * <p>
 *     ELL(Exponential Log Log) 알고리즘을 사용하여 고유 이메일 도메인 수를 추정한다.
 *     회원가입 시 제공된 이메일 주소의 도메인 부분을 해시하여 Long 타입으로 변환한 후 스트림으로 처리한다.
 *     특히 임시 이메일을 사용하여 가입을 시도하는 대량 가입 공격(Sign-up Attack)를 탐지하는 데 활용할 수 있다.
 * </p>
 */
@Slf4j
@Service
public class EmailDataStreamService extends AbstractDataStreamService {
    private static final int P = 20;
    private static final int T = 4;
    private static final int D = 5;
    private static final int NUM_THREADS = 4;

    public EmailDataStreamService(TaskMementoRepository taskMementoRepository) {
        super(P, T, D, NUM_THREADS, taskMementoRepository, new LinkedBlockingQueue<>());
    }

    @Override
    public void processStreamData() {
        process(TaskType.DISTINCT_ACCOUNT_EMAIL_DOMAIN_COUNT);
    }
}
