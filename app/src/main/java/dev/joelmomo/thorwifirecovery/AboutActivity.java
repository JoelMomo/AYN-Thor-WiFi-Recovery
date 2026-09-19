package dev.joelmomo.thorwifirecovery;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AboutActivity extends Activity {
    private int bg;
    private int card;
    private int outline;
    private int primary;
    private int secondary;
    private int accent;
    private android.graphics.Typeface regular;
    private android.graphics.Typeface bold;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean light = getSharedPreferences("recovery_state", MODE_PRIVATE)
                .getBoolean("light_theme", false);
        if (light) {
            bg = Color.rgb(246, 242, 232);
            card = Color.rgb(255, 253, 247);
            outline = Color.rgb(222, 213, 197);
            primary = Color.rgb(20, 35, 40);
            secondary = Color.rgb(78, 99, 107);
            accent = Color.rgb(25, 126, 114);
        } else {
            bg = Color.rgb(9, 13, 16);
            card = Color.rgb(17, 24, 29);
            outline = Color.rgb(38, 52, 60);
            primary = Color.rgb(242, 246, 247);
            secondary = Color.rgb(170, 182, 188);
            accent = Color.rgb(119, 216, 199);
        }
        SystemBars.apply(getWindow(), bg, light);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = column();
        root.setPadding(dp(30), dp(24), dp(30), dp(28));
        scroll.addView(root, new ViewGroup.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_brand_icon);
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titles = column();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.setMargins(dp(16), 0, 0, 0);
        TextView title = text(getString(R.string.about_title), 26, primary, true);
        TextView subtitle = text(getString(R.string.about_subtitle), 13, secondary, false);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, titleParams);

        Button close = button(getString(R.string.close));
        close.setOnClickListener(v -> finish());
        header.addView(close, new LinearLayout.LayoutParams(-2, dp(42)));
        root.addView(header);

        LinearLayout tech = card();
        tech.addView(section(getString(R.string.about_section_technical)));
        tech.addView(row(getString(R.string.about_app_version),
                BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"));
        tech.addView(row(getString(R.string.detail_android),
                android.os.Build.VERSION.RELEASE + " / API " + android.os.Build.VERSION.SDK_INT));
        tech.addView(row(getString(R.string.detail_model), android.os.Build.MODEL));
        tech.addView(row(getString(R.string.about_current_build), android.os.Build.DISPLAY));
        tech.addView(row(getString(R.string.about_validated_firmware),
                DiagnosticEngine.SUPPORTED_FIRMWARE));
        addCard(root, tech);

        LinearLayout recovery = card();
        recovery.addView(section(getString(R.string.section_recovery)));
        recovery.addView(paragraph(getString(R.string.about_recovery_strategy)));
        addCard(root, recovery);

        LinearLayout privacy = card();
        privacy.addView(section(getString(R.string.about_section_privacy)));
        privacy.addView(paragraph(getString(R.string.about_privacy_detail)));
        addCard(root, privacy);

        LinearLayout project = card();
        project.addView(section(getString(R.string.about_section_project)));
        project.addView(paragraph(getString(R.string.about_project_detail)));
        Button github = button(getString(R.string.about_open_github));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, dp(46));
        gp.setMargins(0, dp(12), 0, 0);
        project.addView(github, gp);
        github.setOnClickListener(v -> openGithub());
        addCard(root, project);

        setContentView(scroll);
    }

    private void openGithub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.project_url))));
        } catch (Exception e) {
            Toast.makeText(this, R.string.more_apps_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout card() {
        LinearLayout layout = column();
        layout.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(card);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(dp(1), outline);
        layout.setBackground(drawable);
        return layout;
    }

    private void addCard(LinearLayout root, View view) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(16), 0, 0);
        root.addView(view, p);
    }

    private LinearLayout row(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView left = text(label, 12, secondary, false);
        TextView right = text(value == null || value.trim().isEmpty()
                ? getString(R.string.unknown_value) : value, 12, primary, true);
        right.setGravity(Gravity.END);
        right.setTextIsSelectable(true);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 0.35f));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 0.65f));
        return row;
    }

    private TextView section(String value) {
        TextView text = text(value.toUpperCase(java.util.Locale.ROOT), 11, accent, true);
        text.setLetterSpacing(0.08f);
        text.setPadding(0, 0, 0, dp(5));
        return text;
    }

    private TextView paragraph(String value) {
        TextView text = text(value, 13, secondary, false);
        text.setLineSpacing(0f, 1.15f);
        return text;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(12);
        button.setTextColor(primary);
        button.setTypeface(typeface(true));
        button.setAllCaps(false);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), outline);
        button.setBackground(drawable);
        return button;
    }

    private TextView text(String value, int sp, int color, boolean isBold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(typeface(isBold));
        return view;
    }

    private android.graphics.Typeface typeface(boolean isBold) {
        if (regular == null) regular = getResources().getFont(R.font.nunito_variable);
        if (isBold) {
            if (bold == null) bold = android.graphics.Typeface.create(regular,
                    android.graphics.Typeface.BOLD);
            return bold;
        }
        return regular;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
