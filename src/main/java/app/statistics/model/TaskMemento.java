package app.statistics.model;

import app.statistics.model.enums.TaskType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ExaLogLog 태스크의 상태를 저장하는 Memento 엔티티 클래스
 * <p>{@link app.statistics.application.ELLStreamProcessor ELLStreamProcessor}의 처리 결과를 저장하는 데 사용된다.</p>
 * @implSpec JPA 엔티티로 매핑되며, 태스크 유형, 완료 시간, 독립적인 개수 및 처리 시간을 속성으로 가진다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskMemento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false, unique = true)
    private Long id;

    @Column
    @Enumerated(EnumType.STRING)
    private TaskType taskType;

    @Column
    private LocalDateTime finishedAt;

    @Column
    private double distinctCount;

    @Column
    private long processingTimeMs;

    private TaskMemento(TaskType taskType, LocalDateTime finishedAt, double distinctCount, long processingTimeMs) {
        this.taskType = taskType;
        this.finishedAt = finishedAt;
        this.distinctCount = distinctCount;
        this.processingTimeMs = processingTimeMs;
    }

    public static TaskMemento of(TaskType taskType, LocalDateTime finishedAt, double distinctCount, long processingTimeMs) {
        return new TaskMemento(taskType, finishedAt, distinctCount, processingTimeMs);
    }

    @Override
    public String toString() {
        return String.format("AccountTaskMemento(finishedAt(%s), distinctCount=(%.6f))", finishedAt, distinctCount);
    }
}
