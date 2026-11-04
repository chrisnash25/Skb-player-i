package com.skb.aiplayer;

import android.app.Activity;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ScrollView;
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
    private String geminiKey = "";
    private boolean visionBusy = false;
    private VisualAgent visualAgent;

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
        Button keyButton = new Button(this); keyButton.setText("Gemini Key");
        Button visionButton = new Button(this); visionButton.setText("Analyze Screen");
        Button aiStart = new Button(this); aiStart.setText("Start Visual AI");
        Button aiStop = new Button(this); aiStop.setText("STOP AI");
        bar.addView(load); bar.addView(test); bar.addView(fullscreenButton); bar.addView(labButton);
        LinearLayout agentBar = new LinearLayout(this);
        agentBar.addView(calibrate); agentBar.addView(auto); agentBar.addView(stop);
        toolbar.addView(agentBar);
        LinearLayout visionBar = new LinearLayout(this);
        visionBar.addView(keyButton); visionBar.addView(visionButton);
        LinearLayout aiBar = new LinearLayout(this);
        aiBar.addView(aiStart); aiBar.addView(aiStop);
        toolbar.addView(aiBar);
        toolbar.addView(visionBar);
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
        visualAgent = new VisualAgent(browser, message -> status.setText(message));
        aiStart.setOnClickListener(v -> {
            if (geminiKey.isEmpty()) { status.setText("Set Gemini key first."); return; }
            new AlertDialog.Builder(this).setTitle("Start experimental visual agent?")
                .setMessage("Gemini will repeatedly receive screenshots and select touch gestures for up to 30 decisions or 3 minutes. This can consume API quota. Streaming video may not be captured. It is not an Xbox gamepad. STOP AI ends the session.")
                .setPositiveButton("Start", (dialog, which) -> {
                    touchAgent.stop();
                    visualAgent.start(geminiKey);
                }).setNegativeButton("Cancel",null).show();
        });
        aiStop.setOnClickListener(v -> { visualAgent.stop(); touchAgent.stop(); });
        geminiKey = getPreferences(MODE_PRIVATE).getString("gemini_key", "");
        keyButton.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            input.setHint("Paste your Gemini API key");
            input.setText(geminiKey);
            new AlertDialog.Builder(this).setTitle("Gemini API key (stored on this device)")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    geminiKey = input.getText().toString().trim();
                    getPreferences(MODE_PRIVATE).edit().putString("gemini_key", geminiKey).apply();
                    status.setText("Gemini key saved locally. Tap Analyze Screen to send one screenshot.");
                })
                .setNeutralButton("Delete", (dialog, which) -> {
                    geminiKey = "";
                    getPreferences(MODE_PRIVATE).edit().remove("gemini_key").apply();
                    status.setText("Gemini key deleted from app preferences.");
                })
                .setNegativeButton("Cancel", null).show();
        });
        visionButton.setOnClickListener(v -> {
            if (visionBusy) return;
            if (geminiKey.isEmpty()) { status.setText("Set your Gemini key first."); return; }
            new AlertDialog.Builder(this).setTitle("Send screenshot to Gemini?")
                .setMessage("This sends one screenshot of the WebView to Google's Gemini API. Video may appear black. Do not send private account details.")
                .setPositiveButton("Analyze", (dialog, which) -> {
                    visionBusy = true;
                    status.setText("Gemini is analyzing one captured frame...");
                    GeminiVision.analyze(browser, geminiKey, result -> {
                        visionBusy = false;
                        new AlertDialog.Builder(this).setTitle("Gemini visual analysis")
                            .setMessage(result).setPositiveButton("OK", null).show();
                        status.setText("Visual analysis finished. No gameplay input was sent.");
                    });
                }).setNegativeButton("Cancel",null).show();
        });
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
        if (visualAgent != null && visualAgent.isRunning()) { visualAgent.stop(); return; }
        if (touchAgent != null && touchAgent.isActive()) { touchAgent.stop(); return; }
        if (fullscreen) setFullscreen(false);
        else if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (visualAgent != null) visualAgent.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (visualAgent != null) visualAgent.stop();
        if (touchAgent != null) touchAgent.stop();
        browser.destroy();
        super.onDestroy();
    }
}
