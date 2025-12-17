<div align="center">
  <h1>대규모 트래픽 모니터링 및 악성 공격 탐지를 위한<br>ExaLogLog 기반 실시간 스트림 처리 시스템</h1>
  변시윤<br>
  경희대학교 컴퓨터공학과<br>
  siyun22@khu.ac.kr
  <h3>ExaLogLog-based Real-time Stream Processing System<br>for Large-scale Traffic Monitoring and Malicious Attack Detection</h3><br>
  Siyun Byun<br>
  School of Computer Science and Engineering, Kyung Hee University
</div>
<br>

***

<div align="center">
  현대의 웹 서비스는 초당 수백만 건 이상의 이벤트가 발생하는 대규모 트래픽 환경에 노출되어 있다.
  특히 스마트 팩토리, 자율주행자동차, 대규모 전자상거래 플랫폼 등 미션 크리티컬 도메인에서 서비스 장애가 발생하는 경우 서비스 중단을 넘어 막대한 재산 피해와 심각한 안전 문제로 직결될 수 있다.
  이에 따라 시스템의 가용성을 위협하는 악성 트래픽을 즉각적으로 감지하고 차단할 수 있는 보안 기술에 대한 요구가 급증하고 있다.
  그러나 트래픽의 빈도수만을 고려하는 기존의 보안 모니터링 시스템은 응용 계층에서 발생하는 다양한 요청을 효율적으로 필터링하기 어렵다.
  본 논문에서는 제한된 시스템 리소스 내에서 메모리 오버헤드를 획기적으로 줄이면서 응용 계층에서의 악성 트래픽을 감지할 수 있는 실시간 스트림 처리 시스템을 제안한다.
  실험 결과, 확률적 자료구조인 ExaLogLog 알고리즘을 병렬 처리 아키텍처와 결합하여 활성 사용자의 도메인과 고유 IP 개수와 같은 사용자 데이터를 낮은 메모리 오버헤드로 정확하게 추정함을 확인하였다.
</div>
<br>

***