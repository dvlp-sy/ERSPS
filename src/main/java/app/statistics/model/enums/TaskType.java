package app.statistics.model.enums;

/**
 * ExaLogLog 태스크 유형을 나타내는 열거형
 */
public enum TaskType {
    /**
     * 고유 계정 ID 수 집계 태스크
     */
    DISTINCT_ACCOUNT_ID_COUNT,
    /**
     * 고유 계정 이메일 도메인 수 집계 태스크
     */
    DISTINCT_ACCOUNT_EMAIL_DOMAIN_COUNT,
    /**
     * 고유 IP 주소 수 집계 태스크
     */
    DISTINCT_IP_ADDRESS_COUNT,
    ;
}
