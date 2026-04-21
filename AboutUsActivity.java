package com.deepguard.app;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.deepguard.app.R;
import com.deepguard.app.utils.ThemeManager;

public class AboutUsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TextView tvAboutDescription, tvMissionDescription, tvVersion;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_about_us);

        setupToolbar();
        initViews();
        setupContent();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.about_us);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initViews() {
        tvAboutDescription = findViewById(R.id.tvAboutDescription);
        tvMissionDescription = findViewById(R.id.tvMissionDescription);
        tvVersion = findViewById(R.id.tvVersion); // ✅ ADD THIS
    }



    private void setupContent() {
        tvAboutDescription.setText(R.string.about_description);
        tvMissionDescription.setText(R.string.mission_description);
        tvVersion.setText(R.string.version);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}