// ── State ──────────────────────────────────────────
let currentState = 'IDLE';
let metricsHistory = [];
const MAX_HISTORY = 60;
let pollTimer = null;

// ── Dot Animation Engine ──────────────────────────
const SVG_NS = 'http://www.w3.org/2000/svg';
const dots = [];          // active dot objects
let lastMetrics = { inflight: 0, queueDepth: 0, busyRejectCount: 0 };
let prevBusyCount = 0;
let prevTotalRequests = 0;
let requestRate = 0;      // requests per second (estimated)

// Paths: [startX, startY, endX, endY]
const PATHS = {
    httpIn:   { x1: 185, y1: 100, x2: 315, y2: 100 },   // clients → gateway
    tcpOut:   { x1: 585, y1: 100, x2: 715, y2: 100 },   // gateway → card-sim
    tcpBack:  { x1: 715, y1: 130, x2: 585, y2: 130 },   // card-sim → gateway
    httpBack: { x1: 315, y1: 130, x2: 185, y2: 130 },   // gateway → clients
};

function spawnDot(path, color, radius) {
    const circle = document.createElementNS(SVG_NS, 'circle');
    circle.setAttribute('r', radius || 3.5);
    circle.setAttribute('fill', color);
    circle.setAttribute('opacity', '0.85');
    document.getElementById('dot-layer').appendChild(circle);
    dots.push({
        el: circle,
        x: path.x1, y: path.y1,
        dx: path.x2 - path.x1,
        dy: path.y2 - path.y1,
        progress: 0,
        speed: 0.008 + Math.random() * 0.006,  // ~120-180 frames to cross
    });
}

function tickDots() {
    for (let i = dots.length - 1; i >= 0; i--) {
        const d = dots[i];
        d.progress += d.speed;
        if (d.progress >= 1) {
            d.el.remove();
            dots.splice(i, 1);
            continue;
        }
        const cx = d.x + d.dx * d.progress;
        const cy = d.y + d.dy * d.progress;
        d.el.setAttribute('cx', cx);
        d.el.setAttribute('cy', cy);
        // Fade out near the end
        if (d.progress > 0.85) {
            d.el.setAttribute('opacity', String((1 - d.progress) / 0.15 * 0.85));
        }
    }
}

function spawnDotsForMetrics() {
    const inflight = lastMetrics.inflight || 0;
    const busy = lastMetrics.busyRejectCount || 0;

    // Determine spawn rate from scenario state
    const isActive = currentState === 'RUNNING' || inflight > 0;
    if (!isActive) return;

    // Request dots: scale with inflight (1 dot per ~50 inflight, min 1 when active)
    const reqDotCount = Math.max(1, Math.min(8, Math.ceil(inflight / 50)));
    for (let i = 0; i < reqDotCount; i++) {
        if (Math.random() < 0.3) spawnDot(PATHS.httpIn, '#58a6ff');
    }

    // TCP forward dots (proportional to inflight)
    const tcpDots = Math.max(1, Math.min(6, Math.ceil(inflight / 80)));
    for (let i = 0; i < tcpDots; i++) {
        if (Math.random() < 0.25) spawnDot(PATHS.tcpOut, '#58a6ff', 3);
    }

    // Response dots (slightly fewer than requests)
    const resDots = Math.max(1, Math.min(5, Math.ceil(inflight / 100)));
    for (let i = 0; i < resDots; i++) {
        if (Math.random() < 0.25) spawnDot(PATHS.tcpBack, '#7ee787', 3);
    }
    for (let i = 0; i < resDots; i++) {
        if (Math.random() < 0.25) spawnDot(PATHS.httpBack, '#7ee787', 3);
    }

    // BUSY reject dots (red, spawn when busy count increases)
    const busyDelta = busy - prevBusyCount;
    if (busyDelta > 0) {
        const busyDots = Math.min(5, Math.ceil(busyDelta / 2));
        for (let i = 0; i < busyDots; i++) {
            spawnDot(PATHS.httpBack, '#f85149', 4.5);
        }
    }
    prevBusyCount = busy;
}

let frameCount = 0;
function animationLoop() {
    tickDots();
    frameCount++;
    // Spawn new dots every ~6 frames (~100ms at 60fps)
    if (frameCount % 6 === 0) {
        spawnDotsForMetrics();
    }
    requestAnimationFrame(animationLoop);
}

// ── Init ──────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
    startPolling();
    requestAnimationFrame(animationLoop);
});

// ── Scenario Execution ────────────────────────────
async function runScenario(type) {
    try {
        const res = await fetch(`/api/scenarios/${type}`, { method: 'POST' });
        if (res.status === 409) {
            alert('A scenario is already running. Wait for it to complete.');
            return;
        }
        if (!res.ok) {
            const body = await res.json();
            alert(body.error || 'Failed to start scenario');
            return;
        }
        setButtonsDisabled(true);
        highlightCard(type);
    } catch (e) {
        console.error('Failed to start scenario:', e);
    }
}

// ── Polling ───────────────────────────────────────
function startPolling() {
    poll();
    pollTimer = setInterval(poll, 1500);
}

async function poll() {
    try {
        const [statusRes, metricsRes] = await Promise.all([
            fetch('/api/status').then(r => r.json()),
            fetch('/api/metrics').then(r => r.json())
        ]);

        updateStatusBar(statusRes);
        updateDiagram(metricsRes);
        updateMetricCards(metricsRes);
        pushHistory(metricsRes);
        drawChart();

        // Detect transition to COMPLETED
        if (statusRes.state === 'COMPLETED' && currentState === 'RUNNING') {
            showResults(statusRes);
            setButtonsDisabled(false);
            clearHighlight();
        }

        currentState = statusRes.state;
    } catch (e) {
        // Services may not be ready yet
    }
}

// ── Status Bar ────────────────────────────────────
function updateStatusBar(status) {
    const bar = document.getElementById('status-bar');
    const icon = document.getElementById('status-icon');
    const text = document.getElementById('status-text');

    bar.className = 'status-bar';

    switch (status.state) {
        case 'RUNNING':
            bar.classList.add('status-running');
            icon.textContent = '●';
            text.textContent = `${status.scenario} 실행 중... ${status.elapsedSec}초 경과`;
            if (status.snapshot && status.snapshot.totalRequests) {
                text.textContent += ` | ${status.snapshot.totalRequests.toLocaleString()}건 처리`;
            }
            break;
        case 'COMPLETED':
            bar.classList.add('status-completed');
            icon.textContent = '✓';
            text.textContent = `완료: ${status.scenario}`;
            break;
        default:
            bar.classList.add('status-idle');
            icon.textContent = '●';
            text.textContent = '대기 중 — 시나리오를 선택하세요';
    }
}

// ── SVG Diagram Updates ───────────────────────────
function updateDiagram(metrics) {
    const inflight = metrics.inflight || 0;
    const queue = metrics.queueDepth || 0;

    // Update shared metrics for dot engine
    lastMetrics = metrics;

    // Split inflight roughly between 2 sessions
    const s0 = Math.round(inflight / 2);
    const s1 = Math.round(inflight - s0);

    setText('session0-count', s0);
    setText('session1-count', s1);
    setText('queue-depth', Math.round(queue));

    // Color coding
    setColor('session0-count', getLevel(s0, 500, 1500));
    setColor('session1-count', getLevel(s1, 500, 1500));
    setColor('queue-depth', getLevel(queue, 100, 1000));
}

// ── Metric Cards ──────────────────────────────────
function updateMetricCards(metrics) {
    setMetric('live-inflight', Math.round(metrics.inflight || 0), 500, 1500);
    setMetric('live-busy', Math.round(metrics.busyRejectCount || 0), 1, 10);
    setMetric('live-timeout', Math.round(metrics.timeoutCount || 0), 1, 10);
}

function setMetric(id, value, warnThreshold, dangerThreshold) {
    const el = document.getElementById(id);
    el.textContent = value;
    el.className = 'metric-value-large';
    if (value >= dangerThreshold) el.classList.add('danger');
    else if (value >= warnThreshold) el.classList.add('warning');
}

// ── Latency Chart (Canvas) ────────────────────────
function pushHistory(metrics) {
    metricsHistory.push({
        t: Date.now(),
        inflight: metrics.inflight || 0,
        busyCount: metrics.busyRejectCount || 0,
        busy: metrics.busyRejectCount || 0,
        timeout: metrics.timeoutCount || 0
    });
    if (metricsHistory.length > MAX_HISTORY) {
        metricsHistory.shift();
    }
}

function drawChart() {
    const canvas = document.getElementById('latency-chart');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const w = canvas.width;
    const h = canvas.height;
    const padding = { top: 20, right: 20, bottom: 30, left: 50 };

    ctx.clearRect(0, 0, w, h);

    if (metricsHistory.length < 2) {
        ctx.fillStyle = '#8b949e';
        ctx.font = '14px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('Waiting for metrics...', w / 2, h / 2);
        return;
    }

    const plotW = w - padding.left - padding.right;
    const plotH = h - padding.top - padding.bottom;

    // Find max values
    const maxInflight = Math.max(10, ...metricsHistory.map(m => m.inflight));
    const maxBusy = Math.max(10, ...metricsHistory.map(m => m.busyCount));
    const maxY = Math.max(maxInflight, maxBusy);

    // Grid
    ctx.strokeStyle = '#21262d';
    ctx.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
        const y = padding.top + (plotH / 4) * i;
        ctx.beginPath();
        ctx.moveTo(padding.left, y);
        ctx.lineTo(w - padding.right, y);
        ctx.stroke();

        ctx.fillStyle = '#8b949e';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'right';
        ctx.fillText(Math.round(maxY - (maxY / 4) * i), padding.left - 8, y + 4);
    }

    // Draw lines
    drawLine(ctx, metricsHistory.map(m => m.inflight), maxY, plotW, plotH, padding, '#58a6ff');
    drawLine(ctx, metricsHistory.map(m => m.busyCount), maxY, plotW, plotH, padding, '#f0883e');

    // Legend
    ctx.font = '11px sans-serif';
    ctx.fillStyle = '#58a6ff';
    ctx.textAlign = 'left';
    ctx.fillText('● Inflight', padding.left + 10, h - 8);
    ctx.fillStyle = '#f0883e';
    ctx.fillText('● BUSY Rejects', padding.left + 100, h - 8);
}

function drawLine(ctx, values, maxY, plotW, plotH, padding, color) {
    if (values.length < 2) return;
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.beginPath();
    values.forEach((v, i) => {
        const x = padding.left + (plotW / (values.length - 1)) * i;
        const y = padding.top + plotH - (v / maxY) * plotH;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
    });
    ctx.stroke();
}

// ── Results Display ───────────────────────────────
function showResults(status) {
    const snap = status.snapshot;
    if (!snap || !snap.totalRequests) return;

    document.getElementById('result-scenario').textContent = snap.scenario || status.scenario;
    document.getElementById('result-total').textContent = `Total: ${snap.totalRequests.toLocaleString()} requests`;
    document.getElementById('result-p50').textContent = `${snap.p50Ms}ms`;
    document.getElementById('result-p95').textContent = `${snap.p95Ms}ms`;
    document.getElementById('result-p99').textContent = `${snap.p99Ms}ms`;
    document.getElementById('result-max').textContent = `${snap.maxMs}ms`;

    // Status distribution bar
    const bar = document.getElementById('results-bar');
    bar.innerHTML = '';
    const counts = snap.statusCounts || {};
    const total = snap.totalRequests || 1;
    const segments = [
        { key: 'APPROVED', cls: 'bar-approved' },
        { key: 'DECLINED', cls: 'bar-declined' },
        { key: 'BUSY', cls: 'bar-busy' },
        { key: 'TIMEOUT', cls: 'bar-timeout' },
        { key: 'ERROR', cls: 'bar-error' }
    ];
    segments.forEach(({ key, cls }) => {
        const count = counts[key] || 0;
        if (count === 0) return;
        const pct = (count / total * 100);
        const div = document.createElement('div');
        div.className = `bar-segment ${cls}`;
        div.style.width = `${Math.max(pct, 3)}%`;
        div.textContent = `${key} ${pct.toFixed(1)}%`;
        bar.appendChild(div);
    });

    document.getElementById('results-section').style.display = 'block';
    document.getElementById('results-section').scrollIntoView({ behavior: 'smooth' });
}

// ── Helpers ───────────────────────────────────────
function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

function setColor(id, level) {
    const el = document.getElementById(id);
    if (!el) return;
    el.setAttribute('fill', level === 'danger' ? '#f85149' : level === 'warning' ? '#f0883e' : '#58a6ff');
}

function getLevel(value, warn, danger) {
    if (value >= danger) return 'danger';
    if (value >= warn) return 'warning';
    return 'normal';
}

function setButtonsDisabled(disabled) {
    document.querySelectorAll('.btn-run').forEach(btn => btn.disabled = disabled);
}

function highlightCard(type) {
    clearHighlight();
    const card = document.getElementById('card-' + type.toLowerCase());
    if (card) card.classList.add('active');
}

function clearHighlight() {
    document.querySelectorAll('.scenario-card').forEach(c => c.classList.remove('active'));
}
