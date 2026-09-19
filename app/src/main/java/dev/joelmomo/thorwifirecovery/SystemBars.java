package dev.joelmomo.thorwifirecovery;

import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

final class SystemBars {
    private SystemBars() {}

    static void apply(Window window, int color, boolean lightBackground) {
        setColors(window, color);
        setLightAppearance(window, lightBackground);
    }

    @SuppressWarnings("deprecation")
    static void setColors(Window window, int color) {
        // Kept for API 26-34 compatibility. The app currently targets API 33,
        // where explicit system-bar colors remain part of the visual contract.
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
    }

    static void setLightAppearance(Window window, boolean lightBackground) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBackground ? mask : 0, mask);
                return;
            }
        }
        setLegacyLightAppearance(window, lightBackground);
    }

    @SuppressWarnings("deprecation")
    private static void setLegacyLightAppearance(Window window, boolean lightBackground) {
        int flags = lightBackground
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0;
        window.getDecorView().setSystemUiVisibility(flags);
    }
}
