package com.example.scanner;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import java.io.FileNotFoundException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int MANAGE_STORAGE_CODE = 101;
    private static final String PREFS_NAME = "scanner_settings";
    private static final String PREF_DEFAULT_PDF_APP = "default_pdf_app";
    private static final String PREF_DEFAULT_PDF_METHOD = "default_pdf_method";

    private boolean isAutoEnabled = false;
    private String lastFoundFilePath = "";
    private WebView webView;
    private SharedPreferences prefs;

    private NanoHTTPD localServer;
    private final int serverPort = 8080;
    private File wwwDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupWebView();
        startLocalServer();
        checkManageStoragePermission();
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

    private void startLocalServer() {
        wwwDir = new File(getCacheDir(), "www");
        if (!wwwDir.exists()) {
            wwwDir.mkdirs();
        }

        new Thread(() -> {
            try {
                localServer = new NanoHTTPD(serverPort) {
                    @Override
                    public Response serve(IHTTPSession session) {
                        String uri = session.getUri();
                        String fileName = uri.startsWith("/") ? uri.substring(1) : uri;
                        File requestedFile = new File(wwwDir, fileName);

                        if (requestedFile.exists() && !requestedFile.isDirectory()) {
                            try {
                                FileInputStream fis = new FileInputStream(requestedFile);
                                String mimeType = "application/pdf";
                                return newFixedLengthResponse(Response.Status.OK, mimeType, fis, requestedFile.length());
                            } catch (FileNotFoundException e) {
                                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
                            }
                        } else {
                            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
                        }
                    }
                };

                localServer.start();
                Log.i("NanoHTTPD", "Сервер запущен на порту " + serverPort);

            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Сервер не запущен: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    class WebAppInterface {

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

                    String fileName = originalFile.getName();
                    String extension = "";
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        extension = fileName.substring(dotIndex + 1).toLowerCase();
                    }

                    if (extension.equals("pdf")) {
                        // Проверяем, есть ли сохраненный выбор
                        String defaultApp = prefs.getString(PREF_DEFAULT_PDF_APP, null);
                        String defaultMethod = prefs.getString(PREF_DEFAULT_PDF_METHOD, null);

                        if (defaultApp != null && defaultMethod != null) {
                            // Используем сохраненный выбор
                            openPdfWithDefault(originalFile, defaultApp, defaultMethod);
                        } else {
                            // Показываем диалог выбора
                            showPdfAppChooser(originalFile);
                        }
                    }
                    else {
                        Uri fileUri;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            fileUri = FileProvider.getUriForFile(
                                    MainActivity.this,
                                    getPackageName() + ".fileprovider",
                                    originalFile
                            );
                        } else {
                            fileUri = Uri.fromFile(originalFile);
                        }

                        String mimeType = "*/*";
                        if (!extension.isEmpty()) {
                            MimeTypeMap mime = MimeTypeMap.getSingleton();
                            mimeType = mime.getMimeTypeFromExtension(extension);
                            if (mimeType == null) {
                                mimeType = MainActivity.this.getMimeTypeFallback(extension);
                            }
                        }

                        Log.i("FileOpen", "Пытаемся открыть: " + fileName + " (" + mimeType + ")");

                        if (!tryOpenWithBestApp(fileUri, mimeType, extension)) {
                            trySystemChooser(fileUri, mimeType);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
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

    private void openPdfWithDefault(File pdfFile, String packageName, String method) {
        try {
            if (method.equals("local")) {
                String uniqueFileName = "doc_" + System.currentTimeMillis() + ".pdf";
                File servedFile = new File(wwwDir, uniqueFileName);
                copyFile(pdfFile, servedFile);

                String url = "http://localhost:" + serverPort + "/" + uniqueFileName;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage(packageName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } else {
                Uri fileUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        pdfFile
                );
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, "application/pdf");
                intent.setPackage(packageName);
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            Log.i("FileOpen", "PDF открыт через сохраненный выбор: " + packageName);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка открытия сохраненным приложением", Toast.LENGTH_SHORT).show();
            // Если не получилось, забываем сохраненный выбор и показываем диалог
            prefs.edit().remove(PREF_DEFAULT_PDF_APP).remove(PREF_DEFAULT_PDF_METHOD).apply();
            showPdfAppChooser(pdfFile);
        }
    }

    private void showPdfAppChooser(File pdfFile) {
        try {
            PackageManager pm = getPackageManager();

            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    pdfFile
            );

            Intent pdfIntent = new Intent(Intent.ACTION_VIEW);
            pdfIntent.setDataAndType(fileUri, "application/pdf");
            pdfIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String uniqueFileName = "doc_" + System.currentTimeMillis() + ".pdf";
            File servedFile = new File(wwwDir, uniqueFileName);
            copyFile(pdfFile, servedFile);
            String url = "http://localhost:" + serverPort + "/" + uniqueFileName;
            Intent httpIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            httpIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            List<ResolveInfo> pdfApps = pm.queryIntentActivities(pdfIntent, 0);
            List<ResolveInfo> httpApps = pm.queryIntentActivities(httpIntent, 0);

            List<AppOption> appOptions = new ArrayList<>();

            for (ResolveInfo app : httpApps) {
                if (!containsPackage(appOptions, app.activityInfo.packageName)) {
                    AppOption option = new AppOption();
                    option.packageName = app.activityInfo.packageName;
                    option.appName = app.loadLabel(pm).toString();
                    option.method = "local";
                    appOptions.add(option);
                }
            }

            for (ResolveInfo app : pdfApps) {
                if (!containsPackage(appOptions, app.activityInfo.packageName)) {
                    AppOption option = new AppOption();
                    option.packageName = app.activityInfo.packageName;
                    option.appName = app.loadLabel(pm).toString();
                    option.method = "direct";
                    appOptions.add(option);
                }
            }

            String[] appNames = new String[appOptions.size()];
            for (int i = 0; i < appOptions.size(); i++) {
                appNames[i] = appOptions.get(i).appName;
            }

            // Создаем чекбокс программно
            CheckBox rememberCheck = new CheckBox(this);
            rememberCheck.setText("Запомнить этот выбор");
            rememberCheck.setTextColor(Color.CYAN);
            rememberCheck.setPadding(40, 20, 20, 20);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Открыть PDF с помощью:\n" + // Два переноса для отступа
                            "• Хром только с сетью!!!")
                    .setAdapter(new ArrayAdapter<>(this,
                                    android.R.layout.simple_list_item_1, appNames),
                            (dialog, which) -> {
                                AppOption selected = appOptions.get(which);

                                if (rememberCheck.isChecked()) {
                                    // Запоминаем выбор
                                    prefs.edit()
                                            .putString(PREF_DEFAULT_PDF_APP, selected.packageName)
                                            .putString(PREF_DEFAULT_PDF_METHOD, selected.method)
                                            .apply();
                                    Toast.makeText(this, "Выбор запомнен", Toast.LENGTH_SHORT).show();
                                }

                                try {
                                    if (selected.method.equals("local")) {
                                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                                Uri.parse("http://localhost:" + serverPort + "/" + uniqueFileName));
                                        intent.setPackage(selected.packageName);
                                        startActivity(intent);
                                    } else {
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setDataAndType(fileUri, "application/pdf");
                                        intent.setPackage(selected.packageName);
                                        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                        startActivity(intent);
                                    }
                                } catch (Exception e) {
                                    Toast.makeText(this, "Не удалось открыть в выбранном приложении", Toast.LENGTH_SHORT).show();
                                }
                            })
                    .setNegativeButton("Отмена", null);

            AlertDialog dialog = builder.create();
            dialog.setView(rememberCheck, 50, 0, 50, 20);
            dialog.show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static class AppOption {
        String packageName;
        String appName;
        String method;
    }

    private boolean containsPackage(List<AppOption> list, String packageName) {
        for (AppOption opt : list) {
            if (opt.packageName.equals(packageName)) return true;
        }
        return false;
    }

    private boolean tryOpenWithBestApp(Uri fileUri, String mimeType, String extension) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            PackageManager pm = getPackageManager();
            List<ResolveInfo> appsList = pm.queryIntentActivities(intent, 0);

            if (appsList.isEmpty()) {
                return false;
            }

            List<String> priorityPackages = new ArrayList<>();

            if (mimeType.startsWith("image/")) {
                priorityPackages.add("com.google.android.apps.photos");
                priorityPackages.add("com.android.gallery3d");
                priorityPackages.add("com.miui.gallery");
                priorityPackages.add("com.sec.android.gallery3d");
                priorityPackages.add("com.huawei.photos");
            } else if (mimeType.equals("application/pdf")) {
                priorityPackages.add("com.google.android.apps.pdfviewer");
                priorityPackages.add("com.adobe.reader");
                priorityPackages.add("com.miui.pdfviewer");
                priorityPackages.add("com.sec.android.app.pdfviewer");
            } else if (mimeType.startsWith("text/")) {
                priorityPackages.add("com.google.android.apps.docs");
                priorityPackages.add("com.microsoft.office.word");
                priorityPackages.add("com.android.documentsui");
            }

            if (extension.equals("html") || extension.equals("htm") || extension.equals("pdf")) {
                priorityPackages.add("com.android.chrome");
                priorityPackages.add("com.yandex.browser");
                priorityPackages.add("org.mozilla.firefox");
                priorityPackages.add("com.microsoft.emmx");
                priorityPackages.add("com.opera.browser");
                priorityPackages.add("com.brave.browser");
            }

            for (String packageName : priorityPackages) {
                for (ResolveInfo resolveInfo : appsList) {
                    if (resolveInfo.activityInfo.packageName.equals(packageName)) {
                        try {
                            Intent specificIntent = new Intent(Intent.ACTION_VIEW);
                            specificIntent.setDataAndType(fileUri, mimeType);
                            specificIntent.setPackage(packageName);
                            specificIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            specificIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            startActivity(specificIntent);
                            Log.i("FileOpen", "Автоматически открыто в: " + packageName);
                            return true;
                        } catch (Exception e) {
                            Log.d("FileOpen", "Не удалось открыть в " + packageName + ": " + e.getMessage());
                        }
                    }
                }
            }

            for (ResolveInfo resolveInfo : appsList) {
                try {
                    Intent anyIntent = new Intent(Intent.ACTION_VIEW);
                    anyIntent.setDataAndType(fileUri, mimeType);
                    anyIntent.setPackage(resolveInfo.activityInfo.packageName);
                    anyIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    anyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    startActivity(anyIntent);
                    Log.i("FileOpen", "Автоматически открыто в: " + resolveInfo.activityInfo.packageName);
                    return true;
                } catch (Exception e) {
                    // Пробуем следующее
                }
            }

            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void showAppChooserDialog(Uri fileUri, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PackageManager pm = getPackageManager();
            List<ResolveInfo> appsList = pm.queryIntentActivities(intent, 0);

            if (appsList.isEmpty()) {
                Intent genericIntent = new Intent(Intent.ACTION_VIEW);
                genericIntent.setData(fileUri);
                genericIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(genericIntent, "Выберите приложение");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(chooser);
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Открыть с помощью");

            List<String> appNames = new ArrayList<>();
            List<String> packageNames = new ArrayList<>();

            for (ResolveInfo resolveInfo : appsList) {
                String appName = resolveInfo.loadLabel(pm).toString();
                String packageName = resolveInfo.activityInfo.packageName;
                appNames.add(appName);
                packageNames.add(packageName);
            }

            AlertDialog.Builder builderCompat = new AlertDialog.Builder(MainActivity.this);
            builderCompat.setTitle("Открыть с помощью");

            String[] appNamesArray = appNames.toArray(new String[0]);

            builderCompat.setItems(appNamesArray, (dialog, which) -> {
                try {
                    Intent selectedIntent = new Intent(Intent.ACTION_VIEW);
                    selectedIntent.setDataAndType(fileUri, mimeType);
                    selectedIntent.setPackage(packageNames.get(which));
                    selectedIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    selectedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(selectedIntent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Не удалось открыть в выбранном приложении",
                            Toast.LENGTH_SHORT).show();
                    trySystemChooser(fileUri, mimeType);
                }
            });

            builderCompat.setNegativeButton("Отмена", null);
            builderCompat.show();

        } catch (Exception e) {
            e.printStackTrace();
            trySystemChooser(fileUri, mimeType);
        }
    }

    private void trySystemChooser(Uri fileUri, String mimeType) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Выберите приложение");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(MainActivity.this,
                    "Не удалось открыть файл",
                    Toast.LENGTH_LONG).show();
        }
    }

    private String getMimeTypeFallback(String extension) {
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "txt":
                return "text/plain";
            case "html":
            case "htm":
                return "text/html";
            case "doc":
            case "docx":
                return "application/msword";
            case "xls":
            case "xlsx":
                return "application/vnd.ms-excel";
            case "ppt":
            case "pptx":
                return "application/vnd.ms-powerpoint";
            case "mp4":
                return "video/mp4";
            case "mp3":
                return "audio/mpeg";
            default:
                return "*/*";
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

    private void checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, MANAGE_STORAGE_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        MANAGE_STORAGE_CODE);
            }
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

            if (webView != null) {
                String js = "javascript:onScanResult('"
                        + (barcode != null ? barcode.replace("'", "\\'") : "")
                        + "', '"
                        + (lastFoundFilePath != null ? lastFoundFilePath.replace("'", "\\'") : "")
                        + "')";
                webView.evaluateJavascript(js, null);
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
        if (localServer != null) {
            localServer.stop();
        }
        super.onDestroy();
    }
}