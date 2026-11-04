package com.skb.aiplayer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayDeque;
import java.util.Arrays;

/** Offline, autonomous grid-navigation sandbox. No external game input. */
public final class SimulatorActivity extends Activity {
    private static final int W = 12, H = 7;
    private final boolean[][] blocked = new boolean[H][W];
    private int x, y, moves;
    private boolean running;
    private TextView telemetry;
    private Arena arena;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            step();
            if (running) handler.postDelayed(this, 350);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(13, 17, 25));
        telemetry = new TextView(this);
        telemetry.setTextColor(Color.WHITE);
        telemetry.setTextSize(16);
        root.addView(telemetry);
        LinearLayout controls = new LinearLayout(this);
        Button start = new Button(this); start.setText("Start / Resume");
        Button pause = new Button(this); pause.setText("Pause");
        Button reset = new Button(this); reset.setText("Reset");
        Button back = new Button(this); back.setText("Browser");
        controls.addView(start); controls.addView(pause); controls.addView(reset); controls.addView(back);
        root.addView(controls);
        arena = new Arena();
        root.addView(arena, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        reset();
        start.setOnClickListener(v -> {
            if (x == W - 1 && y == H - 1) reset();
            if (!running) { running = true; update("Autonomous navigation"); handler.post(tick); }
        });
        pause.setOnClickListener(v -> { stop(); update("Paused"); });
        reset.setOnClickListener(v -> reset());
        back.setOnClickListener(v -> finish());
    }

    private void stop() { running = false; handler.removeCallbacks(tick); }
    private void reset() {
        stop();
        for (boolean[] row : blocked) Arrays.fill(row, false);
        int[][] walls = {{2,0},{2,1},{2,2},{4,2},{4,3},{4,4},{6,0},{6,1},{6,2},{8,3},{8,4},{8,5},{10,1},{10,2}};
        for (int[] wall : walls) blocked[wall[1]][wall[0]] = true;
        x = 0; y = 0; moves = 0;
        update("Ready — offline simulation only");
    }
    private void update(String mode) {
        telemetry.setText("SKB Autonomous Lab | " + mode + " | steps " + moves
                + " | position " + x + "," + y + " | goal 11,6");
        if (arena != null) arena.invalidate();
    }
    private void step() {
        if (x == W - 1 && y == H - 1) { stop(); update("Goal reached"); return; }
        int[][] distance = new int[H][W];
        for (int[] row : distance) Arrays.fill(row, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        distance[H-1][W-1] = 0;
        queue.add(new int[]{W-1,H-1});
        int[][] directions = {{1,0},{0,1},{-1,0},{0,-1}};
        while (!queue.isEmpty()) {
            int[] p = queue.removeFirst();
            for (int[] d : directions) {
                int nx = p[0]+d[0], ny = p[1]+d[1];
                if (nx>=0 && nx<W && ny>=0 && ny<H && !blocked[ny][nx] && distance[ny][nx]<0) {
                    distance[ny][nx] = distance[p[1]][p[0]]+1;
                    queue.addLast(new int[]{nx,ny});
                }
            }
        }
        if (distance[y][x] < 0) { stop(); update("No valid path"); return; }
        for (int[] d : directions) {
            int nx=x+d[0], ny=y+d[1];
            if (nx>=0 && nx<W && ny>=0 && ny<H && distance[ny][nx]>=0
                    && distance[ny][nx]<distance[y][x]) {
                x=nx; y=ny; moves++;
                if (x==W-1 && y==H-1) { stop(); update("Goal reached"); }
                else update("Autonomous navigation");
                return;
            }
        }
        stop(); update("Path stalled");
    }
    @Override protected void onPause() { super.onPause(); stop(); }
    @Override protected void onDestroy() { stop(); super.onDestroy(); }

    private final class Arena extends View {
        private final Paint paint = new Paint(3);
        Arena() { super(SimulatorActivity.this); }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cell = Math.min(getWidth()/(float)W, getHeight()/(float)H);
            float ox=(getWidth()-cell*W)/2, oy=(getHeight()-cell*H)/2;
            for (int row=0;row<H;row++) for (int col=0;col<W;col++) {
                int color = blocked[row][col] ? 0xff555f73 : 0xff1d293a;
                if (col==W-1 && row==H-1) color=0xff2c936b;
                if (col==x && row==y) color=0xff5fc9ff;
                paint.setColor(color);
                c.drawRect(ox+col*cell+2,oy+row*cell+2,ox+(col+1)*cell-2,oy+(row+1)*cell-2,paint);
            }
        }
    }
}
