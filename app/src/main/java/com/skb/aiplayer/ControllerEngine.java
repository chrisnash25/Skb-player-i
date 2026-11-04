package com.skb.aiplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;

/**
 * Local 60 Hz controller state engine. This produces controller states, not
 * a virtual Android input device. A real bridge transport must be attached
 * before any Xbox-compatible gamepad events can reach a game.
 */
public final class ControllerEngine {
    public interface Sink {
        void send(float lx, float ly, float rx, float ry, float lt, float rt, int buttons);
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Sink sink;
    private boolean active;
    private long lastCommand;
    private float lx, ly, rx, ry, lt, rt;
    private int buttons;
    private static final long WATCHDOG_MS = 500;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (SystemClock.elapsedRealtime() - lastCommand > WATCHDOG_MS) neutral();
            if (sink != null) sink.send(lx,ly,rx,ry,lt,rt,buttons);
            handler.postDelayed(this,16);
        }
    };
    public void setSink(Sink sink) { this.sink = sink; }
    public void start() {
        stop(); active=true; lastCommand=SystemClock.elapsedRealtime(); handler.post(tick);
    }
    public void stop() {
        active=false; handler.removeCallbacks(tick); neutral();
        if (sink != null) sink.send(0,0,0,0,0,0,0);
    }
    public boolean isActive() { return active; }
    private void neutral() { lx=ly=rx=ry=lt=rt=0; buttons=0; }
    private static float axis(double v) {
        if (!Double.isFinite(v)) return 0;
        return (float)Math.max(-1,Math.min(1,v));
    }
    private static float trigger(double v) {
        if (!Double.isFinite(v)) return 0;
        return (float)Math.max(0,Math.min(1,v));
    }
    /** Explicit state packet. Values are clamped and expire after 500ms. */
    public void command(JSONObject state) {
        if (!active || state==null) return;
        lx=axis(state.optDouble("lx",0)); ly=axis(state.optDouble("ly",0));
        rx=axis(state.optDouble("rx",0)); ry=axis(state.optDouble("ry",0));
        lt=trigger(state.optDouble("lt",0)); rt=trigger(state.optDouble("rt",0));
        buttons=state.optInt("buttons",0) & 0xffff;
        lastCommand=SystemClock.elapsedRealtime();
    }
}
