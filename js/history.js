// ===== ИСТОРИЯ =====

let scansData = [];
let currentEditId = null;

// Функция парсинга выражения штуков
function parseShtukovExpression(expression) {
    if (!expression) return 0;

    try {
        // Заменяем запятые и пробелы на плюсы
        const cleanExpr = expression.toString()
            .replace(/,/g, '+')
            .replace(/\s+/g, '+')
            .replace(/[^\d+\-]/g, ''); // Оставляем только цифры, плюсы и минусы

        if (!cleanExpr) return 0;
        return eval(cleanExpr) || 0;
    } catch (e) {
        console.error('Ошибка парсинга штуков:', e);
        return 0;
    }
}


// Загрузка
function loadHistory() {
    scansData = JSON.parse(localStorage.getItem('all_scans') || '[]');
    renderHistory();
}

// Отрисовка
function renderHistory() {
    const list = document.getElementById('list');
    if (!list) return;

    // Статистика
    const total = scansData.length;
    document.getElementById('stats').innerText = total;

    if (scansData.length === 0) {
        list.innerHTML = '<div class="empty">История пуста</div>';
        return;
    }

    let html = '';
    scansData.forEach(s => {
        html += `
            <div class="item ${s.success ? 'success' : 'error'}" data-id="${s.id}">
                <div class="touch-area">
                    <!-- ЛЕВАЯ ЗОНА - РЕДАКТИРОВАНИЕ -->
                    <div class="touch-zone edit-zone" onclick="openEdit('${s.id}')">
                        <div class="barcode">${s.barcode}</div>
                        <div class="time">${s.timestamp}</div>
                    </div>

                    <!-- ЦЕНТРАЛЬНАЯ ЗОНА - ОТКРЫТИЕ ФАЙЛА -->
                    ${s.filePath ?
                        '<div class="touch-zone open-zone极" onclick="openFileFromHistory(\'' + s.id + '\')">📂<span>ОТКРЫТЬ</span></div>' :
                        '<div class="touch-zone open-zone disabled">📂<span>НЕТ</span></div>'
                    }

                    <!-- ПРАВАЯ ЗОНА - УДАЛЕНИЕ -->
                    <div class="touch-zone delete-zone" onclick="deleteItem('${s.id}')">🗑️<span>УДАЛИТЬ</span></div>
                </div>
                <div class="fields">
                    ${s.agregat ? `<span class="field">🏭 ${s.agregat}</span>` : ''}
                    ${s.shtukov_expr ? `<span class="field">🔢 ${s.shtukov_expr} = ${s.shtukov}шт</span>` :
                      s.shtukov ? `<span class="field">🔢 ${s.shtukov}шт</span>` : ''}
                    ${s.size ? `<span class="field">📏 ${s.size}</span>` : ''}
                    ${s.model ? `<span class="field">📱 ${s.model}</span>` : ''}
                    ${s.color ? `<span class="field">🎨 ${s.color}</span>` : ''}
                </div>
            </div>
        `;
    });
    list.innerHTML = html;
}

// Открыть редактирование
window.openEdit = function(id) {
    const scan = scansData.find(s => s.id === id);
    if (!scan) return;

    currentEditId = id;
    document.getElementById('modalBarcode').innerText = scan.barcode;
    document.getElementById('agregat').value = scan.agregat || '';
    // Показываем выражение если есть, иначе число
    document.getElementById('shtukov').value = scan.shtukov_expr || scan.shtukov || '';
    document.getElementById('size').value = scan.size || '';
    document.getElementById('model').value = scan.model || '';
    document.getElementById('color').value = scan.color || '';
    document.getElementById('modal').style.display = 'flex';
};

// Сохранить
window.saveEdit = function() {
    if (!currentEditId) return;

    const index = scansData.findIndex(s => s.id === currentEditId);
    if (index === -1) return;

    // Парсим выражение штуков
    const shtukovExpr = document.getElementById('shtukov').value.trim();
    const shtukovValue = parseShtukovExpression(shtukovExpr);

    scansData[index].agregat = document.getElementById('agregat').value.trim();
    scansData[index].shtukov = shtukovValue; // Сохраняем ЧИСЛО
    scansData[index].shtukov_expr = shtukovExpr; // Сохраняем выражение для отображения
    scansData[index].size = document.getElementById('size').value.trim();
    scansData[index].model = document.getElementById('model').value.trim();
    scansData[index].color = document.getElementById('color').value.trim();

    localStorage.setItem('all_scans', JSON.stringify(scansData));
    renderHistory();
    closeModal();
    if (window.parent) window.parent.postMessage({ type: 'refresh' }, '*');
};

// ОТКРЫТЬ ФАЙЛ ИЗ ИСТОРИИ
window.openFileFromHistory = function(id) {
    const scan = scansData.find(s => s.id === id);
    if (!scan || !scan.filePath) return;

    // ТОЛЬКО через сообщение родителю
    if (window.parent && window.parent !== window) {
        window.parent.postMessage({
            type: 'openFile',
            filePath: scan.filePath
        }, '*');
    }
};

window.deleteItem = function(id) {
    if (window.deleteScan) {
        window.deleteScan(id);
        // Принудительно перезагружаем через 100мс
        setTimeout(() => loadHistory(), 10);
    }
};

// Закрыть модалку
window.closeModal = function() {
    document.getElementById('modal').style.display = 'none';
    currentEditId = null;
};

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    loadHistory();

    document.getElementById('modal').addEventListener('click', (e) => {
        if (e.target === document.getElementById('modal')) closeModal();
    });
});

// Слушаем обновления
window.addEventListener('storage', loadHistory);
window.addEventListener('message', (e) => {
    if (e.data?.type === 'refresh') loadHistory();
});

window.clearAllHistory = function() {
    // Очищаем историю без подтверждения
    localStorage.setItem('all_scans', '[]');
    scansData = [];
    renderHistory();

    // Уведомляем другие окна
    if (window.parent) window.parent.postMessage({ type: 'refresh' }, '*');
    window.dispatchEvent(new Event('storage'));
};
