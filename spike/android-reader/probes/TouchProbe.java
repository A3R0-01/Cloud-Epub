import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Disposable API 35 AVD: inject real touch events from one shell process. */
public final class TouchProbe {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("android.hardware.input.InputManagerGlobal");
        Object manager = type.getMethod("getInstance").invoke(null);
        Method inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
        float x = Float.parseFloat(args[0]);
        float y = Float.parseFloat(args[1]);
        for (int tap = 0; tap < 2; tap++) {
            long down = SystemClock.uptimeMillis();
            MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0);
            press.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            if (!((Boolean) inject.invoke(manager, press, 2))) throw new IllegalStateException("Down rejected");
            press.recycle();
            Thread.sleep(40);
            long up = SystemClock.uptimeMillis();
            MotionEvent release = MotionEvent.obtain(down, up, MotionEvent.ACTION_UP, x, y, 0);
            release.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            if (!((Boolean) inject.invoke(manager, release, 2))) throw new IllegalStateException("Up rejected");
            release.recycle();
            System.out.println("tap " + tap + " down=" + down + " up=" + up);
            Thread.sleep(70);
        }
    }
}
