package com.skb.aiplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;

/**
 * Experimental local WebView touch actuator.
 * Delivers MotionEvents to this application's own WebView only.
 * No OS-wide input injection, virtual Xbox gamepad, or guaranteed cloud-game support.
 */
public final class TouchAgent {
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private int steps;
    private float targetX = .5f, targetY = .5f;
    private Runnable listener;
    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (steps >= 120) { stop(); return; }
            tap(targetX, targetY);
            steps++;
            if (listener != null) listener.run();
            handler.postDelayed(this, 1200);
        }
    };

    public TouchAgent(WebView web) { this.web = web; }
    public void setTarget(float x, float y) {
        targetX = Math.max(.05f, Math.min(.95f, x));
        targetY = Math.max(.05f, Math.min(.95f, y));
    }
    public void setListener(Runnable listener) { this.listener = listener; }
    public int steps() { return steps; }
    public boolean isActive() { return active; }
    public void start() {
        stop();
        steps = 0;
        active = true;
        handler.post(loop);
    }
    public void stop() {
        active = false;
        handler.removeCallbacks(loop);
        if (listener != null) listener.run();
    }
    public void tap(float normalizedX, float normalizedY) {
        if (web.getWidth() == 0 || web.getHeight() == 0) return;
        float x = normalizedX * web.getWidth();
        float y = normalizedY * web.getHeight();
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 80,
                MotionEvent.ACTION_UP, x, y, 0);
        try {
            web.dispatchTouchEvent(down);
            web.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
    }
}
