package app.statistics.simulator;

import app.statistics.application.AccountDataStreamService;
import app.statistics.application.EmailDataStreamService;
import app.statistics.application.IpDataStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 30초에 한 번씩 세 가지 메트릭에 대해 랜덤 데이터를 생성하여 ELL 스트림 프로세서에 전달하는 테스트 배치 컨트롤러
 * <p>
 *     트래픽이 들어오는 상황을 시뮬레이션하기 위해 고유한 ACCOUNT_ID, IP 주소, 이메일 도메인에 대한 랜덤 해시 값을 생성한다.
 *     각 메트릭에 대해 10,000에서 1,000,000 사이의 랜덤 개수의 해시 값을 생성하여 스트림 서비스에 추가한다.
 *     생성된 해시 값은 ExaLogLog 알고리즘을 사용하여 고유 개수를 추정하는 데 사용된다.
 * </p>
 * @see app.statistics.model.enums.TaskType TaskType
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataBatch {

    private final AccountDataStreamService accountStreamService;
    private final IpDataStreamService ipDataStreamService;
    private final EmailDataStreamService emailDataStreamService;
    private final Random random = new Random();

    @Scheduled(cron = "0/5 * * * * *")
    public void processTestDataBatch() {
        final int batchSize = 10000 + random.nextInt(990000); // 10,000에서 1,000,000 사이의 랜덤 데이터
        log.info("TEST:DATA:CRTE::: Generating {} random events for ACCOUNT_ID, IP, and DOMAIN metrics.", batchSize);
        try {
            addAccountHashes(batchSize);
            addRandomIpHashes(batchSize);
            generateRandomDomainHashes(batchSize);
            log.info("TEST:DATA:CMPL::: Successfully added {} random events for all metrics.", batchSize);
        } catch (Exception e) {
            log.error("TEST:DATA:ERR::: Error generating or adding test data: {}", e.getMessage());
        }
    }

    private long hashString(String input) {
        if (input == null) return 0L;
        return (long) input.hashCode() * 31L;
    }

    private void addAccountHashes(Integer count) {
        // 사용자 수 시뮬레이션
        accountStreamService.addHashList(random.longs(count, 0, Long.MAX_VALUE).boxed()
                        .map(accountStreamService::normalize)
                        .collect(Collectors.toList()));
    }

    private void addRandomIpHashes(Integer count) {
        List<Long> hashes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            // 고유 IP 주소 수 시뮬레이션
            String ip = String.format("%d.%d.%d.%d",
                    random.nextInt(256), random.nextInt(256), random.nextInt(256), random.nextInt(256));
            hashes.add(accountStreamService.normalize(hashString(ip)));
        }
        ipDataStreamService.addHashList(hashes);
    }

    private void generateRandomDomainHashes(Integer count) {
        // 고유 도메인 수 시뮬레이션
        List<Long> hashes = TestDomainGenerator.generateRandomDomains(count).stream()
                .map(this::hashString)
                .map(accountStreamService::normalize)
                .toList();
        emailDataStreamService.addHashList(hashes);
    }
}
