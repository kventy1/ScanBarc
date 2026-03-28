// Сохранение результата в историю (используем storage.js)
function saveScanToHistory(barcode, filePath, success) {
    // Просто вызываем глобальную функцию из storage.js
    if (window.saveScan) {
        window.saveScan(barcode, filePath, success);
    }
}

// Подключаем к onScanResult
function onScanResult(barcode, filePath) {
    lastBarcode = barcode;
    lastFilePath = filePath;

    document.getElementById('barcodeValue').innerText = barcode;

    if (filePath && filePath.length > 0) {
        document.getElementById('openBtn').style.display = 'block';
        saveScanToHistory(barcode, filePath, true);
    } else {
        document.getElementById('openBtn').style.display = 'none';
        saveScanToHistory(barcode, '', false);
    }
}