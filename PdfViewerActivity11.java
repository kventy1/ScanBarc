package com.vkdp.scansheme;

// В самом верху файла, после package
import java.io.File;
import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

public class PdfViewerActivity11 extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Получаем путь (из системы или вручную)
        Uri intentUri = getIntent().getData();
        String rawPath = (intentUri != null) ? intentUri.toString() : getIntent().getStringExtra("pdf_path");

        // 2. Если пути нет — выходим
        if (rawPath == null || rawPath.isEmpty()) {
            finish();
            return;
        }

        // 3. ПРАВИЛЬНАЯ ПОДГОТОВКА ПУТИ (Критично!)
        String finalPdfUri;

        if (rawPath.startsWith("content://") || rawPath.startsWith("file://")) {
            // Если путь уже с протоколом — оставляем как есть
            finalPdfUri = rawPath;
        } else {
            // Если пришел «голый» путь из сканера — создаем правильный URI
            File pdfFile = new File(rawPath);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7+ - используем FileProvider для content:// URI
                try {
                    Uri fileUri = FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".fileprovider",
                            pdfFile
                    );
                    finalPdfUri = fileUri.toString();
                } catch (Exception e) {
                    // Fallback на file:// если FileProvider не сработал
                    finalPdfUri = "file://" + rawPath;
                }
            } else {
                // Старые Android - используем file://
                finalPdfUri = "file://" + rawPath;
            }
        }

        // 4. Кодируем только спецсимволы (пробелы, кириллицу), не ломая протокол
        String encodedUri = Uri.encode(finalPdfUri, ":/");

        // 5. Создаем WebView
        webView = new WebView(this);
        setContentView(webView);

        // 6. Настройки WebView
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());

        // 7. Формируем итоговую ссылку
        String viewerUrl = "file:///android_asset/pdfjs/web/viewer.html?file=" + encodedUri;

        // 8. Логи для отладки
        Log.d("PDF_VIEWER", "Final PDF URI: " + finalPdfUri);
        Log.d("PDF_VIEWER", "Viewer URL: " + viewerUrl);

        webView.loadUrl(viewerUrl);
    }



    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}