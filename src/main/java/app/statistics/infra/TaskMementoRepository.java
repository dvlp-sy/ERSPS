package app.statistics.infra;

import app.statistics.model.TaskMemento;
import app.statistics.model.enums.TaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskMemento 엔티티에 대한 JPA 리포지토리 인터페이스
 * @implSpec Spring Data JPA의 JpaRepository를 확장하여 기본 CRUD 및 쿼리 메서드를 제공한다.
 * @see TaskMemento
 */
@Repository
public interface TaskMementoRepository extends JpaRepository<TaskMemento, Long> {
    List<TaskMemento> findAllByTaskTypeAndFinishedAtAfter(TaskType taskType, LocalDateTime finishedAt);
}
