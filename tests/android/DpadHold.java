import android.os.SystemClock;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.InputDevice;
import java.lang.reflect.Method;

/** Device QA helper, excluded from the application. Injects a real held D-pad key. */
public final class DpadHold {
    public static void main(String[] args) throws Exception {
        int key = Integer.parseInt(args[0]);
        long duration = Long.parseLong(args[1]);
        Class<?> type = Class.forName("android.hardware.input.InputManager");
        Object manager = type.getMethod("getInstance").invoke(null);
        Method inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
        long start = SystemClock.uptimeMillis();
        int repeat = 0;
        do {
            inject.invoke(manager, new KeyEvent(start, SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN, key, repeat++, 0, -1, 0,
                KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_DPAD), 2);
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() - start < duration);
        inject.invoke(manager, new KeyEvent(start, SystemClock.uptimeMillis(),
            KeyEvent.ACTION_UP, key, 0, 0, -1, 0,
            KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_DPAD), 2);
        System.out.println("Injected hold for " + (SystemClock.uptimeMillis() - start) + " ms");
    }
}
