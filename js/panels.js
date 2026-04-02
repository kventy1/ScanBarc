// Конфигурация панелей
const PANELS = [
    {
        id: 'statsPanel',
        position: 'bottom-left',
        size: 'medium',
        theme: 'cyan',
        title: '📊 СТАТИСТИКА',
        ticker: '✦ СКАНИРУЙ ✦ НАХОДИ ✦ ОТКРЫВАЙ ✦',
        data: {
            scans: 0,
            found: 0,
            lost: 0
        }
    },
    {
        id: 'infoPanel',
        position: 'top-right',
        size: 'small',
        theme: 'pink',
        title: 'ℹ️ ИНФО',
        ticker: '✦ СКАНЕР ✦ v1.0 ✦',
        data: {
            version: '1.0',
            status: 'active'
        }
    },
    {
        id: 'historyPanel',
        position: 'top-left',
        size: 'large',
        theme: 'green',
        title: '📜 ИСТОРИЯ',
        ticker: '✦ ПОСЛЕДНИЙ СКАН ✦',
        data: {
            last: '—',
            time: '—'
        }
    }
];

// Создание панелей
function createPanels() {
    PANELS.forEach(panel => {
        // Проверяем, нет ли уже такой панели
        if (document.getElementById(panel.id)) return;

        const panelDiv = document.createElement('div');
        panelDiv.id = panel.id;
        panelDiv.className = `glass-panel panel-${panel.position} panel-${panel.size} panel-${panel.theme}`;

        // HTML структура
        panelDiv.innerHTML = `
            <div class="panel-title">${panel.title}</div>
            <div class="panel-content" id="${panel.id}-content"></div>
            <div class="panel-ticker">
                <div class="panel-ticker-text">${panel.ticker}</div>
            </div>
        `;

        document.body.appendChild(panelDiv);
        updatePanelData(panel.id, panel.data);
    });
}

// Обновление данных панели
function updatePanelData(panelId, data) {
    const content = document.getElementById(`${panelId}-content`);
    if (!content) return;

    let html = '';
    for (let [key, value] of Object.entries(data)) {
        html += `
            <div class="panel-line">
                <span class="panel-label">${key}:</span>
                <span class="panel-value">${value}</span>
            </div>
        `;
    }
    content.innerHTML = html;
}

// Обновление конкретного значения
function updatePanelValue(panelId, key, value) {
    const panel = PANELS.find(p => p.id === panelId);
    if (panel) {
        panel.data[key] = value;
        updatePanelData(panelId, panel.data);
    }
}

// Запуск
document.addEventListener('DOMContentLoaded', createPanels);