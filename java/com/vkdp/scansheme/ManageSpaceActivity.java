package com.vkdp.scansheme; // ЗАМЕНИ НА СВОЙ ПАКЕТ (сверху в манифесте написан)

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ManageSpaceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Создаем простейший вид прямо в коде, чтобы не делать XML-файл
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 50, 50, 50);

        TextView text = new TextView(this);
        text.setText("Внимание! Ручная очистка может повредить данные (10 ГБ).");
        layout.addView(text);

        Button btn = new Button(this);
        btn.setText("ПОНЯТНО, НЕ БУДУ");
        btn.setOnClickListener(v -> finish()); // Просто закрывает окно
        layout.addView(btn);

        setContentView(layout);
    }
}