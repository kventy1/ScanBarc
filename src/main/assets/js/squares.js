// ===== КВАДРАТИКИ С ОДНОЙ ВОЛНОЙ (СТАБИЛЬНАЯ) =====

let waveInterval;

function initSquares() {
    const containers = {
        top: document.getElementById('squaresTop'),
        bottom: document.getElementById('squaresBottom'),
        left: document.getElementById('squaresLeft'),
        right: document.getElementById('squaresRight')
    };

    if (Object.values(containers).some(el => !el)) return;

    // Очищаем
    Object.values(containers).forEach(el => el.innerHTML = '');

    const screenWidth = window.innerWidth;
    const screenHeight = window.innerHeight;
    const squareSize = 10; // 8px + 2px gap

    const horizontalCount = Math.ceil(screenWidth / squareSize) + 5;
    const verticalCount = Math.ceil(screenHeight / squareSize) + 5;

    // Создаем квадраты
    for (let i = 0; i < horizontalCount; i++) {
        containers.top.appendChild(createSquare());
        containers.bottom.appendChild(createSquare());
    }

    for (let i = 0; i < verticalCount; i++) {
        containers.left.appendChild(createSquare());
        containers.right.appendChild(createSquare());
    }

    startSimpleWave();
}

function createSquare() {
    const square = document.createElement('div');
    square.className = 'square';
    return square;
}

function startSimpleWave() {
    if (waveInterval) clearInterval(waveInterval);

    const allSquares = document.querySelectorAll('.square');
    const totalSquares = allSquares.length;
    let position = 0;
    const waveWidth = 15; // ширина волны

    waveInterval = setInterval(() => {
        position = (position + 1) % totalSquares;

        allSquares.forEach((square, index) => {
            // Расстояние по кругу
            const dist = Math.min(
                Math.abs(index - position),
                Math.abs(index - (position - totalSquares))
            );

            if (dist < waveWidth) {
                square.classList.add('active');
            } else {
                square.classList.remove('active');
            }
        });
    }, 80);
}

// Запускаем
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initSquares);
} else {
    initSquares();
}

// Пересоздаем при повороте
window.addEventListener('resize', () => {
    if (waveInterval) clearInterval(waveInterval);
    setTimeout(initSquares, 200);
});