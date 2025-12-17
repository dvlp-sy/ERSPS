package app.statistics.simulator;

import app.statistics.application.ELLStreamProcessor;
import app.statistics.model.TaskMemento;
import app.statistics.model.enums.TaskType;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ExaLogLog 알고리즘의 정확도와 실제 메모리 점유율을 검증하기 위한 시뮬레이터
 * <p>
 *     매 분기마다 10,000에서 1,000,000개의 랜덤 이벤트를 생성하여 {@link ELLStreamProcessor}로 처리한다.
 *     윈도우마다 실제 카디널리티, 추정 카디널리티, 오류율, 처리 시간, ExaLogLog의 메모리 사용량을 CSV 파일로 저장한다.
 * </p>
 */
public class RealTimeELLSimulation {
    private static final int TOTAL_WINDOWS = 100;
    private static final String CSV_FILE = "ell_simulation_results.csv";

    private static final int P = 20;
    private static final int T = 4;
    private static final int D = 5;
    private static final int NUM_THREADS = 4;

    public static void main(String[] args) throws Exception {
        ELLStreamProcessor processor = new ELLStreamProcessor(P, T, D, NUM_THREADS);
        Random random = new Random();
        Set<Long> groundTruthSet = new HashSet<>();

        try (PrintWriter writer = new PrintWriter(new FileWriter(CSV_FILE))) {
            writer.println("Window_ID,Actual_Count,Estimated_Count,Error_Rate(%),Processing_Time(ms),ExaLogLog_Size(KB)");

            for (int w = 1; w <= TOTAL_WINDOWS; w++) {
                List<Long> incomingData = createTestData(random);                   // 데이터 생성
                long actualCount = getActualCount(groundTruthSet, incomingData);    // 실제 데이터 개수 계산

                long startTime = System.currentTimeMillis();
                processor.processBatchAndMerge(incomingData);                       // 배치 처리

                long duration = System.currentTimeMillis() - startTime;             // 처리 시간 측정
                double realSketchSizeKB = getActualExaLogLogSizeKB(processor);      // ExaLogLog 메모리 사용량 측정

                TaskMemento result = processor.rollWindow(TaskType.DISTINCT_ACCOUNT_ID_COUNT, duration);
                double estimatedCount = result.getDistinctCount();
                double errorRate = Math.abs(estimatedCount - actualCount) / actualCount * 100.0;    // 카디널리티 추정 결과 집계

                writer.printf("%d,%d,%.2f,%.4f,%d,%.2f%n",
                        w, actualCount, estimatedCount, errorRate, duration, realSketchSizeKB);     // 데이터 저장
                writer.flush();
            }
        }
        processor.shutdown();
    }

    /**
     * 테스트용 랜덤 데이터 생성 메서드
     * @param random 랜덤 객체
     * @return 생성된 랜덤 데이터 리스트
     */
    private static List<Long> createTestData(Random random) {
        int eventCount = 10000 + random.nextInt(990000); // 10,000 ~ 1,000,000 이벤트
        List<Long> incomingData = random.longs(eventCount, 0, Long.MAX_VALUE)
                .map(RealTimeELLSimulation::mix64)
                .boxed()
                .collect(Collectors.toList());
        return incomingData;
    }

    /**
     * 실제 카디널리티를 계산하는 메서드
     * @param groundTruthSet 중복 제거를 위한 집합 (고유 카디널리티 계산용)
     * @param incomingData 입력 데이터 리스트
     * @return 실제 카디널리티
     */
    private static long getActualCount(Set<Long> groundTruthSet, List<Long> incomingData) {
        groundTruthSet.clear();
        groundTruthSet.addAll(incomingData);
        return groundTruthSet.size();
    }

    /**
     * {@link exaloglog.ExaLogLog ExaLogLog} 객체의 {@code byte[] state} 배열 길이를 조회하는 메서드
     * @implNote Java Reflection을 사용하여 private 필드에 접근한다.
     * @param processor 현재 실행 중인 프로세서
     * @return 실제 할당된 메모리 크기 (KB)
     */
    private static double getActualExaLogLogSizeKB(ELLStreamProcessor processor) {
        try {
            Field sketchField = ELLStreamProcessor.class.getDeclaredField("currentWindowSketch");
            sketchField.setAccessible(true);

            Object exaLogLogInstance = sketchField.get(processor);
            if (exaLogLogInstance == null) {
                return 0.0;
            }

            Field stateField = exaLogLogInstance.getClass().getDeclaredField("state");
            stateField.setAccessible(true);
            byte[] stateArray = (byte[]) stateField.get(exaLogLogInstance);

            // 실제 배열의 길이 (Bytes) + 객체 헤더(약 24~32바이트)
            final long actualSizeBytes = stateArray.length;

            return actualSizeBytes / 1024.0;
        } catch (Exception e) {
            return -1.0;
        }
    }

    /**
     * MurmurHash3의 64비트 믹스 메서드 (데이터 보정용)
     * @param hash 입력 해시 값
     * @return 보정된 해시 값
     */
    private static long mix64(long hash) {
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        return hash ^ (hash >>> 31);
    }
}