package app.statistics.presentation;

import app.statistics.infra.TaskMementoRepository;
import app.statistics.model.TaskMemento;
import app.statistics.model.enums.TaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통계 관련 API 요청을 처리하는 컨트롤러 클래스
 * <p>
 *     작업 유형별로 최근 통계 작업 결과를 조회하기 위한 API를 제공한다.
 *     호출 결과는 대시보드 시스템에 활용된다.
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/statistics")
public class StatisticsApiController {

    private final TaskMementoRepository taskMementoRepository;

    @GetMapping
    public ResponseEntity<List<TaskMemento>> getTasks(TaskType taskType, LocalDateTime finishedAt) {
        List<TaskMemento> taskMementoList = taskMementoRepository
                .findAllByTaskTypeAndFinishedAtAfter(taskType, finishedAt);
        return ResponseEntity.ok(taskMementoList);
    }
}
