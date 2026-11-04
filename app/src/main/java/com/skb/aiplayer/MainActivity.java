package com.skb.aiplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.MotionEvent;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Browser compatibility probe; does not inject controller input. */
public final class MainActivity extends Activity {
    private WebView browser;
    private TextView status;
    private LinearLayout toolbar;
    private boolean fullscreen = false;
    private TouchAgent touchAgent;
    private boolean calibrating = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 16, 22));
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("SKB B1 | Browser compatibility prototype — no AI controller connected");
        toolbar.addView(status);
        LinearLayout bar = new LinearLayout(this);
        Button load = new Button(this); load.setText("Open Xbox Cloud");
        Button test = new Button(this); test.setText("Check gamepad API");
        Button fullscreenButton = new Button(this); fullscreenButton.setText("Fullscreen");
        Button labButton = new Button(this); labButton.setText("AI Lab");
        Button calibrate = new Button(this); calibrate.setText("Set Touch");
        Button auto = new Button(this); auto.setText("Auto Tap");
        Button stop = new Button(this); stop.setText("STOP");
        bar.addView(load); bar.addView(test); bar.addView(fullscreenButton); bar.addView(labButton);
        LinearLayout agentBar = new LinearLayout(this);
        agentBar.addView(calibrate); agentBar.addView(auto); agentBar.addView(stop);
        toolbar.addView(agentBar);
        toolbar.addView(bar);
        root.addView(toolbar);
        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.setWebChromeClient(new WebChromeClient());
        browser.setWebViewClient(new WebViewClient());
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        touchAgent = new TouchAgent(browser);
        touchAgent.setListener(() -> status.setText(touchAgent.isActive()
            ? "Touch test active: " + touchAgent.steps() + "/120 (1 tap/1.2s)"
            : "Touch test stopped. This is not an Xbox virtual controller."));
        calibrate.setOnClickListener(v -> {
            touchAgent.stop();
            calibrating = true;
            status.setText("Tap the desired location inside the WebView once to calibrate.");
        });
        browser.setOnTouchListener((v, event) -> {
            if (!calibrating) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                touchAgent.setTarget(event.getX() / Math.max(1, browser.getWidth()),
                    event.getY() / Math.max(1, browser.getHeight()));
                calibrating = false;
                status.setText("Touch target saved. Press Auto Tap to test.");
            }
            return true;
        });
        auto.setOnClickListener(v -> {
            calibrating = false;
            touchAgent.start();
        });
        stop.setOnClickListener(v -> touchAgent.stop());
        load.setOnClickListener(v -> browser.loadUrl("https://www.xbox.com/play"));
        test.setOnClickListener(v -> browser.evaluateJavascript(
            "(function(){return JSON.stringify({url:location.origin,gamepadAPI:!!navigator.getGamepads,connected:navigator.getGamepads?Array.from(navigator.getGamepads()).filter(Boolean).length:0})})()",
            result -> status.setText("Gamepad diagnostic: " + result)));
        fullscreenButton.setOnClickListener(v -> setFullscreen(true));
        labButton.setOnClickListener(v -> startActivity(new Intent(this, SimulatorActivity.class)));
    }

    private void setFullscreen(boolean enable) {
        fullscreen = enable;
        toolbar.setVisibility(enable ? View.GONE : View.VISIBLE);
        if (enable) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        browser.requestFocus();
    }

    @Override public void onBackPressed() {
        if (touchAgent != null && touchAgent.isActive()) { touchAgent.stop(); return; }
        if (fullscreen) setFullscreen(false);
        else if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (touchAgent != null) touchAgent.stop();
        browser.destroy();
        super.onDestroy();
    }
}
