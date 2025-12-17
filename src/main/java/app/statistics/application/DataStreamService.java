package app.statistics.application;

import java.util.List;

/**
 * 데이터를 스트림 처리하기 위한 인터페이스
 * @implSpec {@link ELLStreamProcessor}를 활용하여 해시된 데이터 스트림을 큐에 추가하는 {@code addHashList} 메서드와
 * 스트림 데이터를 처리하는 {@code processStreamData} 메서드를 정의해야 한다.<br>
 * normalize 메서드는 MurmurHash3의 Mix 함수(SplitMix64 변형)를 사용하여 데이터 품질을 보정하는 기본 구현을 제공한다.
 */
public interface DataStreamService {

    /**
     * 해시된 데이터 리스트를 이벤트 큐에 추가하는 메서드
     * @param hashList 해시 값 리스트
     */
    void addHashList(List<Long> hashList);

    /**
     * 큐에 쌓인 이벤트 데이터를 배치로 처리하여 ELLStreamProcessor에 전달하는 메서드
     * @implSpec ELLStreamProcessor의 병렬 처리 기능을 활용하여 스트림 데이터를 효율적으로 처리할 수 있다.
     * @see ELLStreamProcessor
     */
    void processStreamData();

    /**
     * 데이터 품질 보정을 위한 Normalization 메서드
     * <p>ExaLogLog는 입력값의 비트가 균일하게 분포(Uniform Distribution)되어 있다고 가정한다.
     * 정확도를 높이기 위해 추가적인 보정이 필요할 수 있다.</p>
     * @param z 원본 long 값
     * @return 보정된 long 값
     */
    default long normalize(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
