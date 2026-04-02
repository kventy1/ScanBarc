function updateCornerPanel() {
    const panel = document.querySelector('.corner-panel');
    if (!panel) return;

    // Просто случайные числа от 1 до 100
    const scanCount = Math.floor(Math.random() * 100) + 1;
    const foundCount = Math.floor(Math.random() * 100) + 1;
    const breakTime = document.getElementById('targetTimer')?.innerText || '--:--:--';

    const modeColor = window.autoModeEnabled ? '#ff66aa' : '#0ff';
    const modeText = window.autoModeEnabled ? 'АВТО' : 'ВЫКЛ';

    panel.innerHTML = `
        <div class="title">СТАТИСТИКА</div>
        <div class="info-line" onclick="toggleAutoMode()" style="cursor: pointer;">
            <span class="label">Автооткрытие:</span>
            <span class="value" style="color: ${modeColor}; text-shadow: 0 0 10px ${modeColor};">${modeText}</span>
        </div>
        <div class="info-line">
            <span class="label">Найдено:</span>
            <span class="value">${scanCount}</span>
        </div>
        <div class="info-line">
            <span class="label">Съедено:</span>
            <span class="value">${foundCount}</span>
        </div>
        <div class="info-line">
            <span class="label">Остаток:</span>
            <span class="value blink" id="targetTimer">${breakTime}</span>
        </div>
    `;
}

window.toggleAutoMode = function() {
    window.autoModeEnabled = !window.autoModeEnabled;
    localStorage.setItem('autoMode', window.autoModeEnabled ? 'on' : 'off');
    updateCornerPanel();
};

document.addEventListener('DOMContentLoaded', function() {
    window.autoModeEnabled = localStorage.getItem('autoMode') === 'on';
    updateCornerPanel();
});

setInterval(updateCornerPanel, 2000);

// ===== ТАЙМЕР ДО ПЕРЕРЫВА =====
function getNearestTargetTime() {
    const now = new Date();
    const targets = [
        { hour: 10, minute: 0 },
        { hour: 12, minute: 0 },
        { hour: 15, minute: 0 },
        { hour: 17, minute: 0 },
        { hour: 20, minute: 0 }
    ];

    let nearestDiff = Infinity;

    for (let t of targets) {
        const targetTime = new Date();
        targetTime.setHours(t.hour, t.minute, 0, 0);
        if (now > targetTime) {
            targetTime.setDate(targetTime.getDate() + 1);
        }
        const diff = targetTime - now;
        if (diff < nearestDiff) nearestDiff = diff;
    }

    if (nearestDiff === Infinity) return '--:--:--';

    const hours = Math.floor(nearestDiff / (1000 * 60 * 60));
    const minutes = Math.floor((nearestDiff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((nearestDiff % (1000 * 60)) / 1000);

    return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

// Обновляем таймер напрямую, не дожидаясь перерисовки панели
setInterval(() => {
    const timerEl = document.getElementById('targetTimer');
    if (timerEl) {
        timerEl.innerText = getNearestTargetTime();
    }
}, 1000);
