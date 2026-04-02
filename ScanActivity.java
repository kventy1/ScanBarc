package com.vkdp.scansheme;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScanActivity extends AppCompatActivity {

    // === ШУТКИ ===
    private List<String> lostJokes = new ArrayList<>();
    private final Random random = new Random();
    private int missCount = 0;
    private boolean isJokeVisible = false;

    // === КАМЕРА И СКАНЕР ===
    private PreviewView previewView;
    private TextView resultText;
    private ExecutorService cameraExecutor;
    private boolean isScanning = true;
    private View scanOverlay;
    private TachometerView tachometer;
    private final Handler handler = new Handler();
    private String scannedBarcode = "";

    // === НЕОНОВЫЕ ЭЛЕМЕНТЫ ===
    private TextView neonHint;
    private View neonBarBrightness;
    private View neonBarContrast;
    private Button closeButton;
    private androidx.camera.core.Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        loadJokes();

        FrameLayout rootLayout = new FrameLayout(this);

        // Камера
        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Оверлей для рамки
        scanOverlay = new View(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                drawNeonFrame(canvas);
            }
        };
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(Color.parseColor("#00000000")); // Полупрозрачный черный (80 - прозрачность)
        gd.setCornerRadius(20); // Закругляем углы


        Button flashBtn = new Button(this);
        flashBtn.setText("х1000");
        flashBtn.setTextColor(Color.WHITE);
        flashBtn.setBackground(gd); // Применяем фон
        flashBtn.setAllCaps(false); // Чтобы текст не был капсом

// 2. Параметры расположения (Ширина 220, Высота 100)
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(250, 300);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.setMargins(0, 1885, 70, 200); // Чуть ниже от края
        rootLayout.addView(flashBtn, lp);

// 3. Логика с изменением цвета при нажатии
        flashBtn.setOnClickListener(v -> {
            if (camera != null) {
                boolean isOn = camera.getCameraInfo().getTorchState().getValue() == 1;
                camera.getCameraControl().enableTorch(!isOn);

                flashBtn.setText(!isOn ? "х1000000" : "х1000");
                // Если включен - делаем фон золотистым, если выключен - черным
                gd.setColor(!isOn ? Color.parseColor("#00000000") : Color.parseColor("#00000000"));
                flashBtn.setBackground(gd);
            }
        });
        scanOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Тахометр
        tachometer = new TachometerView(this, null);
        FrameLayout.LayoutParams tachParams = new FrameLayout.LayoutParams(300, 300);
        tachParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        tachParams.bottomMargin = 50;
        tachParams.rightMargin = 50;
        tachometer.setLayoutParams(tachParams);

        // Результат сканирования
        resultText = new TextView(this);
        resultText.setTextSize(72);
        resultText.setGravity(Gravity.CENTER);
        resultText.setTypeface(null, Typeface.BOLD);
        resultText.setTextColor(Color.RED);
        resultText.setShadowLayer(15, 5, 5, Color.BLACK);
        resultText.setVisibility(View.GONE);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        resultText.setLayoutParams(textParams);

        // ========== НЕОНОВАЯ ПОДСКАЗКА ==========
        neonHint = new TextView(this);
        neonHint.setTextSize(28);
        neonHint.setGravity(Gravity.CENTER);
        neonHint.setTextColor(Color.CYAN);
        try {
            Typeface customFont = Typeface.createFromAsset(getAssets(), "fonts/DS Cristal.ttf");
            neonHint.setTypeface(customFont);
        } catch (Exception e) {
            neonHint.setTypeface(Typeface.MONOSPACE);
        }
        neonHint.setShadowLayer(20, 0, 0, Color.CYAN);
        neonHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        hintParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hintParams.topMargin = 150;
        neonHint.setLayoutParams(hintParams);
        // =====================================

        // ========== НЕОНОВЫЕ ЭКВАЛАЙЗЕРЫ ==========
        // Полоска яркости (горизонтальная, зелёная)
        neonBarBrightness = new View(this);
        neonBarBrightness.setBackgroundColor(Color.argb(200, 0, 255, 100));
        neonBarBrightness.setAlpha(0.8f);
        FrameLayout.LayoutParams brightParams = new FrameLayout.LayoutParams(0, 16);
        brightParams.gravity = Gravity.BOTTOM | Gravity.START;
        brightParams.bottomMargin = 70;
        brightParams.leftMargin = 30;
        neonBarBrightness.setLayoutParams(brightParams);

        // Полоска контраста (горизонтальная, голубая)
        neonBarContrast = new View(this);
        neonBarContrast.setBackgroundColor(Color.argb(200, 0, 200, 255));
        neonBarContrast.setAlpha(0.8f);
        FrameLayout.LayoutParams contrastParams = new FrameLayout.LayoutParams(0, 16);
        contrastParams.gravity = Gravity.BOTTOM | Gravity.START;
        contrastParams.bottomMargin = 50;
        contrastParams.leftMargin = 30;
        neonBarContrast.setLayoutParams(contrastParams);
        // ========================================

        // ========== НЕОНОВАЯ КНОПКА ЗАКРЫТИЯ ==========
        closeButton = new Button(this);
        closeButton.setText("✕");
        closeButton.setTextSize(28);
        closeButton.setBackgroundColor(Color.TRANSPARENT);
        closeButton.setTextColor(Color.RED);
        try {
            Typeface customFont = Typeface.createFromAsset(getAssets(), "fonts/DS Cristal.ttf");
            closeButton.setTypeface(customFont);
        } catch (Exception e) {
            closeButton.setTypeface(Typeface.MONOSPACE);
        }
        closeButton.setShadowLayer(15, 0, 0, Color.RED);
        closeButton.setPadding(25, 15, 25, 15);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        closeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        closeParams.topMargin = 40;
        closeParams.rightMargin = 30;
        closeButton.setLayoutParams(closeParams);
        closeButton.setOnClickListener(v -> finish());
        // ==============================================

        rootLayout.addView(previewView);
        rootLayout.addView(scanOverlay);
        rootLayout.addView(tachometer);
        rootLayout.addView(resultText);
        rootLayout.addView(neonHint);
        rootLayout.addView(neonBarBrightness);
        rootLayout.addView(neonBarContrast);
        rootLayout.addView(closeButton);

        setContentView(rootLayout);

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(this, "Нет разрешения на камеру", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    // ========== НЕОНОВАЯ РАМКА ==========
    private void drawNeonFrame(Canvas canvas) {
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        int frameSize = (int)(width * 0.8);
        int left = (width - frameSize) / 2;
        int top = (height - frameSize) / 2;
        int right = left + frameSize;
        int bottom = top + frameSize;

        Paint paint = new Paint();

        // Основная рамка с неоновым свечением
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8);
        paint.setShadowLayer(15, 0, 0, Color.GREEN);
        canvas.drawRect(left, top, right, bottom, paint);

        // Внешнее свечение
        paint.setStrokeWidth(2);
        paint.setAlpha(80);
        paint.setShadowLayer(25, 0, 0, Color.GREEN);
        canvas.drawRect(left - 3, top - 3, right + 3, bottom + 3, paint);

        // Угловые элементы
        paint.setStrokeWidth(12);
        paint.setAlpha(255);
        int cornerSize = 40;
        canvas.drawLine(left, top, left + cornerSize, top, paint);
        canvas.drawLine(left, top, left, top + cornerSize, paint);
        canvas.drawLine(right, top, right - cornerSize, top, paint);
        canvas.drawLine(right, top, right, top + cornerSize, paint);
        canvas.drawLine(left, bottom, left + cornerSize, bottom, paint);
        canvas.drawLine(left, bottom, left, bottom - cornerSize, paint);
        canvas.drawLine(right, bottom, right - cornerSize, bottom, paint);
        canvas.drawLine(right, bottom, right, bottom - cornerSize, paint);

        Paint dark = new Paint();
        dark.setColor(0xAA000000);
        canvas.drawRect(0, 0, width, top, dark);
        canvas.drawRect(0, bottom, width, height, dark);
        canvas.drawRect(0, top, left, bottom, dark);
        canvas.drawRect(right, top, width, bottom, dark);

    }
    // ==================================

    // ========== АНИМАЦИЯ ПУЛЬСАЦИИ ==========
       // ========================================

    // ========== ОБНОВЛЕНИЕ ЭКВАЛАЙЗЕРОВ ==========
    private void updateNeonBars(int brightnessPercent, int contrastPercent) {
        int width = getWindow().getDecorView().getWidth();
        if (width == 0) return;

        int maxBarWidth = width - 100;
        int brightWidth = maxBarWidth * brightnessPercent / 100;
        int contrastWidth = maxBarWidth * contrastPercent / 100;

        FrameLayout.LayoutParams brightParams = (FrameLayout.LayoutParams) neonBarBrightness.getLayoutParams();
        brightParams.width = Math.max(10, brightWidth);
        neonBarBrightness.setLayoutParams(brightParams);

        FrameLayout.LayoutParams contrastParams = (FrameLayout.LayoutParams) neonBarContrast.getLayoutParams();
        contrastParams.width = Math.max(10, contrastWidth);
        neonBarContrast.setLayoutParams(contrastParams);

        // Меняем цвет в зависимости от значения
        if (brightnessPercent > 70) {
            neonBarBrightness.setBackgroundColor(Color.argb(200, 0, 255, 100));
        } else if (brightnessPercent > 30) {
            neonBarBrightness.setBackgroundColor(Color.argb(200, 255, 255, 100));
        } else {
            neonBarBrightness.setBackgroundColor(Color.argb(200, 255, 80, 80));
        }

        if (contrastPercent > 70) {
            neonBarContrast.setBackgroundColor(Color.argb(200, 0, 200, 255));
        } else if (contrastPercent > 30) {
            neonBarContrast.setBackgroundColor(Color.argb(200, 100, 255, 200));
        } else {
            neonBarContrast.setBackgroundColor(Color.argb(200, 255, 100, 100));
        }
    }
    // ============================================

    // ========== РАСЧЁТ ЯРКОСТИ И КОНТРАСТА ==========
    private void updateBrightnessAndContrast(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError") android.media.Image image = imageProxy.getImage();
        if (image == null) return;

        try {
            android.media.Image.Plane yPlane = image.getPlanes()[0];
            ByteBuffer buffer = yPlane.getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            long sum = 0;
            for (byte b : data) {
                sum += b & 0xFF;
            }
            int avgBrightness = (int) (sum / data.length);

            long variance = 0;
            for (byte b : data) {
                int diff = (b & 0xFF) - avgBrightness;
                variance += (long) diff * diff;
            }
            int contrast = (int) Math.sqrt(variance / data.length);

            int brightnessPercent = (avgBrightness * 100) / 255;
            int contrastPercent = Math.min(contrast, 100);

            runOnUiThread(() -> updateNeonBars(brightnessPercent, contrastPercent));

        } catch (Exception e) {
            Log.e("ScanActivity", "Ошибка расчёта яркости: " + e.getMessage());
        }
    }
    // =================================================

    // === МЕТОДЫ ДЛЯ ШУТОК ===
    private void loadJokes() {
        try {
            InputStream is = getAssets().open("lost_jokes.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    lostJokes.add(line);
                }
            }
            reader.close();
        } catch (IOException e) {
            lostJokes = Arrays.asList("Промах!", "Не найдено");
        }
        Collections.shuffle(lostJokes);
    }

    private String getRandomJoke() {
        if (lostJokes.isEmpty()) return "Промах!";
        missCount++;
        long seed = System.currentTimeMillis() + missCount + scannedBarcode.hashCode();
        random.setSeed(seed);
        return lostJokes.get(random.nextInt(lostJokes.size()));
    }

    private void hideJoke() {
        isJokeVisible = false;
        resultText.setVisibility(View.GONE);
        isScanning = true;
        resultText.setOnClickListener(null);
    }
    // =========================

    @SuppressLint("UnsafeOptInUsageError")
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::scanBarcode);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Ошибка запуска камеры", Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void scanBarcode(ImageProxy imageProxy) {
        if (!isScanning) {
            imageProxy.close();
            return;
        }

        updateBrightnessAndContrast(imageProxy);

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        BarcodeScanning.getClient().process(image)
                .addOnSuccessListener(barcodes -> {
                    // Показываем неоновую подсказку в реальном времени
                    runOnUiThread(() -> {
                        if (barcodes.size() > 0 && isScanning) {
                            String code = barcodes.get(0).getRawValue();
                            if (code != null && !code.isEmpty()) {
                                neonHint.setText("⚡ " + code + " ⚡");
                                neonHint.setVisibility(View.VISIBLE);
                            }
                        } else {
                            neonHint.setVisibility(View.GONE);
                        }
                    });

                    for (Barcode barcode : barcodes) {
                        String data = barcode.getRawValue();
                        if (data != null) {

                            if ("16091982".equals(data)) {
                                runOnUiThread(() -> {
                                    previewView.setVisibility(View.GONE);
                                    scanOverlay.setVisibility(View.GONE);
                                    tachometer.setVisibility(View.GONE);
                                    neonHint.setVisibility(View.GONE);
                                    neonBarBrightness.setVisibility(View.GONE);
                                    neonBarContrast.setVisibility(View.GONE);
                                    closeButton.setVisibility(View.GONE);

                                    ImageView secretImage = new ImageView(this);
                                    secretImage.setLayoutParams(new FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                    ));
                                    secretImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                    secretImage.setBackgroundColor(Color.BLACK);

                                    try {
                                        InputStream inputStream = getAssets().open("sva.jpg");
                                        secretImage.setImageBitmap(BitmapFactory.decodeStream(inputStream));
                                        inputStream.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }

                                    secretImage.setOnClickListener(v -> {
                                        ((FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content)).removeView(secretImage);
                                        finish();
                                    });

                                    ((FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content)).addView(secretImage);
                                });
                                imageProxy.close();
                                return;
                            }

                            isScanning = false;
                            scannedBarcode = data;

                            String filePath = findFileByBarcode(data);

                            if (filePath != null) {
                                openFile(filePath);
                            } else {
                                Intent resultIntent = new Intent();
                                resultIntent.putExtra("barcode", scannedBarcode);
                                resultIntent.putExtra("file_path", "");
                                setResult(RESULT_OK, resultIntent);

                                runOnUiThread(() -> {
                                    if (isJokeVisible) return;

                                    isJokeVisible = true;
                                    String joke = getRandomJoke();

                                    resultText.setText(joke);
                                    resultText.setTextSize(38);
                                    resultText.setTextColor(0xFFFF0000);
                                    try {
                                        Typeface customFont = Typeface.createFromAsset(getAssets(), "fonts/ConquerorSemiBold.otf");
                                        resultText.setTypeface(customFont);
                                    } catch (Exception e) {
                                        resultText.setTypeface(Typeface.DEFAULT_BOLD);
                                        e.printStackTrace();
                                    }
                                    resultText.setGravity(Gravity.CENTER);
                                    resultText.setShadowLayer(20, 5, 5, Color.BLACK);
                                    resultText.setMaxLines(6);
                                    resultText.setEllipsize(TextUtils.TruncateAt.END);
                                    resultText.setVisibility(View.VISIBLE);

                                    handler.postDelayed(() -> {
                                        if (isJokeVisible) {
                                            hideJoke();
                                        }
                                    }, 6000);

                                    resultText.setOnClickListener(v -> {
                                        hideJoke();
                                    });
                                });
                            }
                            break;
                        }
                    }
                })
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private String findFileByBarcode(String barcode) {
         return findViaFileWalk(barcode);
    }

       private String findViaFileWalk(String barcode) {
        File appFolder = new File(getExternalFilesDir(null), "PDFs");
        if (!appFolder.exists()) {
            appFolder.mkdirs();
            return null;
        }

        // Формируем searchPattern как раньше
        String[] underscoreParts = barcode.split("_");
        if (underscoreParts.length >= 2) {
            String lastPart = underscoreParts[underscoreParts.length - 1];
            if ("01".equals(lastPart)) {
                String[] newParts = new String[underscoreParts.length - 1];
                System.arraycopy(underscoreParts, 0, newParts, 0, underscoreParts.length - 1);
                barcode = String.join("_", newParts);
            }
        }
        String searchPattern = barcode.replace("_", ".");
        String[] parts = searchPattern.split("\\.");
        if (parts.length >= 2) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.matches("\\d{2}")) {
                parts[parts.length - 1] = "0" + lastPart;
                searchPattern = String.join(".", parts);
            }
        }

        // === ВСТАВЛЯЕМ РЕКУРСИВНЫЙ ПОИСК ===
        return findRecursive(appFolder, searchPattern, barcode);
    }

    // Новый рекурсивный метод поиска
    private String findRecursive(File folder, String searchPattern, String originalBarcode) {
        if (folder == null || !folder.exists()) return null;

        File[] files = folder.listFiles();
        if (files == null) return null;

        // Приводим к нижнему регистру один раз для скорости
        String lowPattern = searchPattern.toLowerCase();
        String lowBarcode = originalBarcode.toLowerCase();

        for (File file : files) {
            if (file.isFile()) {
                String fileName = file.getName().toLowerCase();

                // 1. Простая проверка на вхождение в полное имя
                if (fileName.contains(lowPattern) || fileName.contains(lowBarcode)) {
                    return file.getAbsolutePath();
                }

                // 2. Умная проверка без расширения (для любых файлов)
                int lastDot = fileName.lastIndexOf(".");
                if (lastDot > 0) {
                    String nameOnly = fileName.substring(0, lastDot);
                    if (nameOnly.contains(lowPattern) || nameOnly.contains(lowBarcode)) {
                        return file.getAbsolutePath();
                    }
                }
            }
        }

        // Рекурсия по подпапкам
        for (File file : files) {
            if (file.isDirectory()) {
                String result = findRecursive(file, searchPattern, originalBarcode);
                if (result != null) return result;
            }
        }
        return null;
    }


    private void openFile(String filePath) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("barcode", scannedBarcode);
        resultIntent.putExtra("file_path", filePath);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        handler.removeCallbacksAndMessages(null);
    }
}