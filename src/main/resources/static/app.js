const API_BASE_URL = 'http://localhost:8080/statistics';

const chartData = {
    labels: [],
    activeUsers: [],
    uniqueIPs: [],
    uniqueDomain: []
};

const MAX_HISTORY_POINTS = 1;

const TASK_TYPES = {
    ACCOUNT: 'DISTINCT_ACCOUNT_ID_COUNT',
    IP: 'DISTINCT_IP_ADDRESS_COUNT',
    EMAIL: 'DISTINCT_ACCOUNT_EMAIL_DOMAIN_COUNT'
};

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

async function fetchHistoricalMementos(taskType) {
    const now = new Date();
    const minutesAgo = new Date(now.getTime() - MAX_HISTORY_POINTS * 60000);
    const finishedAtParam = toKstLocalDateTimeString(minutesAgo);

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

function initializeCharts() {
    const commonChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        layout: { padding: 15 },
        scales: { y: { beginAtZero: true } }
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

    return { activeUsersChart, domainsChart, ipsChart };
}

async function fetchDataAndUpdateCharts(charts) {
    const [accountHistory, ipHistory, dnsHistory] = await Promise.all([
        fetchHistoricalMementos(TASK_TYPES.ACCOUNT),
        fetchHistoricalMementos(TASK_TYPES.IP),
        fetchHistoricalMementos(TASK_TYPES.EMAIL)
    ]);

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
        const avgTime = (totalTime / validData.length).toFixed(1);
        document.getElementById(elementId).textContent = `(Avg Time: ${avgTime} ms)`;
    };
    updateAvgTime('account-time', accountHistory);
    updateAvgTime('ip-time', ipHistory);
    updateAvgTime('dns-time', dnsHistory);

    chartData.labels.length = 0;
    chartData.activeUsers.length = 0;
    chartData.uniqueIPs.length = 0;
    chartData.uniqueDomain.length = 0;

    const allMementos = [...accountHistory, ...ipHistory, ...dnsHistory];
    const uniqueTimestamps = [...new Set(allMementos.map(m => m.finishedAt.substring(0, 19)))]
        .sort();

    const maxLen = uniqueTimestamps.length;
    for (let i = 0; i < maxLen; i++) {
        const timestampSecStr = uniqueTimestamps[i];
        const targetTimeMs = new Date(timestampSecStr).getTime();

        const findClosestMemento = (history) => {
            return history.find(m => {
                const mementoTimeMs = new Date(m.finishedAt).getTime();
                return Math.abs(mementoTimeMs - targetTimeMs) < 1000;
            }) || {distinctCount: 0};
        };

        const account = findClosestMemento(accountHistory);
        const ip = findClosestMemento(ipHistory);
        const dns = findClosestMemento(dnsHistory);

        const displayTime = new Date(timestampSecStr).toLocaleTimeString('ko-KR', {
            hour: '2-digit', minute: '2-digit', second: '2-digit', timeZone: 'Asia/Seoul'
        });

        const getCount = (memento) => Math.round(memento.distinctCount);

        chartData.labels.push(displayTime);
        chartData.activeUsers.push(getCount(account));
        chartData.uniqueIPs.push(getCount(ip));
        chartData.uniqueDomain.push(getCount(dns));
    }

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

    charts.activeUsersChart.update();
    charts.domainsChart.update();
    charts.ipsChart.update();
}

document.addEventListener('DOMContentLoaded', () => {
    const charts = initializeCharts();
    const UPDATE_INTERVAL_MS = 5000;

    fetchDataAndUpdateCharts(charts);
    setInterval(() => fetchDataAndUpdateCharts(charts), UPDATE_INTERVAL_MS);
});