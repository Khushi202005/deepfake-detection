package com.deepguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.deepguard.app.R;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HomeActivity extends AppCompatActivity {

    private Button btnLogin, btnSignup, btnStartScanning;
    private CardView cardImageScan, cardVideoScan, cardAudioScan, cardUrlScan, cardAboutUs;
    private SessionManager sessionManager;
    private ThemeManager themeManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply theme
        themeManager = new ThemeManager(this);
        themeManager.applyTheme();

        setContentView(R.layout.activity_home);

        // Initialize session manager
        sessionManager = new SessionManager(this);

        // Initialize views
        initViews();

        // Setup click listeners
        setupClickListeners();
    }

    private void initViews() {
        btnLogin = findViewById(R.id.btnLogin);
        btnSignup = findViewById(R.id.btnSignup);
        btnStartScanning = findViewById(R.id.btnStartScanning);
        cardImageScan = findViewById(R.id.cardImageScan);
        cardVideoScan = findViewById(R.id.cardVideoScan);
        cardAudioScan = findViewById(R.id.cardAudioScan);
        cardUrlScan = findViewById(R.id.cardUrlScan);
        cardAboutUs = findViewById(R.id.cardAboutUs);
    }

    private void setupClickListeners() {
        // Login button
        btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, LoginActivity.class));
        });

        // Signup button
        btnSignup.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, SignupActivity.class));
        });

        // Start scanning button
        btnStartScanning.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(HomeActivity.this, DashboardActivity.class));
            } else {
                showLoginRequiredDialog();
            }
        });

        // Image scan card
        cardImageScan.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(HomeActivity.this, ImageScanActivity.class));
            } else {
                showLoginRequiredDialog();
            }
        });


        // Video scan card
        cardVideoScan.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(HomeActivity.this, VideoScanActivity.class));
            } else {
                showLoginRequiredDialog();
            }
        });

        // Audio scan card
        cardAudioScan.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(HomeActivity.this, AudioScanActivity.class));
            } else {
                showLoginRequiredDialog();
            }
        });

        // URL scan card
        cardUrlScan.setOnClickListener(v -> {
            if (sessionManager.isLoggedIn()) {
                startActivity(new Intent(HomeActivity.this, UrlScanActivity.class));
            } else {
                showLoginRequiredDialog();
            }
        });

        // About Us card
        cardAboutUs.setOnClickListener(v -> {
            startActivity(new Intent(HomeActivity.this, AboutUsActivity.class));
        });
    }

    private void showLoginRequiredDialog() {
        new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle(R.string.login_required)
                .setMessage(R.string.login_required_msg)
                .setPositiveButton(R.string.login, (dialog, which) -> {
                    startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                })
                .setNegativeButton(R.string.signup, (dialog, which) -> {
                    startActivity(new Intent(HomeActivity.this, SignupActivity.class));
                })
                .setNeutralButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If user is logged in, redirect to dashboard
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        }
    }
    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this, R.style.CustomDialogTheme)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    super.onBackPressed();   // ✅ REQUIRED
                    finishAffinity();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }


}