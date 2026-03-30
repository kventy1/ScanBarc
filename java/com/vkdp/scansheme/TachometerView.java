package com.vkdp.scansheme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class TachometerView extends View {

    private Paint scalePaint, markPaint, needlePaint, centerPaint, textPaint;
    private float needleAngle = 0;
    private float targetAngle = 0;
    private final Random random = new Random();
    private long lastPauseTime = 0;
    private boolean isPaused = false;
    private long pauseStartTime = 0;

    private static final int PAUSE_INTERVAL = 10000;
    private static final int PAUSE_DURATION = 3000;
    private static final int NEEDLE_SPEED = 3;
    private static final int CHANGE_TARGET_CHANCE = 5;

    public TachometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        scalePaint = new Paint();
        scalePaint.setColor(Color.argb(200, 0, 0, 0));
        scalePaint.setStyle(Paint.Style.STROKE);
        scalePaint.setStrokeWidth(14);

        markPaint = new Paint();
        markPaint.setColor(Color.GREEN);
        markPaint.setStrokeWidth(6);

        needlePaint = new Paint();
        needlePaint.setColor(Color.RED);
        needlePaint.setStrokeWidth(10);
        needlePaint.setStyle(Paint.Style.STROKE);

        centerPaint = new Paint();
        centerPaint.setColor(Color.YELLOW);

        textPaint = new Paint();
        textPaint.setColor(Color.argb(200, 0, 200, 255));
        textPaint.setTextSize(40);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float centerX = getWidth() / 2;
        float centerY = getHeight() / 2;
        float radius = Math.min(getWidth(), getHeight()) / 2 - 20;

        // Рисуем шкалу (полукруг)
        RectF rect = new RectF(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
        canvas.drawArc(rect, 180, 180, false, scalePaint);

        // Рисуем метки
        for (int i = 0; i <= 30; i++) {
            double angle = Math.toRadians(180 + i * 6);
            float startX = centerX + (float)(radius * Math.cos(angle));
            float startY = centerY + (float)(radius * Math.sin(angle));
            float endX = centerX + (float)((radius - 20) * Math.cos(angle));
            float endY = centerY + (float)((radius - 20) * Math.sin(angle));
            canvas.drawLine(startX, startY, endX, endY, markPaint);
        }

        // Анимация стрелки
        updateNeedle();

        double needleRad = Math.toRadians(180 + needleAngle);
        float needleX = centerX + (float)((radius - 10) * Math.cos(needleRad));
        float needleY = centerY + (float)((radius - 10) * Math.sin(needleRad));
        canvas.drawLine(centerX, centerY, needleX, needleY, needlePaint);

        canvas.drawCircle(centerX, centerY, 25, centerPaint);
        canvas.drawText("\u00A0\u00A0ОГОНЬ", centerX, centerY + radius - 20, textPaint);

        invalidate();
    }

    private void updateNeedle() {
        long currentTime = System.currentTimeMillis();

        if (!isPaused && currentTime - lastPauseTime > PAUSE_INTERVAL) {
            isPaused = true;
            pauseStartTime = currentTime;
        }

        if (isPaused && currentTime - pauseStartTime > PAUSE_DURATION) {
            isPaused = false;
            lastPauseTime = currentTime;
        }

        if (!isPaused) {
            if (random.nextInt(100) < CHANGE_TARGET_CHANCE) {
                targetAngle = random.nextInt(180);
            }

            if (needleAngle < targetAngle) {
                needleAngle += NEEDLE_SPEED;
                if (needleAngle > targetAngle) needleAngle = targetAngle;
            } else if (needleAngle > targetAngle) {
                needleAngle -= NEEDLE_SPEED;
                if (needleAngle < targetAngle) needleAngle = targetAngle;
            }
        }
    }
}