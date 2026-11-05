package com.skb.aiplayer;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Passive USB/OTG gamepad diagnostics. Never injects Android input events. */
public final class WiredControllerMonitor {
    public interface Listener {
        void onButtons(int keyCode, boolean pressed);
        void onAxes(float lx, float ly, float rx, float ry, float lt, float rt);
    }
    private final Listener listener;
    public WiredControllerMonitor(Listener listener) { this.listener = listener; }
    public static boolean isGamepad(InputDevice device) {
        if (device == null) return false;
        int s = device.getSources();
        return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    public boolean onKey(KeyEvent event) {
        if (event == null || !isGamepad(event.getDevice())) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            listener.onButtons(event.getKeyCode(),true);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            listener.onButtons(event.getKeyCode(),false);
        } else return false;
        return true;
    }
    private static float axis(MotionEvent event, int id) {
        return event.getAxisValue(id);
    }
    public boolean onMotion(MotionEvent event) {
        if (event == null || !isGamepad(event.getDevice()) || event.getAction() != MotionEvent.ACTION_MOVE) return false;
        listener.onAxes(axis(event,MotionEvent.AXIS_X),axis(event,MotionEvent.AXIS_Y),
                axis(event,MotionEvent.AXIS_Z),axis(event,MotionEvent.AXIS_RZ),
                axis(event,MotionEvent.AXIS_BRAKE),axis(event,MotionEvent.AXIS_GAS));
        return true;
    }
}
