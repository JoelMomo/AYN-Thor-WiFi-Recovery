package dev.joelmomo.thorwifirecovery;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "recovery_state";
    private static final String KEY_RECOVERY_PENDING = "recovery_pending";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView buildInfo;
    private Button diagnoseButton;
    private Button recoverButton;
    private Button reportButton;
    private Button projectButton;
    private DiagnosticEngine.Snapshot lastSnapshot;
    private boolean diagnosisAllowed = true;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,16,20));
        getWindow().setNavigationBarColor(Color.rgb(11,16,20));
        buildUi();
        boolean pending = prefs().getBoolean(KEY_RECOVERY_PENDING, false);
        runProbe(pending, pending);
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
        subtitle.setPadding(0, dp(6), 0, dp(8));
        root.addView(subtitle);
        buildInfo = text(getString(R.string.build_info, BuildConfig.VERSION_NAME, getString(R.string.unknown_value)), 12, Color.rgb(128,145,154), false);
        buildInfo.setPadding(0, 0, 0, dp(18));
        root.addView(buildInfo);

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
        diagnoseButton.setOnClickListener(v -> runProbe(true, false));
        recoverButton.setOnClickListener(v -> confirmRecovery());
        reportButton = button(getString(R.string.copy_report), Color.rgb(35,49,57), Color.WHITE);
        projectButton = button(getString(R.string.open_project), Color.rgb(35,49,57), Color.WHITE);
        reportButton.setEnabled(false);
        reportButton.setOnClickListener(v -> copyDiagnosticReport());
        projectButton.setOnClickListener(v -> openProject());
        root.addView(diagnoseButton, matchWrap(0, 0, 0, 12));
        root.addView(recoverButton, matchWrap(0, 0, 0, 12));
        root.addView(reportButton, matchWrap(0, 0, 0, 12));
        root.addView(projectButton, matchWrap(0, 0, 0, 20));

        TextView warning = text(getString(R.string.warning), 14, Color.rgb(222,228,231), false);
        warning.setPadding(dp(2), 0, dp(2), dp(18));
        root.addView(warning);
        TextView about = text(getString(R.string.about), 12, Color.rgb(128,145,154), false);
        root.addView(about);
        setContentView(scroll);
    }

    private void runProbe(boolean fullDiagnosis, boolean postRecovery) {
        setBusy(true);
        statusTitle.setText(postRecovery ? R.string.status_verifying : R.string.status_checking);
        statusDetail.setText("");
        worker.execute(() -> {
            try {
                if (fullDiagnosis) {
                    DiagnosticEngine.Snapshot snapshot =
                            DiagnosticEngine.parseDiagnosis(ThorRootBridge.diagnoseScanner());
                    android.util.Log.i("ThorWiFiRecovery",
                            "diagnosis=" + snapshot.diagnosis()
                                    + " carrierUp=" + snapshot.carrierUp
                                    + " scannerRc=" + snapshot.scannerRc
                                    + " radioAps=" + snapshot.radioAps);
                    runOnUiThread(() -> showDiagnosis(snapshot, postRecovery));
                } else {
                    DiagnosticEngine.DeviceInfo info =
                            DiagnosticEngine.parseProbe(ThorRootBridge.probe());
                    runOnUiThread(() -> showProbe(info));
                }
            } catch (Exception e) {
                runOnUiThread(() -> showServiceError(e, postRecovery));
            }
        });
    }

    private void showProbe(DiagnosticEngine.DeviceInfo info) {
        lastSnapshot = null;
        updateBuildInfo(info);
        recoverButton.setEnabled(false);
        if (!DiagnosticEngine.isSupportedFirmware(info.firmware)) {
            diagnosisAllowed = false;
            statusTitle.setText(R.string.status_unsupported);
            statusDetail.setText(getString(R.string.unsupported_firmware_detail, info.firmware));
        } else if (!info.wifiEnabled) {
            diagnosisAllowed = true;
            statusTitle.setText(R.string.status_wifi_off);
            statusDetail.setText(R.string.wifi_off_detail);
        } else if (!info.wlanPresent) {
            diagnosisAllowed = false;
            statusTitle.setText(R.string.status_wlan_missing);
            statusDetail.setText(R.string.wlan_missing_detail);
        } else {
            diagnosisAllowed = true;
            statusTitle.setText(R.string.status_ready);
            statusDetail.setText(getString(R.string.validated_firmware_detail, info.firmware));
        }
        setBusy(false);
    }

    private void showDiagnosis(DiagnosticEngine.Snapshot snapshot, boolean postRecovery) {
        lastSnapshot = snapshot;
        updateBuildInfo(snapshot.device);
        if (postRecovery) {
            prefs().edit().remove(KEY_RECOVERY_PENDING).apply();
            showPostRecovery(snapshot);
            setBusy(false);
            return;
        }

        DiagnosticEngine.Diagnosis diagnosis = snapshot.diagnosis();
        recoverButton.setEnabled(false);
        diagnosisAllowed = diagnosis != DiagnosticEngine.Diagnosis.UNSUPPORTED_FIRMWARE
                && diagnosis != DiagnosticEngine.Diagnosis.WLAN_MISSING;

        switch (diagnosis) {
            case READY:
                statusTitle.setText(R.string.status_ready);
                statusDetail.setText(R.string.scanner_idle_detail);
                break;
            case LOCKUP_CONFIRMED:
                statusTitle.setText(R.string.status_lockup);
                statusDetail.setText(snapshot.carrierUp
                        ? getString(R.string.lockup_state_detail)
                        : getResources().getQuantityString(R.plurals.lockup_detail, snapshot.radioAps, snapshot.radioAps));
                recoverButton.setEnabled(true);
                break;
            case UNSUPPORTED_FIRMWARE:
                statusTitle.setText(R.string.status_unsupported);
                statusDetail.setText(getString(
                        R.string.unsupported_firmware_detail, snapshot.device.firmware));
                break;
            case WIFI_DISABLED:
                statusTitle.setText(R.string.status_wifi_off);
                statusDetail.setText(R.string.wifi_off_detail);
                break;
            case WLAN_MISSING:
                statusTitle.setText(R.string.status_wlan_missing);
                statusDetail.setText(R.string.wlan_missing_detail);
                break;
            case SCANNER_TIMEOUT:
                statusTitle.setText(R.string.status_scanner_timeout);
                statusDetail.setText(R.string.scanner_timeout_detail);
                break;
            case SCANNER_ERROR:
                statusTitle.setText(R.string.status_scanner_error);
                statusDetail.setText(getString(R.string.scanner_error_detail, snapshot.scannerRc));
                break;
            case SCANNER_UNCONFIRMED:
                statusTitle.setText(R.string.status_scanner_unconfirmed);
                statusDetail.setText(R.string.scanner_unconfirmed_detail);
                break;
            default:
                statusTitle.setText(R.string.status_scanner_unknown);
                statusDetail.setText(R.string.scanner_unknown_detail);
                break;
        }
        setBusy(false);
    }

    private void showPostRecovery(DiagnosticEngine.Snapshot snapshot) {
        DiagnosticEngine.Diagnosis diagnosis = snapshot.diagnosis();
        recoverButton.setEnabled(false);
        diagnosisAllowed = diagnosis != DiagnosticEngine.Diagnosis.UNSUPPORTED_FIRMWARE
                && diagnosis != DiagnosticEngine.Diagnosis.WLAN_MISSING;

        if (diagnosis == DiagnosticEngine.Diagnosis.READY) {
            statusTitle.setText(R.string.status_recovery_success);
            statusDetail.setText(R.string.recovery_success_detail);
        } else if (diagnosis == DiagnosticEngine.Diagnosis.LOCKUP_CONFIRMED) {
            statusTitle.setText(R.string.status_recovery_failed);
            statusDetail.setText(R.string.recovery_failed_detail);
            recoverButton.setEnabled(true);
        } else {
            statusTitle.setText(R.string.status_recovery_unverified);
            statusDetail.setText(postRecoveryDetail(diagnosis, snapshot));
        }
    }

    private CharSequence postRecoveryDetail(DiagnosticEngine.Diagnosis diagnosis,
                                            DiagnosticEngine.Snapshot snapshot) {
        switch (diagnosis) {
            case UNSUPPORTED_FIRMWARE:
                return getString(R.string.unsupported_firmware_detail, snapshot.device.firmware);
            case WIFI_DISABLED:
                return getString(R.string.wifi_off_detail);
            case WLAN_MISSING:
                return getString(R.string.wlan_missing_detail);
            case SCANNER_TIMEOUT:
                return getString(R.string.recovery_unverified_timeout_detail);
            case SCANNER_ERROR:
                return getString(R.string.scanner_error_detail, snapshot.scannerRc);
            case SCANNER_UNCONFIRMED:
                return getString(R.string.scanner_unconfirmed_detail);
            default:
                return getString(R.string.recovery_unverified_detail);
        }
    }

    private void showServiceError(Exception e, boolean postRecovery) {
        if (postRecovery) prefs().edit().remove(KEY_RECOVERY_PENDING).apply();
        lastSnapshot = null;
        statusTitle.setText(postRecovery
                ? R.string.status_recovery_unverified
                : R.string.status_unavailable);
        statusDetail.setText(getString(R.string.service_error, safeMessage(e)));
        recoverButton.setEnabled(false);
        setBusy(false);
    }

    private void confirmRecovery() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_title)
                .setMessage(R.string.confirm_body)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.run, (dialog, which) -> runRecovery())
                .show();
    }
    private void runRecovery() {
        setBusy(true);
        boolean persisted = prefs().edit().putBoolean(KEY_RECOVERY_PENDING, true).commit();
        if (!persisted) {
            statusTitle.setText(R.string.status_unavailable);
            statusDetail.setText(R.string.pending_state_error);
            setBusy(false);
            return;
        }

        worker.execute(() -> {
            try {
                ThorRootBridge.recover();
            } catch (Exception e) {
                prefs().edit().remove(KEY_RECOVERY_PENDING).apply();
                runOnUiThread(() -> showServiceError(e, false));
            }
        });
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void updateBuildInfo(DiagnosticEngine.DeviceInfo info) {
        String firmware = info == null || info.firmware == null || info.firmware.trim().isEmpty()
                ? getString(R.string.unknown_value) : info.firmware.trim();
        buildInfo.setText(getString(R.string.build_info, BuildConfig.VERSION_NAME, firmware));
    }

    private void setBusy(boolean busy) {
        diagnoseButton.setEnabled(!busy && diagnosisAllowed);
        if (busy) recoverButton.setEnabled(false);
        diagnoseButton.setAlpha(diagnoseButton.isEnabled() ? 1f : 0.55f);
        recoverButton.setAlpha(recoverButton.isEnabled() ? 1f : 0.55f);
        reportButton.setEnabled(!busy && lastSnapshot != null);
        reportButton.setAlpha(reportButton.isEnabled() ? 1f : 0.55f);
        projectButton.setEnabled(!busy);
        projectButton.setAlpha(projectButton.isEnabled() ? 1f : 0.55f);
    }

    private void copyDiagnosticReport() {
        if (lastSnapshot == null) {
            Toast.makeText(this, R.string.report_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        String report = DiagnosticReport.build(BuildConfig.VERSION_NAME, lastSnapshot);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Thor Wi-Fi diagnostic", report));
        Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
    }

    private void openProject() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.project_url))));
        } catch (Exception e) {
            Toast.makeText(this, R.string.project_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private String safeMessage(Exception e) {
        String value = e.getMessage();
        return value == null || value.trim().isEmpty()
                ? e.getClass().getSimpleName() : value;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
        }
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinHeight(dp(54));
        button.setBackground(roundRect(background, 16));
        return button;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
