// ===== СТАТИСТИКА =====

function loadStats() {
    const scans = JSON.parse(localStorage.getItem('all_scans') || '[]');

    // Общая статистика
    const totalScans = scans.length;
    const totalFound = scans.filter(s => s.success).length;

    document.getElementById('totalScans').innerText = totalScans;
    document.getElementById('totalFound').innerText = totalFound;

    // Группировка по агрегатам
    const stats = {};
    scans.forEach(s => {
        if (s.agregat && s.agregat.trim()) {
            const agregat = s.agregat.trim();
            const shtukov = parseInt(s.shtukov) || 0;
            stats[agregat] = (stats[agregat] || 0) + shtukov;
        }
    });

    // Отрисовка
    const list = document.getElementById('statsList');
    if (Object.keys(stats).length === 0) {
        list.innerHTML = '<div class="empty">Нет данных по агрегатам</div>';
        return;
    }

    const sorted = Object.entries(stats).sort((a, b) => b[1] - a[1]);

    let html = '';
    sorted.forEach(([name, total]) => {
        html += `
            <div class="stat-item">
                <span class="stat-name">${name}</span>
                <span class="stat-value">${total} шт</span>
            </div>
        `;
    });
    list.innerHTML = html;
}

// Обновление при изменениях
window.addEventListener('storage', loadStats);
window.addEventListener('message', (e) => {
    if (e.data?.type === 'refresh') loadStats();
});

// Запуск
document.addEventListener('DOMContentLoaded', loadStats);

// ===== ЭКСПОРТ СТАТИСТИКИ ПО АГРЕГАТУ =====

function addExportButton() {
    const detailHeader = document.querySelector('.detail-header');
    if (detailHeader && !document.getElementById('exportBtn')) {
        const exportBtn = document.createElement('button');
        exportBtn.id = 'exportBtn';
        exportBtn.innerHTML = '📄 Экспорт';
        exportBtn.className = 'export-btn';
        exportBtn.onclick = exportAggregateStats;
        detailHeader.appendChild(exportBtn);
    }
}

function exportAggregateStats() {
    const agregat = currentAgregat;
    const scans = currentScans.filter(s => s.agregat && s.agregat.trim() === agregat);

    if (scans.length === 0) {
        alert('Нет данных для экспорта');
        return;
    }

    // Группируем и формируем файл
    const groupedData = groupScans(scans);
    const content = formatStatsContent(agregat, groupedData);

    // Сохраняем через Android
    if (typeof AndroidScanner !== 'undefined') {
        const filename = `${agregat}_${getFormattedDate()}.txt`;
        AndroidScanner.saveTextFile(filename, content);
    } else {
        // Fallback для браузера
        downloadFile(content, `${agregat}.txt`);
    }
}

function groupScans(scans) {
    const groups = {};

    scans.forEach(scan => {
        const key = `${scan.size || ''}_${scan.barcode}_${scan.model}_${scan.color}`;
        if (!groups[key]) {
            groups[key] = {
                size: scan.size || '',
                barcode: scan.barcode || '',
                model: scan.model || '',
                color: scan.color || '',
                shtukov: 0,
                count: 0
            };
        }
        groups[key].shtukov += parseInt(scan.shtukov) || 0;
        groups[key].count++;
    });

    return Object.values(groups);
}

function formatStatsContent(agregat, data) {
    let content = '';

    // СОРТИРОВКА ТОЛЬКО ПО МОДЕЛИ (без размера)
    data.sort((a, b) => {
        const modelA = a.model || '';
        const modelB = b.model || '';
        return modelA.localeCompare(modelB);
    });

    // Формируем строки данных с группировкой по модели
    let lastModel = '';

    data.forEach(item => {
        const currentModel = item.model || '';

        // Добавляем заголовок модели если она изменилась
        if (lastModel !== currentModel) {
            if (lastModel !== '') content += '\n';
            content += `=== ${currentModel || 'БЕЗ МОДЕЛИ'} ===\n`;
            lastModel = currentModel;
        }

        const sizePad = item.size.padEnd(10, ' ');
        const barcodePad = item.barcode.padEnd(15, ' ');
        const shtukovPad = item.shtukov.toString().padStart(3, ' ') + ' ';
        const modelPad = (item.model || '').padEnd(10, ' ');
        const colorPad = (item.color || '').padEnd(10, ' ');

        content += `${sizePad}${barcodePad}${shtukovPad}${modelPad}${colorPad}\n`;
    });

    // Добавляем итоги
    const totalItems = data.length;
    const totalShtukov = data.reduce((sum, item) => sum + item.shtukov, 0);

    content += `\nВсего позиций: ${totalItems}\n`;
    content += `Общее количество: ${totalShtukov} шт\n`;

    return content;
}

function getFormattedDate() {
   const now = new Date();

       const year = now.getFullYear();
       const month = String(now.getMonth() + 1).padStart(2, '0');
       const day = String(now.getDate()).padStart(2, '0');

       const hours = String(now.getHours()).padStart(2, '0');
       const minutes = String(now.getMinutes()).padStart(2, '0');

       return `${year}${month}${day}_${hours}${minutes}`;
}

function downloadFile(content, filename) {
    const blob = new Blob([content], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 100);
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    // Переопределяем функцию showDetailView для добавления кнопки
    const originalShowDetailView = window.showDetailView;
    window.showDetailView = function(agregat) {
        originalShowDetailView.call(this, agregat);
        setTimeout(addExportButton, 100);
    };
});
