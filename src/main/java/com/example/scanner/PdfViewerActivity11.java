package com.example.scanner;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

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
            // Если пришел «голый» путь из сканера (/sdcard/...) — добавляем file://
            finalPdfUri = "file://" + rawPath;
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