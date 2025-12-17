const API_BASE_URL = 'http://localhost:8080/statistics';

// 데이터 저장용 전역 변수
const chartData = {
    labels: [],
    activeUsers: [],
    uniqueIPs: [],
    uniqueDomain: []
};

// 최대 히스토리 포인트 수
const MAX_HISTORY_POINTS = 11;

// ExaLogLog 태스크 유형
const TASK_TYPES = {
    ACCOUNT: 'DISTINCT_ACCOUNT_ID_COUNT',
    IP: 'DISTINCT_IP_ADDRESS_COUNT',
    EMAIL: 'DISTINCT_ACCOUNT_EMAIL_DOMAIN_COUNT'
};

/**
 * KST를 기준으로 'YYYY-MM-DDTHH:mm:ss' 형식의 문자열을 생성하는 메서드
 * @param {Date} date - 변환할 Date 객체
 * @returns {string} KST 기준의 로컬 날짜 시간 문자열
 */
function toKstLocalDateTimeString(date) {
    const formatter = new Intl.DateTimeFormat('en-US', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', second: '2-digit',
        hour12: false, timeZone: 'Asia/Seoul'
    });

    const parts = formatter.formatToParts(date);
    const getPart = (type) => parts.find(p => p.type === type).value;

    return `${getPart('year')}-${getPart('month')}-${getPart('day')}T${getPart('hour')}:${getPart('minute')}:${getPart('second')}`;
}

/**
 * 최근 히스토리 데이터를 API에서 가져오는 메서드
 * @param {string} taskType - ExaLogLog 태스크 유형
 * @returns {Promise<Array>} 히스토리 Memento 배열
 */
async function fetchHistoricalMementos(taskType) {
    const now = new Date();

    const elevenMinutesAgo = new Date(now.getTime() - MAX_HISTORY_POINTS * 60000);
    const finishedAtParam = toKstLocalDateTimeString(elevenMinutesAgo);

    const url = `${API_BASE_URL}?taskType=${taskType}&finishedAt=${finishedAtParam}`;

    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

        return await response.json() || [];

    } catch (error) {
        console.error(`[Error fetching history for ${taskType}]:`, error);
        return [];
    }
}

/**
 * 차트 초기화 메서드
 * @returns {{activeUsersChart: Chart, domainsChart: Chart, ipsChart: Chart}}
 */
function initializeCharts() {
    const commonChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        layout: {
            padding: 15
        },
        scales: {
            y: { beginAtZero: true }
        }
    };

    // 접속자 수 그래프
    const activeUsersChart = new Chart(document.getElementById('activeUsersChart'), {
        type: 'line', data: { labels: chartData.labels, datasets: [{ label: '접속자 수 (명)', data: chartData.activeUsers, borderColor: 'rgb(75, 192, 192)', tension: 0.1 }] }, options: commonChartOptions
    });
    // 이메일 도메인 수 그래프
    const domainsChart = new Chart(document.getElementById('domainsChart'), {
        type: 'line', data: { labels: chartData.labels, datasets: [{ label: '접속자 이메일 도메인 수 (개)', data: chartData.uniqueDomain, backgroundColor: 'rgb(255, 99, 132)' }] }, options: commonChartOptions
    });
    // 고유 IP 수 그래프
    const ipsChart = new Chart(document.getElementById('ipsChart'), {
        type: 'line', data: { labels: chartData.labels, datasets: [{ label: '접속자 고유 IP 수 (개)', data: chartData.uniqueIPs, borderColor: 'rgb(54, 162, 235)', tension: 0.2 }] }, options: commonChartOptions
    });

    return { activeUsersChart, domainsChart: domainsChart, ipsChart };
}

/**
 * 차트 업데이트 메서드
 */
async function fetchDataAndUpdateCharts(charts) {

    // 히스토리 데이터 병렬 호출
    const [accountHistory, ipHistory, dnsHistory] = await Promise.all([
        fetchHistoricalMementos(TASK_TYPES.ACCOUNT),
        fetchHistoricalMementos(TASK_TYPES.IP),
        fetchHistoricalMementos(TASK_TYPES.EMAIL)
    ]);

    // 평균 처리 시간 계산 업데이트
    const updateAvgTime = (elementId, history) => {
        if (!history || history.length === 0) {
            document.getElementById(elementId).textContent = '(Avg Time: - ms)';
            return;
        }

        const validData = history.filter(m => m.processingTimeMs !== undefined && m.processingTimeMs !== null);

        if (validData.length === 0) {
            document.getElementById(elementId).textContent = '(Avg Time: 0 ms)';
            return;
        }

        const totalTime = validData.reduce((sum, m) => sum + m.processingTimeMs, 0);
        const avgTime = (totalTime / validData.length).toFixed(1); // 소수점 1자리까지

        document.getElementById(elementId).textContent = `(Avg Time: ${avgTime} ms)`;
    };
    updateAvgTime('account-time', accountHistory);
    updateAvgTime('ip-time', ipHistory);
    updateAvgTime('dns-time', dnsHistory);

    // 데이터 배열 초기화
    chartData.labels.length = 0;
    chartData.activeUsers.length = 0;
    chartData.uniqueIPs.length = 0;
    chartData.uniqueDomain.length = 0;

    // 모든 Memento에서 고유한 시간 목록 생성
    const allMementos = [...accountHistory, ...ipHistory, ...dnsHistory];
    const uniqueTimestamps = [...new Set(allMementos.map(m => m.finishedAt.substring(0, 16)))]
        .sort();

    // 차트 데이터 생성
    const maxLen = uniqueTimestamps.length;
    for (let i = 0; i < maxLen; i++) {
        const timestampMinuteStr = uniqueTimestamps[i]; // YYYY-MM-DDTHH:MM
        const targetTimeMs = new Date(timestampMinuteStr + ':00Z').getTime();

        const findClosestMemento = (history) => {
            const memento = history.find(m => {
                const mementoTimeMs = new Date(m.finishedAt + 'Z').getTime();
                // 59초 이내의 차이는 동일한 1분 윈도우로 간주하여 데이터를 매칭
                return Math.abs(mementoTimeMs - targetTimeMs) < 60000;
            }) || {distinctCount: 0};
            return memento;
        };

        const account = findClosestMemento(accountHistory);
        const ip = findClosestMemento(ipHistory);
        const dns = findClosestMemento(dnsHistory);

        const displayTime = new Date(timestampMinuteStr).toLocaleTimeString('ko-KR', {hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Seoul'});

        const getCount = (memento) => Math.round(memento.distinctCount);

        const active = getCount(account);
        const uniqueIps = getCount(ip);
        const uniqueDns = getCount(dns);

        chartData.labels.push(displayTime);
        chartData.activeUsers.push(active);
        chartData.uniqueIPs.push(uniqueIps);
        chartData.uniqueDomain.push(uniqueDns);
    }

    // 실시간 도표 업데이트
    const lastIndex = chartData.labels.length - 1;

    if (lastIndex >= 0) {
        const currentTime = chartData.labels[lastIndex];

        const latestActiveUsers = chartData.activeUsers[lastIndex];
        const latestUniqueIPs = chartData.uniqueIPs[lastIndex];
        const latestUniqueDNS = chartData.uniqueDomain[lastIndex];

        document.getElementById('active-users-row').children[1].textContent = latestActiveUsers + ' 명';
        document.getElementById('active-users-row').children[2].textContent = currentTime;

        document.getElementById('unique-ips-row').children[1].textContent = latestUniqueIPs + ' 개';
        document.getElementById('unique-ips-row').children[2].textContent = currentTime;

        document.getElementById('unique-dns-row').children[1].textContent = latestUniqueDNS + ' 개';
        document.getElementById('unique-dns-row').children[2].textContent = currentTime;
    }

    // 그래프 렌더링
    charts.activeUsersChart.update();
    charts.domainsChart.update();
    charts.ipsChart.update();
}

/**
 * 문서 로드 시 초기화 및 주기적 업데이트 설정
 */
document.addEventListener('DOMContentLoaded', () => {
    const charts = initializeCharts();
    const UPDATE_INTERVAL_MS = 60000; // 1분마다 업데이트

    // 즉시 실행
    fetchDataAndUpdateCharts(charts);

    // 1분마다 주기적으로 업데이트
    setInterval(() => fetchDataAndUpdateCharts(charts), UPDATE_INTERVAL_MS);
});