package dev.joelmomo.thorwifirecovery;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "recovery_state";
    private static final String KEY_RECOVERY_PENDING = "recovery_pending";
    private static final String KEY_LIGHT_THEME = "light_theme";
    private static final String KEY_THEME_TRANSITION = "theme_transition";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView recoveryHint;
    private TextView statusInfoButton;
    private ProgressBar statusSpinner;
    private TextView themeToggle;
    private TextView buildInfo;
    private LinearLayout statusCard;
    private View statusRail;
    private LinearLayout detailsBody;
    private Button detailsToggle;
    private TextView detailFirmware;
    private TextView detailSupport;
    private TextView detailScanner;
    private TextView detailCarrier;
    private TextView detailAps;
    private Button diagnoseButton;
    private Button recoverButton;
    private Button reportButton;
    private Button projectButton;
    private DiagnosticEngine.Snapshot lastSnapshot;
    private boolean diagnosisAllowed = true;
    private boolean isLightTheme;
    private int colorBg, colorCard, colorCardSoft, colorOutline, colorAccent;
    private int colorTextPrimary, colorTextSecondary, colorTextMuted, colorButtonText;
    private int colorChipValidated, colorChipNeutral, colorDetailText;
    private int toneOk, toneRecovery, toneWarning, toneError, toneNeutral;
    private int currentStatusColor, currentStatusFill;
    private ValueAnimator statusAnimator;
    private LiquidStatusDrawable statusDrawable;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        isLightTheme = prefs().getBoolean(KEY_LIGHT_THEME, false);
        boolean animateTheme = prefs().getBoolean(KEY_THEME_TRANSITION, false);
        if (animateTheme) prefs().edit().remove(KEY_THEME_TRANSITION).apply();
        applyPalette();
        View decor = getWindow().getDecorView();
        decor.setAlpha(animateTheme ? 0f : 1f);
        getWindow().setStatusBarColor(colorBg);
        getWindow().setNavigationBarColor(colorBg);
        getWindow().getDecorView().setSystemUiVisibility(isLightTheme
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0);
        buildUi();
        if (animateTheme) runThemeReveal(decor);
        boolean pending = prefs().getBoolean(KEY_RECOVERY_PENDING, false);
        runProbe(pending, pending);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        final boolean wide = getResources().getConfiguration().screenWidthDp >= 700;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = column();
        root.setPadding(dp(wide ? 24 : 18), dp(14), dp(wide ? 24 : 18), dp(14));
        root.setBackground(themeBackground());
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(isLightTheme
                ? R.drawable.ic_wifi_recovery_header_light
                : R.drawable.ic_wifi_recovery_header_dark);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(50), dp(50));
        logoParams.setMargins(0, 0, dp(14), 0);
        header.addView(logo, logoParams);
        LinearLayout headerText = column();
        TextView title = text(getString(R.string.app_name), wide ? 27 : 24, colorTextPrimary, true);
        title.setLetterSpacing(-0.015f);
        headerText.addView(title);
        TextView subtitle = text(getString(R.string.subtitle), 13, colorTextSecondary, false);
        subtitle.setPadding(0, dp(1), 0, 0);
        headerText.addView(subtitle);
        header.addView(headerText, new LinearLayout.LayoutParams(0, -2, 1f));        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        chips.addView(chip(getString(R.string.chip_validated), colorChipValidated, colorAccent));
        chips.addView(chip(getString(R.string.chip_offline), colorChipNeutral, colorTextSecondary), chipMargin());
        themeToggle = iconButton(isLightTheme ? "\u2600" : "\u263E", getString(R.string.theme_toggle));
        themeToggle.setOnClickListener(v -> { interactionFeedback(v); toggleTheme(); });
        if (wide) {
            header.addView(chips);
            header.addView(themeToggle, iconMargin());
        } else {
            header.addView(themeToggle);
        }
        root.addView(header);
        if (!wide) {
            chips.setGravity(Gravity.START);
            chips.setPadding(dp(64), dp(7), 0, 0);
            root.addView(chips);
        }

        LinearLayout dashboard = column();
        LinearLayout.LayoutParams dashboardParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        dashboardParams.setMargins(0, dp(12), 0, 0);
        root.addView(dashboard, dashboardParams);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.TOP);
        LinearLayout bottomRow = new LinearLayout(this);
        bottomRow.setGravity(Gravity.TOP);        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        currentStatusColor = colorAccent;
        currentStatusFill = colorCard;
        statusDrawable = new LiquidStatusDrawable(colorCard, colorOutline);
        statusCard.setBackground(statusDrawable);
        statusRail = new View(this);
        statusRail.setBackground(roundRect(colorAccent, 999));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(4), -1);
        railParams.setMargins(0, dp(12), 0, dp(12));
        statusCard.addView(statusRail, railParams);

        LinearLayout statusContent = column();
        statusContent.setPadding(dp(18), dp(15), dp(18), dp(16));
        LinearLayout statusHeader = new LinearLayout(this);
        statusHeader.setGravity(Gravity.CENTER_VERTICAL);
        statusHeader.addView(sectionLabel(getString(R.string.section_status)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        statusInfoButton = infoButton(getString(R.string.status_info));
        statusInfoButton.setVisibility(View.GONE);
        statusInfoButton.setOnClickListener(v -> { interactionFeedback(v); toggleInfo(statusDetail, statusInfoButton); });
        statusHeader.addView(statusInfoButton);
        statusContent.addView(statusHeader);

        LinearLayout statusCenter = new LinearLayout(this);
        statusCenter.setGravity(Gravity.CENTER_VERTICAL);
        statusTitle = text(getString(R.string.status_checking), 23, colorAccent, true);
        statusTitle.setLetterSpacing(-0.01f);
        statusCenter.addView(statusTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        statusSpinner = new ProgressBar(this);
        statusSpinner.setIndeterminate(true);
        statusSpinner.getIndeterminateDrawable().setTint(colorAccent);
        statusSpinner.setVisibility(View.GONE);
        statusCenter.addView(statusSpinner, spinnerParams());
        statusContent.addView(statusCenter, new LinearLayout.LayoutParams(-1, 0, 1f));        statusDetail = text("", 13, colorDetailText, false);
        statusDetail.setPadding(0, dp(8), 0, 0);
        statusDetail.setVisibility(View.GONE);
        statusContent.addView(statusDetail);
        statusCard.addView(statusContent, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout recoveryCard = column();
        recoveryCard.setPadding(dp(18), dp(15), dp(18), dp(16));
        recoveryCard.setBackground(panelBackground());
        LinearLayout recoveryHeader = new LinearLayout(this);
        recoveryHeader.setGravity(Gravity.CENTER_VERTICAL);
        recoveryHeader.addView(sectionLabel(getString(R.string.section_recovery)),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView recoveryInfo = infoButton(getString(R.string.recovery_info));
        recoveryInfo.setOnClickListener(v -> { interactionFeedback(v); toggleInfo(recoveryHint, recoveryInfo); });
        recoveryHeader.addView(recoveryInfo);
        recoveryCard.addView(recoveryHeader);
        recoveryHint = text(getString(R.string.recovery_hint), 13, colorTextSecondary, false);
        recoveryHint.setPadding(0, dp(8), 0, dp(8));
        recoveryHint.setVisibility(View.GONE);
        recoveryCard.addView(recoveryHint);
        recoverButton = button(getString(R.string.recover), colorAccent, colorButtonText);
        recoverButton.setEnabled(false);
        recoverButton.setOnClickListener(v -> { interactionFeedback(v); confirmRecovery(); });
        LinearLayout recoveryCenter = new LinearLayout(this);
        recoveryCenter.setGravity(Gravity.CENTER);
        recoveryCenter.addView(recoverButton, new LinearLayout.LayoutParams(-1, -2));
        recoveryCard.addView(recoveryCenter, new LinearLayout.LayoutParams(-1, 0, 1f));        LinearLayout toolsCard = column();
        toolsCard.setPadding(dp(16), dp(14), dp(16), dp(15));
        toolsCard.setBackground(panelBackground());
        toolsCard.addView(sectionLabel(getString(R.string.section_tools)));
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        toolRow.setPadding(0, dp(10), 0, 0);
        diagnoseButton = toolButton(getString(R.string.diagnose), R.drawable.ic_tool_scan);
        reportButton = toolButton(getString(R.string.copy_report_short), R.drawable.ic_tool_report);
        reportButton.setEnabled(false);
        detailsToggle = toolButton(getString(R.string.details_show_short), R.drawable.ic_tool_details);
        diagnoseButton.setOnClickListener(v -> { interactionFeedback(v); runProbe(true, false); });
        reportButton.setOnClickListener(v -> { interactionFeedback(v); copyDiagnosticReport(); });
        detailsToggle.setOnClickListener(v -> { interactionFeedback(v); toggleDetails(); });
        toolRow.addView(diagnoseButton, weightedButton(0, dp(5)));
        toolRow.addView(reportButton, weightedButton(dp(5), dp(5)));
        toolRow.addView(detailsToggle, weightedButton(dp(5), 0));
        toolsCard.addView(toolRow, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout deviceCard = column();
        deviceCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        deviceCard.setBackground(panelBackground());
        deviceCard.addView(sectionLabel(getString(R.string.section_device)));
        LinearLayout metrics = new LinearLayout(this);
        metrics.setGravity(Gravity.CENTER_VERTICAL);
        metrics.setPadding(0, dp(9), 0, 0);
        detailFirmware = metricValue();
        detailScanner = metricValue();
        detailCarrier = metricValue();
        detailAps = metricValue();
        metrics.addView(metricTile(getString(R.string.detail_firmware), detailFirmware), metricParams(0, dp(4)));
        metrics.addView(metricTile(getString(R.string.detail_scanner), detailScanner), metricParams(dp(4), dp(4)));
        metrics.addView(metricTile(getString(R.string.detail_carrier), detailCarrier), metricParams(dp(4), dp(4)));
        metrics.addView(metricTile(getString(R.string.detail_radio_aps), detailAps), metricParams(dp(4), 0));
        deviceCard.addView(metrics, new LinearLayout.LayoutParams(-1, 0, 1f));

        detailsBody = column();
        detailsBody.setVisibility(View.GONE);
        detailsBody.setPadding(0, dp(7), 0, 0);
        detailSupport = addDetail(detailsBody, R.string.detail_support, R.string.detail_not_checked);
        detailsBody.addView(divider());
        TextView privacy = text(getString(R.string.privacy_note), 11, colorTextMuted, false);
        privacy.setPadding(0, dp(8), 0, dp(2));
        detailsBody.addView(privacy);
        projectButton = textButton(getString(R.string.open_project));
        projectButton.setOnClickListener(v -> { interactionFeedback(v); openProject(); });
        detailsBody.addView(projectButton);
        deviceCard.addView(detailsBody);

        if (wide) {
            LinearLayout.LayoutParams topLeft = new LinearLayout.LayoutParams(0, -1, 1.10f);
            LinearLayout.LayoutParams topRight = new LinearLayout.LayoutParams(0, -1, 0.90f);
            topLeft.setMargins(0, 0, dp(7), 0);
            topRight.setMargins(dp(7), 0, 0, 0);
            topRow.addView(statusCard, topLeft);
            topRow.addView(recoveryCard, topRight);
            LinearLayout.LayoutParams bottomLeft = new LinearLayout.LayoutParams(0, -1, 1.10f);
            LinearLayout.LayoutParams bottomRight = new LinearLayout.LayoutParams(0, -1, 0.90f);
            bottomLeft.setMargins(0, 0, dp(7), 0);
            bottomRight.setMargins(dp(7), 0, 0, 0);
            bottomRow.addView(toolsCard, bottomLeft);
            bottomRow.addView(deviceCard, bottomRight);
            dashboard.addView(topRow, new LinearLayout.LayoutParams(-1, 0, 1.15f));
            LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, 0, 0.85f);
            bottomParams.setMargins(0, dp(12), 0, 0);
            dashboard.addView(bottomRow, bottomParams);
        } else {            topRow.setOrientation(LinearLayout.VERTICAL);
            bottomRow.setOrientation(LinearLayout.VERTICAL);
            topRow.addView(statusCard, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams recParams = new LinearLayout.LayoutParams(-1, -2);
            recParams.setMargins(0, dp(12), 0, 0);
            topRow.addView(recoveryCard, recParams);
            bottomRow.addView(toolsCard, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams devParams = new LinearLayout.LayoutParams(-1, -2);
            devParams.setMargins(0, dp(12), 0, 0);
            bottomRow.addView(deviceCard, devParams);
            dashboard.addView(topRow, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, -2);
            bottomParams.setMargins(0, dp(12), 0, 0);
            dashboard.addView(bottomRow, bottomParams);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(2), dp(10), dp(2), 0);
        buildInfo = text(getString(R.string.build_info, BuildConfig.VERSION_NAME,
                getString(R.string.unknown_value)), 10, colorTextMuted, false);
        TextView about = text(getString(R.string.about), 10, colorTextMuted, false);
        if (wide) {
            footer.addView(buildInfo, new LinearLayout.LayoutParams(0, -2, 1f));
            about.setGravity(Gravity.END);
            footer.addView(about, new LinearLayout.LayoutParams(0, -2, 1f));
        } else {
            LinearLayout footerColumn = column();
            footerColumn.addView(buildInfo);
            about.setPadding(0, dp(3), 0, 0);
            footerColumn.addView(about);
            footer.addView(footerColumn, new LinearLayout.LayoutParams(-1, -2));
        }
        root.addView(footer);
        setContentView(scroll);
    }

    private void runProbe(boolean fullDiagnosis, boolean postRecovery) {
        setBusy(true);
        statusTitle.setText(postRecovery ? R.string.status_verifying : R.string.status_checking);
        statusDetail.setText("");
        setStatusVisual(StatusTone.NEUTRAL);
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
        updateDetails(info, null);
        recoverButton.setEnabled(false);
        if (!DiagnosticEngine.isSupportedFirmware(info.firmware)) {
            diagnosisAllowed = false;
            statusTitle.setText(R.string.status_unsupported);
            statusDetail.setText(getString(R.string.unsupported_firmware_detail, info.firmware));
            setStatusVisual(StatusTone.WARNING);
        } else if (!info.wifiEnabled) {
            diagnosisAllowed = true;
            statusTitle.setText(R.string.status_wifi_off);
            statusDetail.setText(R.string.wifi_off_detail);
            setStatusVisual(StatusTone.WARNING);
        } else if (!info.wlanPresent) {
            diagnosisAllowed = false;
            statusTitle.setText(R.string.status_wlan_missing);
            statusDetail.setText(R.string.wlan_missing_detail);
            setStatusVisual(StatusTone.ERROR);
        } else {
            diagnosisAllowed = true;
            statusTitle.setText(R.string.status_probe_ready);
            statusDetail.setText(R.string.validated_firmware_detail);
            setStatusVisual(StatusTone.NEUTRAL);
        }
        setBusy(false);
    }

    private void showDiagnosis(DiagnosticEngine.Snapshot snapshot, boolean postRecovery) {
        lastSnapshot = snapshot;
        updateBuildInfo(snapshot.device);
        updateDetails(snapshot.device, snapshot);
        if (postRecovery) {
            prefs().edit().remove(KEY_RECOVERY_PENDING).apply();
            showPostRecovery(snapshot);
            setBusy(false);
            return;
        }

        DiagnosticEngine.Diagnosis diagnosis = snapshot.diagnosis();
        recoverButton.setEnabled(false);
        recoverButton.setText(R.string.recover);
        diagnosisAllowed = diagnosis != DiagnosticEngine.Diagnosis.UNSUPPORTED_FIRMWARE
                && diagnosis != DiagnosticEngine.Diagnosis.WLAN_MISSING;

        switch (diagnosis) {
            case READY:
                statusTitle.setText(R.string.status_ready);
                statusDetail.setText(R.string.scanner_idle_detail);
                setStatusVisual(StatusTone.OK);
                break;
            case LOCKUP_CONFIRMED:
                statusTitle.setText(R.string.status_lockup);
                statusDetail.setText(snapshot.carrierUp
                        ? getString(R.string.lockup_state_detail)
                        : getResources().getQuantityString(R.plurals.lockup_detail, snapshot.radioAps, snapshot.radioAps));
                recoverButton.setEnabled(true);
                setStatusVisual(StatusTone.RECOVERY);
                break;
            case LOCKUP_PROBABLE:
                statusTitle.setText(R.string.status_lockup_probable);
                statusDetail.setText(R.string.lockup_probable_detail);
                recoverButton.setText(R.string.recover_probable);
                recoverButton.setEnabled(true);
                setStatusVisual(StatusTone.WARNING);
                break;
            case UNSUPPORTED_FIRMWARE:
                statusTitle.setText(R.string.status_unsupported);
                statusDetail.setText(getString(
                        R.string.unsupported_firmware_detail, snapshot.device.firmware));
                setStatusVisual(StatusTone.WARNING);
                break;
            case WIFI_DISABLED:
                statusTitle.setText(R.string.status_wifi_off);
                statusDetail.setText(R.string.wifi_off_detail);
                setStatusVisual(StatusTone.WARNING);
                break;
            case WLAN_MISSING:
                statusTitle.setText(R.string.status_wlan_missing);
                statusDetail.setText(R.string.wlan_missing_detail);
                setStatusVisual(StatusTone.ERROR);
                break;
            case SCANNER_TIMEOUT:
                statusTitle.setText(R.string.status_scanner_timeout);
                statusDetail.setText(R.string.scanner_timeout_detail);
                setStatusVisual(StatusTone.WARNING);
                break;
            case SCANNER_ERROR:
                statusTitle.setText(R.string.status_scanner_error);
                statusDetail.setText(getString(R.string.scanner_error_detail, snapshot.scannerRc));
                setStatusVisual(StatusTone.ERROR);
                break;
            default:
                statusTitle.setText(R.string.status_scanner_unknown);
                statusDetail.setText(R.string.scanner_unknown_detail);
                setStatusVisual(StatusTone.WARNING);
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
            setStatusVisual(StatusTone.OK);
        } else if (diagnosis == DiagnosticEngine.Diagnosis.LOCKUP_CONFIRMED
                || diagnosis == DiagnosticEngine.Diagnosis.LOCKUP_PROBABLE) {
            statusTitle.setText(R.string.status_recovery_failed);
            statusDetail.setText(R.string.recovery_failed_detail);
            recoverButton.setText(diagnosis == DiagnosticEngine.Diagnosis.LOCKUP_PROBABLE
                    ? R.string.recover_probable : R.string.recover);
            recoverButton.setEnabled(true);
            setStatusVisual(StatusTone.ERROR);
        } else {
            statusTitle.setText(R.string.status_recovery_unverified);
            statusDetail.setText(postRecoveryDetail(diagnosis, snapshot));
            setStatusVisual(StatusTone.WARNING);
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
            case LOCKUP_PROBABLE:
                return getString(R.string.lockup_probable_detail);
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
        setStatusVisual(StatusTone.ERROR);
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
            setStatusVisual(StatusTone.ERROR);
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
        recoverButton.setAlpha(recoverButton.isEnabled() ? 1f : 0.35f);
        reportButton.setEnabled(!busy && lastSnapshot != null);
        reportButton.setAlpha(reportButton.isEnabled() ? 1f : 0.55f);
        projectButton.setEnabled(!busy);
        projectButton.setAlpha(projectButton.isEnabled() ? 1f : 0.55f);
        themeToggle.setEnabled(!busy);
        themeToggle.setAlpha(busy ? 0.55f : 1f);
        statusSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusInfoButton.setVisibility(busy ? View.GONE : View.VISIBLE);
        if (busy) statusDetail.setVisibility(View.GONE);
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

    private void applyPalette() {
        if (isLightTheme) {
            colorBg=Color.rgb(246,242,232); colorCard=Color.rgb(255,253,247); colorCardSoft=Color.rgb(251,247,238); colorOutline=Color.rgb(222,213,197); colorAccent=Color.rgb(25,126,114); colorTextPrimary=Color.rgb(20,35,40); colorTextSecondary=Color.rgb(78,99,107); colorTextMuted=Color.rgb(111,131,138); colorButtonText=Color.rgb(5,43,39); colorChipValidated=Color.rgb(225,242,235); colorChipNeutral=Color.rgb(238,232,219); colorDetailText=Color.rgb(67,84,91); toneOk=Color.rgb(22,135,120); toneRecovery=Color.rgb(190,91,56); toneWarning=Color.rgb(156,100,7); toneError=Color.rgb(190,58,58); toneNeutral=Color.rgb(88,116,127);
        } else {
            colorBg=Color.rgb(9,13,16); colorCard=Color.rgb(17,24,29); colorCardSoft=Color.rgb(13,19,23); colorOutline=Color.rgb(38,52,60); colorAccent=Color.rgb(119,216,199); colorTextPrimary=Color.rgb(242,246,247); colorTextSecondary=Color.rgb(170,182,188); colorTextMuted=Color.rgb(116,132,140); colorButtonText=Color.rgb(4,33,30); colorChipValidated=Color.rgb(24,56,51); colorChipNeutral=Color.rgb(31,42,48); colorDetailText=Color.rgb(202,211,215); toneOk=Color.rgb(110,214,197); toneRecovery=Color.rgb(255,151,118); toneWarning=Color.rgb(238,190,103); toneError=Color.rgb(255,132,132); toneNeutral=Color.rgb(157,184,198);
        }
    }

    private void toggleTheme() {
        final boolean nextLight = !isLightTheme;
        final int targetBackground = themeBackground(nextLight);
        themeToggle.setEnabled(false);
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        View curtain = new View(this);
        curtain.setBackgroundColor(targetBackground);
        curtain.setPivotY(0f);
        curtain.setScaleY(0f);
        decor.addView(curtain, new ViewGroup.LayoutParams(-1, -1));
        curtain.animate().scaleY(1f).setDuration(560)
                .setInterpolator(new PathInterpolator(0.22f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    prefs().edit()
                            .putBoolean(KEY_LIGHT_THEME, nextLight)
                            .putBoolean(KEY_THEME_TRANSITION, true)
                            .apply();
                    recreate();
                }).start();
    }

    private void runThemeReveal(View decorView) {
        decorView.post(() -> {
            ViewGroup decor = (ViewGroup) decorView;
            View curtain = new View(this);
            curtain.setBackgroundColor(colorBg);
            decor.addView(curtain, new ViewGroup.LayoutParams(-1, -1));
            decorView.setAlpha(1f);
            curtain.animate().translationY(decor.getHeight()).setDuration(620)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .withEndAction(() -> decor.removeView(curtain)).start();
        });
    }

    private int themeBackground(boolean light) {
        return light ? Color.rgb(246, 242, 232) : Color.rgb(9, 13, 16);
    }

    private void toggleInfo(View body, TextView button) {
        boolean show = body.getVisibility() != View.VISIBLE;
        if (show) {
            body.setAlpha(0f);
            body.setVisibility(View.VISIBLE);
            body.animate().alpha(1f).setDuration(160).start();
        } else {
            body.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                body.setVisibility(View.GONE);
                body.setAlpha(1f);
            }).start();
        }
    }
    private void interactionFeedback(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private TextView infoButton(String description) { TextView v=iconButton("i",description); v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return v; }
    private TextView iconButton(String symbol,String description) { TextView v=text(symbol,17,colorTextPrimary,false); v.setGravity(Gravity.CENTER); v.setContentDescription(description); v.setBackground(roundRectStroke(Color.TRANSPARENT,colorOutline,999,1)); v.setMinWidth(dp(34)); v.setMinHeight(dp(34)); v.setHapticFeedbackEnabled(true); v.setSoundEffectsEnabled(true); return v; }
    private LinearLayout.LayoutParams iconMargin() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(34),dp(34)); p.setMargins(dp(10),0,0,0); return p; }
    private LinearLayout.LayoutParams spinnerParams() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(30),dp(30)); p.setMargins(dp(10),0,0,0); return p; }

    private enum StatusTone { OK, RECOVERY, WARNING, ERROR, NEUTRAL }

    private void setStatusVisual(StatusTone tone) {
        int targetColor;
        switch (tone) {
            case OK: targetColor = toneOk; break;
            case RECOVERY: targetColor = toneRecovery; break;
            case WARNING: targetColor = toneWarning; break;
            case ERROR: targetColor = toneError; break;
            default: targetColor = toneNeutral;
        }
        int targetFill = blendColors(colorCard, targetColor, isLightTheme ? 0.105f : 0.135f);
        if (statusAnimator != null) statusAnimator.cancel();
        final int startColor = currentStatusColor;
        final int startFill = currentStatusFill;
        final ArgbEvaluator evaluator = new ArgbEvaluator();
        statusDrawable.beginTransition(startFill, targetFill);
        statusAnimator = ValueAnimator.ofFloat(0f, 1f);
        statusAnimator.setDuration(1180);
        statusAnimator.setInterpolator(new PathInterpolator(0.18f, 0f, 0.12f, 1f));
        statusAnimator.addUpdateListener(animation -> {
            float f = (float) animation.getAnimatedValue();
            int color = (int) evaluator.evaluate(f, startColor, targetColor);
            statusTitle.setTextColor(color);
            statusRail.setBackground(roundRect(color, 999));
            statusDrawable.setProgress(f);
        });
        statusAnimator.start();
        currentStatusColor = targetColor;
        currentStatusFill = targetFill;
    }

    private GradientDrawable themeBackground() {
        int[] colors = isLightTheme
                ? new int[]{Color.rgb(248, 242, 231), Color.rgb(244, 239, 228), Color.rgb(236, 246, 241)}
                : new int[]{Color.rgb(6, 17, 24), Color.rgb(8, 14, 21), Color.rgb(22, 14, 27)};
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        drawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return drawable;
    }

    private GradientDrawable panelBackground() {
        int start = isLightTheme ? Color.argb(244, 255, 253, 247) : Color.argb(235, 15, 25, 33);
        int end = isLightTheme ? Color.argb(236, 249, 246, 238) : Color.argb(228, 12, 20, 28);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{start, end});
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), colorOutline);
        return drawable;
    }

    private Button toolButton(String value, int iconRes) {
        Button button = button(value, Color.TRANSPARENT, colorTextPrimary);
        button.setTextSize(12);
        button.setMinHeight(dp(58));
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setBackground(roundRectStroke(
                blendColors(colorCard, colorAccent, isLightTheme ? 0.02f : 0.035f),
                colorOutline, 14, 1));
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        android.graphics.drawable.Drawable icon = button.getCompoundDrawables()[0];
        if (icon != null) icon.setTint(colorAccent);
        button.setCompoundDrawablePadding(dp(7));
        return button;
    }

    private TextView sectionLabel(String value) {
        TextView view = text(value.toUpperCase(java.util.Locale.ROOT), 11,
                colorTextMuted, true);
        view.setLetterSpacing(0.12f);
        return view;
    }

    private TextView chip(String value, int background, int foreground) {
        TextView view = text(value, 11, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(roundRect(background, 999));
        return view;
    }

    private LinearLayout.LayoutParams chipMargin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(dp(8), 0, 0, 0);
        return params;
    }

    private Button outlineButton(String value) {
        Button button = button(value, colorCard, colorTextPrimary);
        button.setBackground(roundRectStroke(colorCard, colorOutline, 16, 1));
        return button;
    }

    private Button textButton(String value) {
        Button button = button(value, Color.TRANSPARENT, colorAccent);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(dp(42));
        return button;
    }

    private TextView metricValue() {
        TextView value = text("-", 12, colorTextPrimary, true);
        value.setSingleLine(true);
        value.setGravity(Gravity.CENTER_HORIZONTAL);
        return value;
    }

    private LinearLayout metricTile(String label, TextView value) {
        LinearLayout tile = column();
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(6), dp(7), dp(6), dp(7));
        tile.setBackground(roundRectStroke(
                blendColors(colorCardSoft, colorAccent, isLightTheme ? 0.015f : 0.03f),
                colorOutline, 12, 1));
        TextView labelView = text(label, 10, colorTextMuted, false);
        labelView.setGravity(Gravity.CENTER);
        labelView.setSingleLine(true);
        tile.addView(labelView);
        value.setPadding(0, dp(3), 0, 0);
        tile.addView(value);
        return tile;
    }

    private LinearLayout.LayoutParams metricParams(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        params.setMargins(left, 0, right, 0);
        return params;
    }

    private LinearLayout.LayoutParams weightedButton(int left, int right) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(left, 0, right, 0);
        return params;
    }

    private TextView addDetail(LinearLayout parent, int labelRes, int initialValueRes) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text(getString(labelRes), 13, colorTextMuted, false);
        TextView value = text(getString(initialValueRes), 13, colorTextPrimary, true);
        value.setGravity(Gravity.END);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(value, new LinearLayout.LayoutParams(0, -2, 1.35f));
        parent.addView(row, matchWrap(0, 0, 0, 10));
        return value;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(colorOutline);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return view;
    }

    private void toggleDetails() {
        boolean show = detailsBody.getVisibility() != View.VISIBLE;
        detailsBody.setVisibility(show ? View.VISIBLE : View.GONE);
        detailsToggle.setText(show ? R.string.details_hide_short : R.string.details_show_short);
    }

    private void updateDetails(DiagnosticEngine.DeviceInfo info,
                               DiagnosticEngine.Snapshot snapshot) {
        String firmware = info == null || info.firmware == null || info.firmware.trim().isEmpty()
                ? getString(R.string.unknown_value) : info.firmware.trim();
        detailFirmware.setText(firmware.contains(".377") ? ".377" : firmware);
        detailSupport.setText(info != null && DiagnosticEngine.isSupportedFirmware(info.firmware)
                ? R.string.detail_supported : R.string.detail_not_supported);
        if (snapshot == null) {
            detailScanner.setText("-");
            detailCarrier.setText("-");
            detailAps.setText("-");
            return;
        }
        detailScanner.setText(snapshot.scannerIdle() ? "IdleState"
                : snapshot.scannerScanning() ? "ScanningState" : getString(R.string.detail_unknown));
        detailCarrier.setText(snapshot.carrierUp ? R.string.detail_active : R.string.detail_inactive);
        detailAps.setText(String.valueOf(snapshot.radioAps));
    }

    private int blendColors(int base, int tint, float amount) {
        return Color.rgb(
                Math.round(Color.red(base) * (1f - amount) + Color.red(tint) * amount),
                Math.round(Color.green(base) * (1f - amount) + Color.green(tint) * amount),
                Math.round(Color.blue(base) * (1f - amount) + Color.blue(tint) * amount));
    }

    private final class LiquidStatusDrawable extends Drawable {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint liquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();
        private final Path liquidPath = new Path();
        private int baseColor;
        private int targetColor;
        private final int outlineColor;
        private float progress = 1f;

        LiquidStatusDrawable(int baseColor, int outlineColor) {
            this.baseColor = baseColor;
            this.targetColor = baseColor;
            this.outlineColor = outlineColor;
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(outlineColor);
        }

        void beginTransition(int fromColor, int toColor) {
            baseColor = fromColor;
            targetColor = toColor;
            progress = 0f;
            invalidateSelf();
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            if (progress >= 0.999f) baseColor = targetColor;
            invalidateSelf();
        }

        @Override public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            float halfStroke = dp(0.5f);
            rect.set(bounds.left + halfStroke, bounds.top + halfStroke,
                    bounds.right - halfStroke, bounds.bottom - halfStroke);
            float radius = dp(18);
            basePaint.setColor(baseColor);
            canvas.drawRoundRect(rect, radius, radius, basePaint);

            if (progress > 0.01f && progress < 0.999f) {
                clipPath.reset();
                clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipPath(clipPath);
                liquidPaint.setColor(targetColor);
                float boundary = rect.left + rect.width() * progress;
                float amp = dp(9) * (0.72f + 0.28f * (1f - progress));
                float phase = progress * (float) Math.PI * 2.15f;
                liquidPath.reset();
                liquidPath.moveTo(rect.left, rect.top);
                liquidPath.lineTo(boundary + (float) Math.sin(phase) * amp, rect.top);
                final int segments = 22;
                for (int i = 1; i <= segments; i++) {
                    float y = rect.top + rect.height() * i / segments;
                    float x = boundary + (float) Math.sin(phase + i * 0.72f) * amp;
                    liquidPath.lineTo(x, y);
                }
                liquidPath.lineTo(rect.left, rect.bottom);
                liquidPath.close();
                canvas.drawPath(liquidPath, liquidPaint);
                canvas.restore();
            } else if (progress >= 0.999f) {
                basePaint.setColor(targetColor);
                canvas.drawRoundRect(rect, radius, radius, basePaint);
            }
            canvas.drawRoundRect(rect, radius, radius, strokePaint);
        }

        @Override public void setAlpha(int alpha) {
            basePaint.setAlpha(alpha);
            liquidPaint.setAlpha(alpha);
            strokePaint.setAlpha(alpha);
        }

        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            basePaint.setColorFilter(colorFilter);
            liquidPaint.setColorFilter(colorFilter);
            strokePaint.setColorFilter(colorFilter);
        }

        @Override public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    private GradientDrawable roundRectStroke(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable drawable = roundRect(fill, radiusDp);
        drawable.setStroke(dp(strokeDp), stroke);
        return drawable;
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
        view.setTypeface(android.graphics.Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif",
                android.graphics.Typeface.NORMAL));
        view.setLineSpacing(0, 1.10f);
        return view;
    }

    private Button button(String value, int background, int foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setMinHeight(dp(54));
        button.setBackground(roundRect(background, 16));
        button.setHapticFeedbackEnabled(true);
        button.setSoundEffectsEnabled(true);
        return button;
    }

    private GradientDrawable roundRect(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private LinearLayout.LayoutParams weightedCard(float weight, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, weight);
        params.setMargins(0, 0, 0, dp(bottomDp));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
