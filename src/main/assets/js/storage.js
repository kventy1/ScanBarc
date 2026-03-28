// ===== ЕДИНОЕ ХРАНИЛИЩЕ =====

// Сохранить результат сканирования
function saveScan(barcode, filePath, success) {
    try {
        const scans = JSON.parse(localStorage.getItem('all_scans') || '[]');

        scans.unshift({
            id: Date.now() + '_' + Math.random().toString(36).substr(2, 8),
            barcode: barcode,
            timestamp: new Date().toLocaleString('ru-RU'),
            filePath: filePath || '',
            success: success,
            agregat: '',
            shtukov: '',
            color: '',
            model: '',
            size: ''
        });

        // Храним последние 1000 записей
        if (scans.length > 1000) scans.length = 1000;

        localStorage.setItem('all_scans', JSON.stringify(scans));

        // Обновить уголок
        updateCornerStats();

        // Уведомляем все фреймы
        notifyFrames('refresh');
    } catch (e) {}
}

// Обновить данные записи
function updateScan(id, fields) {
    try {
        const scans = JSON.parse(localStorage.getItem('all_scans') || '[]');
        const index = scans.findIndex(s => s.id === id);
        if (index !== -1) {
            scans[index] = { ...scans[index], ...fields };
            localStorage.setItem('all_scans', JSON.stringify(scans));
            updateCornerStats();
            notifyFrames('refresh');
        }
    } catch (e) {}
}

// Удалить запись
window.deleteScan = function(id) {
    let scans = JSON.parse(localStorage.getItem('all_scans') || '[]');
    scans = scans.filter(scan => scan.id != id);
    localStorage.setItem('all_scans', JSON.stringify(scans));
    if (window.updateCornerStats) window.updateCornerStats();
};

// Получить все записи
function getAllScans() {
    try {
        return JSON.parse(localStorage.getItem('all_scans') || '[]');
    } catch {
        return [];
    }
}

// ===== СТАТИСТИКА В УГОЛКЕ =====
function updateCornerStats() {
    const scans = getAllScans();
    const total = scans.length;
    const found = scans.filter(s => s.success).length;

    // Обновляем элементы в уголке
    const scanCountEl = document.getElementById('scanCount');
    const foundCountEl = document.getElementById('foundCount');

    if (scanCountEl) scanCountEl.innerText = total;
    if (foundCountEl) foundCountEl.innerText = found;
}

// Уведомить фреймы
function notifyFrames(type) {
    const frames = ['historyFrame', 'statsFrame'];
    frames.forEach(id => {
        const frame = document.getElementById(id);
        if (frame && frame.contentWindow) {
            frame.contentWindow.postMessage({ type: type }, '*');
        }
    });
}

// Автообновление при изменении из другого окна
window.addEventListener('storage', (e) => {
    if (e.key === 'all_scans') {
        updateCornerStats();
    }
});

// Загрузить при старте
document.addEventListener('DOMContentLoaded', updateCornerStats);

// Доступ из консоли для отладки
window.storage = { saveScan, updateScan, deleteScan, getAllScans };