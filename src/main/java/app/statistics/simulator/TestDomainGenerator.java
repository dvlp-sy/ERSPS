package app.statistics.simulator;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ExaLogLog 시뮬레이션을 위한 테스트 도메인을 제공하는 유틸리티 클래스
 * @implSpec 100개 이상의 고유 도메인과 시뮬레이션용 랜덤 도메인을 생성하는 정적 메서드를 포함한다.
 */
@UtilityClass
public final class TestDomainGenerator {

    private static final Random RANDOM = new Random();

    // 도메인 접두사
    private static final String[] BASE_NAMES = {
            "google", "amazon", "apple", "microsoft", "facebook", "twitter", "linkedin",
            "alibaba", "tencent", "samsung", "lgcorp", "hyundai", "oracle", "adobe",
            "netflix", "spotify", "reddit", "pinterest", "etsy", "ebay", "paypal",
            "visa", "mastercard", "cisco", "ibm", "dell", "hp", "intel", "nvidia",
            "amd", "tesla", "spacex", "boeing", "airbus", "siemens", "sony", "nintendo"
    };

    // 탑 레벨 도메인
    private static final String[] TLD = {
            ".com", ".net", ".org", ".co.kr", ".kr", ".io", ".dev", ".ai", ".tech", ".info", ".biz"
    };

    // 서브 도메인
    private static final String[] SERVICE_PREFIX = {
            "", "mail.", "web.", "api.", "app.", "secure.", "cloud.", "data."
    };

    /**
     * 시뮬레이션용으로 사용할 랜덤 도메인을 생성하는 메서드
     * @param count 생성할 도메인 수
     * @return 랜덤 도메인 문자열 리스트
     */
    public static List<String> generateRandomDomains(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    // BASE_NAMES와 TLD, SERVICE_PREFIX 랜덤 조합
                    String baseName = BASE_NAMES[RANDOM.nextInt(BASE_NAMES.length)];
                    String tld = TLD[RANDOM.nextInt(TLD.length)];
                    String prefix = SERVICE_PREFIX[RANDOM.nextInt(SERVICE_PREFIX.length)];
                    return prefix + baseName + tld;
                })
                .collect(Collectors.toList());
    }
}