package com.streamtv.webview;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;

public class TvCursorView extends View {
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cursorX;
    private float cursorY;
    private float targetX;
    private float targetY;
    private float sizeScale = 1f;
    private long animationDuration = 110;
    private ValueAnimator animator;
    private Runnable positionListener;
    private Runnable idleListener;
    private final Runnable hidePointer = () -> {
        animate().alpha(0f).setDuration(280).start();
        if (idleListener != null) idleListener.run();
    };

    public TvCursorView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        shadow.setColor(0x66000000);
        fill.setColor(Color.WHITE);
        ring.setColor(0xff3b82f6);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(4));
    }

    public void moveTo(float x, float y) {
        cursorX = Math.max(dp(18), Math.min(getWidth() - dp(18), x));
        cursorY = Math.max(dp(18), Math.min(getHeight() - dp(18), y));
        targetX = cursorX;
        targetY = cursorY;
        invalidate();
        scheduleAutoHide();
    }

    public void smoothMoveBy(float dx, float dy) {
        reveal();
        targetX = Math.max(dp(18), Math.min(getWidth() - dp(18), targetX + dx));
        targetY = Math.max(dp(18), Math.min(getHeight() - dp(18), targetY + dy));
        final float startX = cursorX, startY = cursorY, endX = targetX, endY = targetY;
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(animationDuration);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            cursorX = startX + (endX - startX) * progress;
            cursorY = startY + (endY - startY) * progress;
            invalidate();
            if (positionListener != null) positionListener.run();
        });
        animator.start();
        scheduleAutoHide();
    }

    public void reveal() {
        removeCallbacks(hidePointer);
        animate().cancel();
        setAlpha(1f);
        setVisibility(VISIBLE);
        scheduleAutoHide();
    }

    private void scheduleAutoHide() {
        removeCallbacks(hidePointer);
        postDelayed(hidePointer, 2400);
    }

    public void setPointerScale(float scale) { sizeScale = Math.max(.65f, Math.min(1.8f, scale)); invalidate(); }
    public void setSmoothness(int value) { animationDuration = 45 + Math.max(0, Math.min(100, value)) * 2L; }
    public void setStyle(int style) {
        if (style == 1) { fill.setColor(0xff111827); ring.setColor(0xffffc857); }
        else if (style == 2) { fill.setColor(0xff58e6a9); ring.setColor(Color.WHITE); }
        else { fill.setColor(Color.WHITE); ring.setColor(0xff3b82f6); }
        invalidate();
    }
    public void center() { moveTo(getWidth()/2f, getHeight()/2f); reveal(); }
    public void setPositionListener(Runnable listener) { positionListener = listener; }
    public void setIdleListener(Runnable listener) { idleListener = listener; }
    public float getTargetX() { return targetX; }
    public float getTargetY() { return targetY; }

    public float getCursorX() { return cursorX; }
    public float getCursorY() { return cursorY; }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (cursorX == 0 && cursorY == 0) moveTo(width / 2f, height / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = dp(11) * sizeScale;
        canvas.drawCircle(cursorX + dp(3), cursorY + dp(4), radius + dp(3), shadow);
        canvas.drawCircle(cursorX, cursorY, radius, fill);
        canvas.drawCircle(cursorX, cursorY, radius + dp(3), ring);
        canvas.drawCircle(cursorX, cursorY, dp(3), ring);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
