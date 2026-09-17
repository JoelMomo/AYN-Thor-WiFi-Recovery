package dev.joelmomo.thorwifirecovery;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView statusTitle;
    private TextView statusDetail;
    private Button diagnoseButton;
    private Button recoverButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,16,20));
        getWindow().setNavigationBarColor(Color.rgb(11,16,20));
        buildUi();
        runProbe(false);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(24), dp(24), dp(28));
        root.setBackgroundColor(Color.rgb(11,16,20));
        scroll.addView(root);

        TextView title = text(getString(R.string.app_name), 30, Color.WHITE, true);
        root.addView(title);
        TextView subtitle = text(getString(R.string.subtitle), 15, Color.rgb(165,181,190), false);
        subtitle.setPadding(0, dp(6), 0, dp(22));
        root.addView(subtitle);

        LinearLayout statusCard = column();
        statusCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        statusCard.setBackground(roundRect(Color.rgb(20,29,35), 18));
        statusTitle = text(getString(R.string.status_checking), 20, Color.rgb(110,214,197), true);
        statusDetail = text("", 14, Color.rgb(190,201,207), false);
        statusDetail.setPadding(0, dp(8), 0, 0);
        statusCard.addView(statusTitle);
        statusCard.addView(statusDetail);
        root.addView(statusCard, matchWrap(0, 0, 0, 18));

        diagnoseButton = button(getString(R.string.diagnose), Color.rgb(35,49,57), Color.WHITE);
        recoverButton = button(getString(R.string.recover), Color.rgb(110,214,197), Color.rgb(4,33,30));
        recoverButton.setEnabled(false);
        diagnoseButton.setOnClickListener(v -> runProbe(true));
        recoverButton.setOnClickListener(v -> confirmRecovery());
        root.addView(diagnoseButton, matchWrap(0, 0, 0, 12));
        root.addView(recoverButton, matchWrap(0, 0, 0, 20));

        TextView warning = text(getString(R.string.warning), 14, Color.rgb(222,228,231), false);
        warning.setPadding(dp(2), 0, dp(2), dp(18));
        root.addView(warning);
        TextView about = text(getString(R.string.about), 12, Color.rgb(128,145,154), false);
        root.addView(about);
        setContentView(scroll);
    }

    private void runProbe(boolean forced) {
        setBusy(true);
        statusTitle.setText(R.string.status_checking);
        statusDetail.setText("");
        worker.execute(() -> {
            try {
                String output = forced ? ThorRootBridge.forceScan() : ThorRootBridge.probe();
                android.util.Log.i("ThorWiFiRecovery", "probe forced=" + forced + " output=\n" + output);
                int androidAps = forced ? countAccessPoints(section(output, "__ANDROID_RESULTS__", "__RADIO_RESULTS__")) : countAccessPoints(output);
                int radioAps = forced ? countAccessPoints(section(output, "__RADIO_RESULTS__", null)) : -1;
                String ssid = connectedSsid(output);
                runOnUiThread(() -> showProbeResult(forced, androidAps, radioAps, ssid));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusTitle.setText(R.string.status_unavailable);
                    statusDetail.setText(getString(R.string.service_error, safeMessage(e)));
                    recoverButton.setEnabled(false);
                    setBusy(false);
                });
            }
        });
    }

    private void showProbeResult(boolean forced, int androidAps, int radioAps, String ssid) {
        if (!forced) {
            statusTitle.setText(R.string.status_ready);
            statusDetail.setText(ssid != null ? getString(R.string.connected_to, ssid) : getString(R.string.service_ready_detail));
            recoverButton.setEnabled(false);
        } else if (androidAps > 0) {
            statusTitle.setText(R.string.status_ready);
            statusDetail.setText(getResources().getQuantityString(R.plurals.scan_ok, androidAps, androidAps));
            recoverButton.setEnabled(false);
        } else if (radioAps > 0) {
            statusTitle.setText(R.string.status_lockup);
            statusDetail.setText(getString(R.string.lockup_detail, radioAps));
            recoverButton.setEnabled(true);
        } else {
            statusTitle.setText(R.string.status_radio_empty);
            statusDetail.setText(R.string.radio_empty_detail);
            recoverButton.setEnabled(false);
        }
        setBusy(false);
    }

    private void confirmRecovery() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(R.string.confirm_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.run, (d, which) -> runRecovery())
                .show();
    }

    private void runRecovery() {
        setBusy(true);
        worker.execute(() -> {
            try { ThorRootBridge.recover(); }
            catch (Exception e) {
                runOnUiThread(() -> {
                    statusTitle.setText(R.string.status_unavailable);
                    statusDetail.setText(getString(R.string.service_error, safeMessage(e)));
                    setBusy(false);
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        diagnoseButton.setEnabled(!busy);
        if (busy) recoverButton.setEnabled(false);
        diagnoseButton.setAlpha(busy ? 0.55f : 1f);
        recoverButton.setAlpha(recoverButton.isEnabled() ? 1f : 0.55f);
    }

    private static String section(String output, String startMarker, String endMarker) {
        if (output == null) return "";
        int start = output.indexOf(startMarker);
        if (start < 0) return "";
        start += startMarker.length();
        int end = endMarker == null ? output.length() : output.indexOf(endMarker, start);
        if (end < 0) end = output.length();
        return output.substring(start, end);
    }

    private static int countAccessPoints(String output) {
        Matcher m = Pattern.compile("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}").matcher(output == null ? "" : output);
        int count = 0;
        while (m.find()) count++;
        return count;
    }

    private static String connectedSsid(String output) {
        Matcher m = Pattern.compile("connected to \\\"([^\\\"]+)\\\"").matcher(output == null ? "" : output);
        return m.find() ? m.group(1) : null;
    }

    private String safeMessage(Exception e) {
        String s = e.getMessage();
        return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        v.setLineSpacing(0, 1.12f);
        return v;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(16);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setMinHeight(dp(54));
        b.setBackground(roundRect(background, 16));
        return b;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout.LayoutParams matchWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
