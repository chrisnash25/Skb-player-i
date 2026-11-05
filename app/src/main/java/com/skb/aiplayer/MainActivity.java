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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.ImageView;
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
    private ScrollView commandScroll;
    private TextView gameHeading;
    private LinearLayout gameControls, aiControls, bleControls;
    private boolean fullscreen = false;
    private android.widget.FrameLayout gameFrame;
    private View videoFullscreenView;
    private WebChromeClient.CustomViewCallback videoFullscreenCallback;
    private TouchAgent touchAgent;
    private boolean calibrating = false;
    private String geminiKey = "";
    private boolean visionBusy = false;
    private VisualAgent visualAgent;
    private Button floatingStop;
    private ControllerEngine controllerEngine;
    private BleControllerBridge bleBridge;
    private static final int BLE_PERMISSION_REQUEST = 4102;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12, 16, 22));
        commandScroll = new ScrollView(this);
        commandScroll.setBackgroundColor(Color.rgb(20, 26, 34));
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("SKB AI PLAYER PRO | COMMAND CENTRE\nBluetooth: disconnected | Game: not loaded");
        status.setPadding(dp(14), dp(12), dp(14), dp(12));
        status.setTextSize(13);
        status.setBackgroundColor(Color.rgb(25, 36, 49));
        TextView brandHeading = new TextView(this);
        brandHeading.setText("SKB  /  AI PLAYER PRO");
        brandHeading.setTextColor(Color.rgb(174, 224, 255));
        brandHeading.setTextSize(22);
        brandHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        brandHeading.setPadding(dp(14), dp(12), dp(14), dp(5));
        toolbar.addView(brandHeading);
        LinearLayout nav = new LinearLayout(this);
        Button gameTab = new Button(this); gameTab.setText("GAME");
        Button aiTab = new Button(this); aiTab.setText("AI");
        Button controllerTab = new Button(this); controllerTab.setText("BLE");
        Button toolsTab = new Button(this); toolsTab.setText("LAB");
        nav.addView(gameTab); nav.addView(aiTab); nav.addView(controllerTab); nav.addView(toolsTab);
        toolbar.addView(horizontalControls(nav));
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
        Button previewCapture = new Button(this); previewCapture.setText("Verify Game Vision");
        Button bridgeStatus = new Button(this); bridgeStatus.setText("Connect SKB BLE");
        Button bridgeTest = new Button(this); bridgeTest.setText("Test BLE input");
        bar.addView(load); bar.addView(test); bar.addView(fullscreenButton); bar.addView(labButton);
        LinearLayout agentBar = new LinearLayout(this);
        agentBar.addView(calibrate); agentBar.addView(auto); agentBar.addView(stop);
        gameControls = new LinearLayout(this);
        gameControls.setOrientation(LinearLayout.VERTICAL);
        gameControls.addView(horizontalControls(agentBar));
        LinearLayout visionBar = new LinearLayout(this);
        visionBar.addView(keyButton); visionBar.addView(visionButton);
        LinearLayout aiBar = new LinearLayout(this);
        aiBar.addView(aiStart); aiBar.addView(aiStop);
        aiBar.addView(previewCapture);
        LinearLayout bridgeBar = new LinearLayout(this);
        bridgeBar.addView(bridgeStatus);
        bridgeBar.addView(bridgeTest);
        aiControls = new LinearLayout(this);
        aiControls.setOrientation(LinearLayout.VERTICAL);
        aiControls.addView(horizontalControls(aiBar));
        aiControls.addView(horizontalControls(visionBar));
        gameControls.addView(horizontalControls(bar));
        toolbar.addView(gameControls);
        toolbar.addView(aiControls);
        bleControls = new LinearLayout(this);
        bleControls.setOrientation(LinearLayout.VERTICAL);
        bleControls.addView(horizontalControls(bridgeBar));
        toolbar.addView(bleControls);
        bleControls.setVisibility(View.GONE);
        aiControls.setVisibility(View.GONE);
        commandScroll.addView(toolbar);
        root.addView(commandScroll, new LinearLayout.LayoutParams(-1, dp(248)));
        gameHeading = new TextView(this);
        gameHeading.setText("LIVE GAME VIEW   •   Xbox Cloud Gaming   •   Not loaded");
        gameHeading.setTextColor(Color.rgb(174, 224, 255));
        gameHeading.setTextSize(12);
        gameHeading.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(gameHeading);
        browser = new WebView(this);
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (videoFullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                videoFullscreenView = view;
                videoFullscreenCallback = callback;
                gameFrame.addView(view, new android.widget.FrameLayout.LayoutParams(-1, -1));
                browser.setVisibility(View.INVISIBLE);
                setFullscreen(true);
            }
            @Override public void onHideCustomView() {
                closeVideoFullscreen();
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                gameHeading.setText("LIVE GAME VIEW   •   Loading Xbox Cloud Gaming");
            }
            @Override public void onPageFinished(WebView view, String url) {
                gameHeading.setText("LIVE GAME VIEW   •   Browser page loaded (stream not verified)");
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                    android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) gameHeading.setText("LIVE GAME VIEW   •   Page failed to load");
            }
        });
        gameFrame = new android.widget.FrameLayout(this);
        gameFrame.setClipChildren(true);
        gameFrame.setClipToPadding(true);
        gameFrame.addView(browser, new android.widget.FrameLayout.LayoutParams(-1,-1));
        floatingStop = new Button(this);
        floatingStop.setText("STOP AI");
        floatingStop.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams stopParams = new android.widget.FrameLayout.LayoutParams(
            -2,-2,android.view.Gravity.TOP | android.view.Gravity.RIGHT);
        gameFrame.addView(floatingStop,stopParams);
        root.addView(gameFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        touchAgent = new TouchAgent(browser);
        visualAgent = new VisualAgent(browser, message -> status.setText(message));
        controllerEngine = new ControllerEngine();
        bleBridge = new BleControllerBridge(this, message -> status.setText(message));
        controllerEngine.setSink(bleBridge);
        bridgeTest.setOnClickListener(v -> {
            if (!bleBridge.isReady()) { status.setText("Connect SKB BLE first"); return; }
            controllerEngine.start();
            try { controllerEngine.command(new org.json.JSONObject().put("buttons",1)); }
            catch (org.json.JSONException ignored) { }
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> controllerEngine.stop(), 250);
            status.setText("Sent short A-button test; verify gamepad response on Android");
        });
        aiStart.setOnClickListener(v -> {
            if (geminiKey.isEmpty()) { status.setText("Set Gemini key first."); return; }
            new AlertDialog.Builder(this).setTitle("Start experimental visual agent?")
                .setMessage("Gemini will repeatedly receive screenshots and select touch gestures for up to 120 decisions or 10 minutes, subject to rate limits. This consumes API quota. Streaming video may not be captured. It is not an Xbox gamepad. STOP AI ends the session.")
                .setPositiveButton("Start", (dialog, which) -> {
                    touchAgent.stop();
                    visualAgent.start(geminiKey);
                }).setNegativeButton("Cancel",null).show();
        });
        aiStop.setOnClickListener(v -> { visualAgent.stop(); touchAgent.stop(); });
        bridgeStatus.setOnClickListener(v -> {
            if (!bleBridge.hasPermission()) {
                if (android.os.Build.VERSION.SDK_INT >= 31)
                    requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT}, BLE_PERMISSION_REQUEST);
                else requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, BLE_PERMISSION_REQUEST);
            } else bleBridge.connect();
        });
        floatingStop.setOnClickListener(v -> {
            visualAgent.stop(); touchAgent.stop(); controllerEngine.stop(); setFullscreen(false);
        });
        previewCapture.setOnClickListener(v -> {
            if (browser.getWidth() < 1 || browser.getHeight() < 1) {
                status.setText("No visible WebView to capture."); return;
            }
            int width = Math.min(640,browser.getWidth());
            int height = Math.max(1,Math.round(browser.getHeight() * width / (float)browser.getWidth()));
            Bitmap frame = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            canvas.scale(width/(float)browser.getWidth(),height/(float)browser.getHeight());
            browser.draw(canvas);
            ImageView image = new ImageView(this);
            image.setImageBitmap(frame);
            image.setAdjustViewBounds(true);
            new AlertDialog.Builder(this).setTitle("Actual WebView capture — check game video")
                .setMessage("This is what Android captured, not what Gemini imagines. If gameplay is black or absent here, the AI cannot reliably see the game through this capture method.")
                .setView(image)
                .setPositiveButton("Close",null).show();
        });
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
        gameTab.setOnClickListener(v -> {
            showSection("game");
            browser.loadUrl("https://www.xbox.com/play");
        });
        aiTab.setOnClickListener(v -> {
            showSection("ai");
            status.setText("AI controls: set Gemini key, verify capture, then start experimental visual AI.");
        });
        controllerTab.setOnClickListener(v -> {
            showSection("ble");
            status.setText("BLE controls: connect SKB controller, then run the short input test. Hardware not verified.");
        });
        toolsTab.setOnClickListener(v -> startActivity(new Intent(this, SimulatorActivity.class)));
        test.setOnClickListener(v -> browser.evaluateJavascript(
            "(function(){return JSON.stringify({url:location.origin,gamepadAPI:!!navigator.getGamepads,connected:navigator.getGamepads?Array.from(navigator.getGamepads()).filter(Boolean).length:0})})()",
            result -> status.setText("Gamepad diagnostic: " + result)));
        fullscreenButton.setOnClickListener(v -> setFullscreen(true));
        labButton.setOnClickListener(v -> startActivity(new Intent(this, SimulatorActivity.class)));
    }

    private void showSection(String section) {
        gameControls.setVisibility("game".equals(section) ? View.VISIBLE : View.GONE);
        aiControls.setVisibility("ai".equals(section) ? View.VISIBLE : View.GONE);
        bleControls.setVisibility("ble".equals(section) ? View.VISIBLE : View.GONE);
        commandScroll.smoothScrollTo(0, 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private android.widget.HorizontalScrollView horizontalControls(LinearLayout controls) {
        android.widget.HorizontalScrollView scroller = new android.widget.HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.addView(controls);
        for (int i = 0; i < controls.getChildCount(); i++) {
            View child = controls.getChildAt(i);
            if (child instanceof Button) {
                Button button = (Button) child;
                button.setTextColor(Color.WHITE);
                button.setTextSize(11);
                button.setAllCaps(false);
                button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(32, 55, 72)));
            }
        }
        return scroller;
    }

    private void closeVideoFullscreen() {
        if (videoFullscreenView == null) return;
        gameFrame.removeView(videoFullscreenView);
        videoFullscreenView = null;
        browser.setVisibility(View.VISIBLE);
        WebChromeClient.CustomViewCallback callback = videoFullscreenCallback;
        videoFullscreenCallback = null;
        if (callback != null) callback.onCustomViewHidden();
        setFullscreen(false);
    }

    private void setFullscreen(boolean enable) {
        fullscreen = enable;
        commandScroll.setVisibility(enable ? View.GONE : View.VISIBLE);
        gameHeading.setVisibility(enable ? View.GONE : View.VISIBLE);
        if (floatingStop != null) floatingStop.setVisibility(enable ? View.VISIBLE : View.GONE);
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

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLE_PERMISSION_REQUEST && bleBridge != null) {
            if (bleBridge.hasPermission()) bleBridge.connect();
            else status.setText("Bluetooth permission denied");
        }
    }

    @Override public void onBackPressed() {
        if (visualAgent != null && visualAgent.isRunning()) { visualAgent.stop(); return; }
        if (touchAgent != null && touchAgent.isActive()) { touchAgent.stop(); return; }
        if (videoFullscreenView != null) closeVideoFullscreen();
        else if (fullscreen) setFullscreen(false);
        else if (browser.canGoBack()) browser.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        if (visualAgent != null) visualAgent.stop();
        if (controllerEngine != null) controllerEngine.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (visualAgent != null) visualAgent.stop();
        if (controllerEngine != null) controllerEngine.stop();
        if (bleBridge != null) bleBridge.disconnect();
        if (touchAgent != null) touchAgent.stop();
        if (videoFullscreenView != null) closeVideoFullscreen();
        browser.destroy();
        super.onDestroy();
    }
}
