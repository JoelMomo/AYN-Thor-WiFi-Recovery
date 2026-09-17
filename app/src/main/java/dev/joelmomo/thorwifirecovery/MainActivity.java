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
import android.view.Gravity;
import android.view.View;
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

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView statusTitle;
    private TextView statusDetail;
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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        isLightTheme = prefs().getBoolean(KEY_LIGHT_THEME, false);
        applyPalette();
        getWindow().setStatusBarColor(colorBg);
        getWindow().setNavigationBarColor(colorBg);
        getWindow().getDecorView().setSystemUiVisibility(isLightTheme
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                : 0);
        buildUi();
        boolean pending = prefs().getBoolean(KEY_RECOVERY_PENDING, false);
        runProbe(pending, pending);
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void buildUi() {
        final int bg = colorBg;
        final int card = colorCard;
        final int cardSoft = colorCardSoft;
        final int outline = colorOutline;
        final int accent = colorAccent;
        final int textPrimary = colorTextPrimary;
        final int textSecondary = colorTextSecondary;
        final int textMuted = colorTextMuted;
        final boolean wide = getResources().getConfiguration().screenWidthDp >= 700;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = column();
        root.setPadding(dp(wide ? 24 : 20), dp(16), dp(wide ? 24 : 20), dp(18));
        root.setBackgroundColor(bg);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_wifi_recovery_foreground);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        logoParams.setMargins(0, 0, dp(14), 0);
        header.addView(logo, logoParams);
        LinearLayout headerText = column();
        TextView title = text(getString(R.string.app_name), wide ? 25 : 24, textPrimary, true);
        headerText.addView(title);
        TextView subtitle = text(getString(R.string.subtitle), 13, textSecondary, false);
        subtitle.setPadding(0, dp(2), 0, 0);
        headerText.addView(subtitle);
        header.addView(headerText, new LinearLayout.LayoutParams(0, -2, 1f));

        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        chips.addView(chip(getString(R.string.chip_validated), colorChipValidated, accent));
        chips.addView(chip(getString(R.string.chip_offline), colorChipNeutral, textSecondary), chipMargin());
        themeToggle = iconButton(isLightTheme ? "\u2600" : "\u263E", getString(R.string.theme_toggle));
        themeToggle.setOnClickListener(v -> toggleTheme());
        if (wide) {
            header.addView(chips);
            header.addView(themeToggle, iconMargin());
        } else {
            header.addView(themeToggle);
        }
        root.addView(header);
        if (!wide) {
            chips.setGravity(Gravity.START);
            chips.setPadding(dp(62), dp(8), 0, 0);
            root.addView(chips);
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP);
        content.setPadding(0, dp(wide ? 14 : 16), 0, 0);
        LinearLayout left = column();
        LinearLayout right = column();
        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.HORIZONTAL);
        statusCard.setBackground(roundRectStroke(card, outline, 18, 1));
        statusRail = new View(this);
        statusRail.setBackground(roundRect(accent, 999));
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(4), -1);
        railParams.setMargins(0, dp(12), 0, dp(12));
        statusCard.addView(statusRail, railParams);

        LinearLayout statusContent = column();
        statusContent.setPadding(dp(18), dp(14), dp(18), dp(16));
        statusContent.addView(sectionLabel(getString(R.string.section_status)));
        LinearLayout statusTitleRow = new LinearLayout(this);
        statusTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        statusTitle = text(getString(R.string.status_checking), 21, accent, true);
        statusTitle.setPadding(0, dp(6), 0, 0);
        statusTitleRow.addView(statusTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        statusSpinner = new ProgressBar(this);
        statusSpinner.setIndeterminate(true);
        statusSpinner.getIndeterminateDrawable().setTint(accent);
        statusSpinner.setVisibility(View.GONE);
        statusTitleRow.addView(statusSpinner, spinnerParams());
        statusInfoButton = infoButton(getString(R.string.status_info));
        statusInfoButton.setVisibility(View.GONE);
        statusInfoButton.setOnClickListener(v -> toggleInfo(statusDetail, statusInfoButton));
        statusTitleRow.addView(statusInfoButton, iconMargin());
        statusContent.addView(statusTitleRow);
        statusDetail = text("", 13, colorDetailText, false);
        statusDetail.setPadding(0, dp(8), 0, 0);
        statusDetail.setVisibility(View.GONE);
        statusContent.addView(statusDetail);
        statusCard.addView(statusContent, new LinearLayout.LayoutParams(0, -2, 1f));
        left.addView(statusCard, matchWrap(0, 0, 0, 12));

        LinearLayout tools = column();
        tools.setPadding(dp(2), 0, dp(2), 0);
        tools.addView(sectionLabel(getString(R.string.section_tools)));
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setPadding(0, dp(8), 0, 0);
        diagnoseButton = outlineButton(getString(R.string.diagnose));
        reportButton = outlineButton(getString(R.string.copy_report));
        reportButton.setEnabled(false);
        diagnoseButton.setOnClickListener(v -> runProbe(true, false));
        reportButton.setOnClickListener(v -> copyDiagnosticReport());
        toolRow.addView(diagnoseButton, weightedButton(0, dp(5)));
        toolRow.addView(reportButton, weightedButton(dp(5), 0));
        tools.addView(toolRow);
        left.addView(tools);

        LinearLayout recoveryCard = column();
        recoveryCard.setPadding(dp(18), dp(15), dp(18), dp(17));
        recoveryCard.setBackground(roundRectStroke(cardSoft, outline, 18, 1));
        LinearLayout recoveryHeader = new LinearLayout(this);
        recoveryHeader.setGravity(Gravity.CENTER_VERTICAL);
        recoveryHeader.addView(sectionLabel(getString(R.string.section_recovery)), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView recoveryInfo = infoButton(getString(R.string.recovery_info));
        recoveryHeader.addView(recoveryInfo);
        recoveryCard.addView(recoveryHeader);
        TextView recoveryHint = text(getString(R.string.recovery_hint), 13, textSecondary, false);
        recoveryHint.setPadding(0, dp(8), 0, dp(12));
        recoveryHint.setVisibility(View.GONE);
        recoveryInfo.setOnClickListener(v -> toggleInfo(recoveryHint, recoveryInfo));
        recoveryCard.addView(recoveryHint);
        recoverButton = button(getString(R.string.recover), accent, colorButtonText);
        recoverButton.setEnabled(false);
        recoverButton.setOnClickListener(v -> confirmRecovery());
        recoveryCard.addView(recoverButton);
        right.addView(recoveryCard, matchWrap(0, 0, 0, 12));

        LinearLayout detailsCard = column();
        detailsCard.setPadding(dp(18), dp(8), dp(18), dp(10));
        detailsCard.setBackground(roundRectStroke(cardSoft, outline, 18, 1));
        detailsToggle = textButton(getString(R.string.details_show));
        detailsToggle.setOnClickListener(v -> toggleDetails());
        detailsCard.addView(detailsToggle);
        detailsBody = column();
        detailsBody.setVisibility(View.GONE);
        detailsBody.setPadding(0, dp(8), 0, 0);
        detailFirmware = addDetail(detailsBody, R.string.detail_firmware, R.string.unknown_value);
        detailSupport = addDetail(detailsBody, R.string.detail_support, R.string.detail_not_checked);
        detailScanner = addDetail(detailsBody, R.string.detail_scanner, R.string.detail_not_checked);
        detailCarrier = addDetail(detailsBody, R.string.detail_carrier, R.string.detail_not_checked);
        detailAps = addDetail(detailsBody, R.string.detail_radio_aps, R.string.detail_not_checked);
        detailsBody.addView(divider());
        TextView privacy = text(getString(R.string.privacy_note), 12, textMuted, false);
        privacy.setPadding(0, dp(10), 0, dp(2));
        detailsBody.addView(privacy);
        projectButton = textButton(getString(R.string.open_project));
        projectButton.setOnClickListener(v -> openProject());
        detailsBody.addView(projectButton);
        detailsCard.addView(detailsBody);
        right.addView(detailsCard);

        if (wide) {
            content.addView(left, new LinearLayout.LayoutParams(0, -2, 1.12f));
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 0.88f);
            rightParams.setMargins(dp(12), 0, 0, 0);
            content.addView(right, rightParams);
        } else {
            content.addView(left, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(-1, -2);
            rightParams.setMargins(0, dp(14), 0, 0);
            content.addView(right, rightParams);
        }
        root.addView(content);

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(2), dp(12), dp(2), 0);
        buildInfo = text(getString(R.string.build_info, BuildConfig.VERSION_NAME,
                getString(R.string.unknown_value)), 10, textMuted, false);
        TextView about = text(getString(R.string.about), 10, textMuted, false);
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
            colorBg=Color.rgb(244,247,248); colorCard=Color.WHITE; colorCardSoft=Color.rgb(249,251,251); colorOutline=Color.rgb(211,223,227); colorAccent=Color.rgb(25,126,114); colorTextPrimary=Color.rgb(20,35,40); colorTextSecondary=Color.rgb(78,99,107); colorTextMuted=Color.rgb(111,131,138); colorButtonText=Color.rgb(5,43,39); colorChipValidated=Color.rgb(222,244,239); colorChipNeutral=Color.rgb(231,237,239); colorDetailText=Color.rgb(67,84,91); toneOk=Color.rgb(22,135,120); toneRecovery=Color.rgb(190,91,56); toneWarning=Color.rgb(156,100,7); toneError=Color.rgb(190,58,58); toneNeutral=Color.rgb(88,116,127);
        } else {
            colorBg=Color.rgb(9,13,16); colorCard=Color.rgb(17,24,29); colorCardSoft=Color.rgb(13,19,23); colorOutline=Color.rgb(38,52,60); colorAccent=Color.rgb(119,216,199); colorTextPrimary=Color.rgb(242,246,247); colorTextSecondary=Color.rgb(170,182,188); colorTextMuted=Color.rgb(116,132,140); colorButtonText=Color.rgb(4,33,30); colorChipValidated=Color.rgb(24,56,51); colorChipNeutral=Color.rgb(31,42,48); colorDetailText=Color.rgb(202,211,215); toneOk=Color.rgb(110,214,197); toneRecovery=Color.rgb(255,151,118); toneWarning=Color.rgb(238,190,103); toneError=Color.rgb(255,132,132); toneNeutral=Color.rgb(157,184,198);
        }
    }

    private void toggleTheme() { prefs().edit().putBoolean(KEY_LIGHT_THEME, !isLightTheme).apply(); recreate(); }
    private void toggleInfo(View body, TextView button) { boolean show=body.getVisibility()!=View.VISIBLE; body.setVisibility(show?View.VISIBLE:View.GONE); }
    private TextView infoButton(String description) { TextView v=iconButton("i",description); v.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return v; }
    private TextView iconButton(String symbol,String description) { TextView v=text(symbol,17,colorTextPrimary,false); v.setGravity(Gravity.CENTER); v.setContentDescription(description); v.setBackground(roundRectStroke(Color.TRANSPARENT,colorOutline,999,1)); v.setMinWidth(dp(34)); v.setMinHeight(dp(34)); return v; }
    private LinearLayout.LayoutParams iconMargin() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(34),dp(34)); p.setMargins(dp(10),0,0,0); return p; }
    private LinearLayout.LayoutParams spinnerParams() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(30),dp(30)); p.setMargins(dp(10),0,0,0); return p; }

    private enum StatusTone { OK, RECOVERY, WARNING, ERROR, NEUTRAL }

    private void setStatusVisual(StatusTone tone) {
        int titleColor;
        switch (tone) {
            case OK:
                titleColor = toneOk;
                break;
            case RECOVERY:
                titleColor = toneRecovery;
                break;
            case WARNING:
                titleColor = toneWarning;
                break;
            case ERROR:
                titleColor = toneError;
                break;
            default:
                titleColor = toneNeutral;
        }
        statusTitle.setTextColor(titleColor);
        statusRail.setBackground(roundRect(titleColor, 999));
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
        detailsToggle.setText(show ? R.string.details_hide : R.string.details_show);
    }

    private void updateDetails(DiagnosticEngine.DeviceInfo info,
                               DiagnosticEngine.Snapshot snapshot) {
        String firmware = info == null || info.firmware == null || info.firmware.trim().isEmpty()
                ? getString(R.string.unknown_value) : info.firmware.trim();
        detailFirmware.setText(firmware);
        detailSupport.setText(info != null && DiagnosticEngine.isSupportedFirmware(info.firmware)
                ? R.string.detail_supported : R.string.detail_not_supported);
        if (snapshot == null) {
            detailScanner.setText(R.string.detail_not_checked);
            detailCarrier.setText(R.string.detail_not_checked);
            detailAps.setText(R.string.detail_not_checked);
            return;
        }
        detailScanner.setText(snapshot.scannerIdle() ? "IdleState"
                : snapshot.scannerScanning() ? "ScanningState" : getString(R.string.detail_unknown));
        detailCarrier.setText(snapshot.carrierUp ? R.string.detail_active : R.string.detail_inactive);
        detailAps.setText(String.valueOf(snapshot.radioAps));
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
