package com.skb.aiplayer;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;
import org.json.JSONObject;

/**
 * Experimental opt-in observe -> plan -> act loop limited to this WebView.
 * Does NOT emulate a hardware Xbox controller. No reliable protected video capture.
 */
public final class VisualAgent {
    public interface Status { void update(String message); }
    private final WebView web;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Status status;
    private boolean running;
    private int cycles;
    private int failures;
    private String key;
    private long started;
    private static final int MAX_CYCLES = 30;
    private static final long MAX_RUNTIME_MS = 180000;
    private final String prompt = "You are a cautious visual interaction planner for a game screen. "
        + "Return ONLY a JSON object with keys action, x, y, x2, y2, duration_ms, reason. "
        + "action must be one of wait, tap, swipe, hold, stop. "
        + "Coordinates are normalized 0 to 1 across the entire image. "
        + "Choose one short, safe action based strictly on visible information. "
        + "If screen is blank, video is not visible, a login or payment screen is shown, "
        + "or you cannot identify an appropriate game interaction, choose stop. "
        + "Do not guess controls, buy anything, or interact with account settings. "
        + "No markdown or extra prose.";

    public VisualAgent(WebView web, Status status) { this.web=web; this.status=status; }
    public boolean isRunning() { return running; }
    public void start(String apiKey) {
        stop();
        if (apiKey == null || apiKey.trim().isEmpty()) { status.update("Enter Gemini key first."); return; }
        key=apiKey; running=true; cycles=0; failures=0; started=SystemClock.elapsedRealtime();
        status.update("Visual agent started (max 30 decisions / 3 minutes).");
        observe();
    }
    public void stop() {
        running=false;
        handler.removeCallbacksAndMessages(null);
        status.update("Visual agent stopped.");
    }
    private void observe() {
        if (!running) return;
        if (cycles >= MAX_CYCLES || SystemClock.elapsedRealtime()-started > MAX_RUNTIME_MS) {
            stop(); status.update("Visual agent safety limit reached."); return;
        }
        cycles++;
        status.update("Gemini observing frame " + cycles + "/" + MAX_CYCLES);
        GeminiVision.analyze(web,key,prompt,result -> {
            if (!running) return;
            try {
                String clean = result.trim();
                if (clean.startsWith("```")) {
                    clean=clean.replace("```json","").replace("```","").trim();
                }
                JSONObject plan=new JSONObject(clean);
                String action=plan.optString("action","stop").toLowerCase(java.util.Locale.ROOT);
                String reason=plan.optString("reason","");
                if (action.equals("stop")) { stop(); status.update("AI stopped: "+reason); return; }
                if (!action.equals("wait") && !action.equals("tap")
                    && !action.equals("swipe") && !action.equals("hold")) {
                    throw new IllegalArgumentException("Invalid action");
                }
                double x=plan.optDouble("x",Double.NaN),y=plan.optDouble("y",Double.NaN);
                double x2=plan.optDouble("x2",Double.NaN),y2=plan.optDouble("y2",Double.NaN);
                int duration=Math.max(60,Math.min(700,plan.optInt("duration_ms",250)));
                if (!action.equals("wait")) {
                    if (!valid(x)||!valid(y)) throw new IllegalArgumentException("Invalid coordinate");
                    if (action.equals("swipe") && (!valid(x2)||!valid(y2)))
                        throw new IllegalArgumentException("Invalid swipe target");
                    gesture(action,(float)x,(float)y,(float)x2,(float)y2,duration);
                }
                failures=0;
                status.update("AI decision "+cycles+": "+action+" — "+reason);
                handler.postDelayed(this::observe,1800);
            } catch (Exception e) {
                failures++;
                status.update("AI response rejected ("+failures+"): "+result.substring(0,Math.min(100,result.length())));
                if (failures>=2) stop();
                else handler.postDelayed(this::observe,2500);
            }
        });
    }
    private boolean valid(double v) { return !Double.isNaN(v) && v>=0.02 && v<=0.98; }
    private void gesture(String action,float nx,float ny,float nx2,float ny2,int duration) {
        if (web.getWidth()<1 || web.getHeight()<1) return;
        float x=nx*web.getWidth(),y=ny*web.getHeight();
        float tx=action.equals("swipe")?nx2*web.getWidth():x;
        float ty=action.equals("swipe")?ny2*web.getHeight():y;
        long t=SystemClock.uptimeMillis();
        send(t,t,MotionEvent.ACTION_DOWN,x,y);
        if (action.equals("swipe")) {
            for(int i=1;i<=5;i++) {
                final int step=i;
                handler.postDelayed(() -> {
                    float p=step/5f;
                    send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x+(tx-x)*p,y+(ty-y)*p);
                }, duration*i/5);
            }
        }
        handler.postDelayed(() -> send(t,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,tx,ty), duration);
    }
    private void send(long down,long time,int action,float x,float y) {
        MotionEvent event=MotionEvent.obtain(down,time,action,x,y,0);
        try { web.dispatchTouchEvent(event); } finally { event.recycle(); }
    }
}
