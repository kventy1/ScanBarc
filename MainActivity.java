package com.vkdp.scansheme;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int MANAGE_STORAGE_CODE = 101;
    private static final String PREFS_NAME = "scanner_settings";

    private boolean isAutoEnabled = false;
    private String lastFoundFilePath = "";
    private WebView webView;
    private SharedPreferences prefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        createAppFolders();
        setupWebView();


    }
    private void createAppFolders() {
        File pdfFolder = new File(getExternalFilesDir(null), "PDFs");
        if (!pdfFolder.exists()) {
            pdfFolder.mkdirs();
        }
    }

    private void setupWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setTextZoom(100);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowContentAccess(true);
// GPU ускорение
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        }
//
//// Дополнительные оптимизации
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//            WebView.setWebContentsDebuggingEnabled(true); // для отладки, можно убрать
//        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!lastFoundFilePath.isEmpty()) {
                    String js = "javascript:setLastFile('" + lastFoundFilePath + "')";
                    webView.evaluateJavascript(js, null);
                }
            }
        });

        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidScanner");
        webView.loadUrl("file:///android_asset/index.html");

        FrameLayout root = findViewById(android.R.id.content);
        root.addView(webView);
    }




    class WebAppInterface {
        @JavascriptInterface
        public String getAppFolderInfo() {
            File appFolder = new File(getExternalFilesDir(null), "PDFs");
            if (!appFolder.exists()) {
                appFolder.mkdirs();
            }
            return appFolder.getAbsolutePath();
        }

        @JavascriptInterface
        public void openScanner() {
            runOnUiThread(() -> {
                checkCameraPermission();
            });
        }

        @JavascriptInterface
        public void setAutoMode(boolean enabled) {
            runOnUiThread(() -> {
                isAutoEnabled = enabled;
            });
        }

        @JavascriptInterface
        public String getLastFile() {
            return lastFoundFilePath;
        }

        @JavascriptInterface
        public void testConnection() {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Связь с Java работает!", Toast.LENGTH_SHORT).show();
            });
        }


        @JavascriptInterface
        public void openFile(String filePath) {
            runOnUiThread(() -> {
                try {
                    String path = filePath.replace("file://", "");
                    File originalFile = new File(path);

                    if (!originalFile.exists()) {
                        Toast.makeText(MainActivity.this,
                                "Файл не найден: " + originalFile.getName(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // === ИСПОЛЬЗУЕМ FILEPROVIDER ДЛЯ INTENT ===
                    Uri fileUri;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        // Android 7+ - FileProvider
                        fileUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                getPackageName() + ".fileprovider",
                                originalFile
                        );
                    } else {
                        // Старые версии - file://
                        fileUri = Uri.fromFile(originalFile);
                    }

                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(fileUri, "application/pdf");
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    // Добавляем разрешение для всех приложений
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    try {
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this,
                                "Не найдено приложение для открытия PDF\n" +
                                        "Установите PDF-ридер из Play Store",
                                Toast.LENGTH_LONG).show();
                    }

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Ошибка открытия: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        }



        @JavascriptInterface
        public void saveTextFile(String filename, String content) {
            runOnUiThread(() -> {
                try {
                    File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File scannerFolder = new File(downloadsDir, "ScannerStats");
                    if (!scannerFolder.exists()) {
                        scannerFolder.mkdirs();
                    }

                    File file = new File(scannerFolder, filename);

                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(content.getBytes(StandardCharsets.UTF_8));
                    }

                    Toast.makeText(MainActivity.this,
                            "Файл сохранен: ScannerStats/" + file.getName(),
                            Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Ошибка сохранения: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }




    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        } else {
            openScanner();
        }
    }


    private void openScanner() {
        Intent intent = new Intent(MainActivity.this, ScanActivity.class);
        intent.putExtra("auto_enabled", isAutoEnabled);
        startActivityForResult(intent, 1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1000 && resultCode == RESULT_OK && data != null) {
            lastFoundFilePath = data.getStringExtra("file_path");
            String barcode = data.getStringExtra("barcode");

            if (lastFoundFilePath != null) {
                // Сохраняем ВСЕ результаты в историю (и картинки, и PDF)
                saveScanToHistory(barcode, lastFoundFilePath);

                String lowerPath = lastFoundFilePath.toLowerCase();

                // Если это КАРТИНКА — открываем через галерею
                if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") ||
                        lowerPath.endsWith(".png") || lowerPath.endsWith(".webp")) {

                    openImage(new File(lastFoundFilePath));
                }
                // Если это PDF или что-то другое — открываем через WebView
                else if (webView != null) {
                    // JavaScript уже вызван в saveScanToHistory,
                    // но если нужна дополнительная логика, можно оставить пустым
                    // или добавить уведомление в WebView
                    String js = "javascript:onScanResultComplete('"
                            + (barcode != null ? barcode.replace("'", "\\'") : "")
                            + "')";
                    webView.evaluateJavascript(js, null);
                }
            }
        } else if (requestCode == MANAGE_STORAGE_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    Toast.makeText(this, "Доступ к файлам получен", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Нет доступа к файлам", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void openImage(File file) {
        try {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            // Определяем точный тип по расширению
            String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString());
            String mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());

            if (mimeType == null) mimeType = "image/jpeg"; // Фолбэк на самый частый тип

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType); // Указываем точный тип (image/jpeg, image/png и т.д.)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // ВАЖНО: Не используй Intent.createChooser!
            // Если запустить напрямую, появится системный диалог с кнопкой "Всегда"
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void saveScanToHistory(String barcode, String filePath) {
        if (webView != null && filePath != null) {
            String js = "javascript:onScanResult('"
                    + (barcode != null ? barcode.replace("'", "\\'") : "")
                    + "', '"
                    + filePath.replace("'", "\\'")
                    + "')";
            webView.evaluateJavascript(js, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();  // Ставим WebView на паузу
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();  // Снимаем с паузы
        }
    }
    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("javascript:document.dispatchEvent(new Event('backbutton'))", null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openScanner();
            } else {
                Toast.makeText(this, "Нужно разрешение на камеру", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == MANAGE_STORAGE_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Доступ к файлам получен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Нет доступа к файлам", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
               super.onDestroy();
    }
}