// ===== БОКОВЫЕ ПАНЕЛИ =====

function initDrawers() {
    // Проверяем, не созданы ли уже
    if (document.getElementById('drawerContainer')) return;

    const container = document.createElement('div');
    container.id = 'drawerContainer';
    container.className = 'drawer-container';
    container.innerHTML = `
        <div class="drawer-overlay" id="drawerOverlay" onclick="closeAllDrawers()"></div>
        <div class="drawer-left" id="drawerLeft">
            <iframe src="stats.html" class="drawer-frame" id="statsFrame"></iframe>
        </div>
        <div class="drawer-right" id="drawerRight">
            <iframe src="history.html" class="drawer-frame" id="historyFrame"></iframe>
        </div>
    `;
    document.body.appendChild(container);

    // Кнопки с высоким z-index
    const btnLeft = document.createElement('div');
    btnLeft.className = 'drawer-toggle-btn btn-left';
    btnLeft.id = 'btnLeft';
    btnLeft.innerHTML = '▶';
    btnLeft.onclick = toggleLeftDrawer;
    btnLeft.style.zIndex = '2500';
    document.body.appendChild(btnLeft);

    const btnRight = document.createElement('div');
    btnRight.className = 'drawer-toggle-btn btn-right';
    btnRight.id = 'btnRight';
    btnRight.innerHTML = '◀';
    btnRight.onclick = toggleRightDrawer;
    btnRight.style.zIndex = '2500';
    document.body.appendChild(btnRight);

    console.log('✅ Кнопки созданы');
}

function toggleLeftDrawer() {
    const left = document.getElementById('drawerLeft');
    const right = document.getElementById('drawerRight');
    const overlay = document.getElementById('drawerOverlay');
    const isLeftOpen = left.classList.contains('open');
    const isRightOpen = right.classList.contains('open');

    // Если открыта правая панель - сначала закроем её
    if (isRightOpen) {
        right.classList.remove('open');
        // Не закрываем оверлей, т.к. будем открывать левую
    }

    if (!isLeftOpen) {
        const frame = document.getElementById('historyFrame');
        frame.src = frame.src; // Перезагружаем iframe
    }

    left.classList.toggle('open');
    overlay.classList.toggle('active');
    updateOverlay();
}

function toggleRightDrawer() {
    const left = document.getElementById('drawerLeft');
    const right = document.getElementById('drawerRight');
    const overlay = document.getElementById('drawerOverlay');
    const isLeftOpen = left.classList.contains('open');
    const isRightOpen = right.classList.contains('open');

    // Если открыта левая панель - сначала закроем её
    if (isLeftOpen) {
        left.classList.remove('open');
        // Не закрываем оверлей, т.к. будем открывать правую
    }

    if (!isRightOpen) {
        const frame = document.getElementById('statsFrame');
        frame.src = frame.src; // Перезагружаем iframe
    }

    right.classList.toggle('open');
    overlay.classList.toggle('active');
    updateOverlay();
}


window.closeAllDrawers = function() {
    document.getElementById('drawerLeft')?.classList.remove('open');
    document.getElementById('drawerRight')?.classList.remove('open');
    document.getElementById('drawerOverlay')?.classList.remove('active');
    updateOverlay();
}

// Закрытие по кнопке "Назад"
document.addEventListener('backbutton', function() {
    const left = document.getElementById('drawerLeft');
    const right = document.getElementById('drawerRight');

    if (left?.classList.contains('open')) {
        closeAllDrawers();
    } else if (right?.classList.contains('open')) {
        closeAllDrawers();
    }
});

// Запуск
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initDrawers);
} else {
    initDrawers();
}

function updateOverlay() {
    const left = document.getElementById('drawerLeft');
    const right = document.getElementById('drawerRight');
    const overlay = document.getElementById('drawerOverlay');

    // Оверлей активен если открыта хоть одна панель
    if (left.classList.contains('open') || right.classList.contains('open')) {
        overlay.classList.add('active');
    } else {
        overlay.classList.remove('active');
    }
}

