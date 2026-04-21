package com.deepguard.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.utils.Constants;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private SwitchCompat switchDarkMode, switchAutoDelete, switchNotifications;
    private LinearLayout cardClearHistory, cardPrivacyPolicy, btnLogout;

    private SessionManager sessionManager;
    private ThemeManager themeManager;
    private DatabaseHelper databaseHelper;

    // ── Android 13+ notification permission launcher ─────────────────────────
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            // Permission granted — save preference & setup channel
                            sessionManager.setNotificationsEnabled(true);
                            setupNotificationChannel();
                            switchNotifications.setChecked(true);
                            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
                        } else {
                            // Permission denied — revert switch silently
                            switchNotifications.setChecked(false);
                            sessionManager.setNotificationsEnabled(false);
                            Toast.makeText(this,
                                    "Notification permission denied. Enable from app settings.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        themeManager = new ThemeManager(this);
        themeManager.applyTheme();

        setContentView(R.layout.activity_settings);

        sessionManager  = new SessionManager(this);
        databaseHelper  = new DatabaseHelper(this);

        setupToolbar();
        initViews();
        loadSettings();
        setupListeners();
    }

    // ── Toolbar ──────────────────────────────────────────────────────────────
    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── initViews ────────────────────────────────────────────────────────────
    private void initViews() {
        switchDarkMode      = findViewById(R.id.switchDarkMode);
        switchAutoDelete    = findViewById(R.id.switchAutoDelete);
        switchNotifications = findViewById(R.id.switchNotifications);

        cardClearHistory  = findViewById(R.id.cardClearHistory);
        cardPrivacyPolicy = findViewById(R.id.cardPrivacyPolicy);
        btnLogout         = findViewById(R.id.btnLogout);
    }

    // ── Load saved preferences into UI ───────────────────────────────────────
    private void loadSettings() {
        switchDarkMode.setChecked(sessionManager.getThemeMode() == Constants.THEME_DARK);
        switchAutoDelete.setChecked(sessionManager.isAutoDeleteEnabled());
        switchNotifications.setChecked(sessionManager.areNotificationsEnabled());
    }

    // ── Listeners ────────────────────────────────────────────────────────────
    private void setupListeners() {

        // ── Dark Mode ────────────────────────────────────────────────────────
        switchDarkMode.setOnCheckedChangeListener((btn, isChecked) -> {
            int theme = isChecked ? Constants.THEME_DARK : Constants.THEME_LIGHT;
            themeManager.setTheme(this, theme);
        });

        // ── Auto Delete ──────────────────────────────────────────────────────
        // When enabled: delete scan history older than 30 days immediately,
        // and on every app open (called from onResume).
        switchAutoDelete.setOnCheckedChangeListener((btn, isChecked) -> {
            sessionManager.setAutoDelete(isChecked);

            if (isChecked) {
                // Run deletion right away so user sees it working
                int deleted = deleteOldHistory();
                String msg = deleted > 0
                        ? "Auto-delete enabled. Removed " + deleted + " old record(s)."
                        : "Auto-delete enabled. No old records to remove.";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Auto-delete disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Notifications ────────────────────────────────────────────────────
        switchNotifications.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                enableNotifications();
            } else {
                sessionManager.setNotificationsEnabled(false);
                disableNotificationChannel();
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Clear History ────────────────────────────────────────────────────
        cardClearHistory.setOnClickListener(v -> showClearHistoryDialog());

        // ── Privacy Policy ───────────────────────────────────────────────────
        cardPrivacyPolicy.setOnClickListener(v ->
                startActivity(new Intent(this, PrivacyPolicyActivity.class)));

        // ── Logout ───────────────────────────────────────────────────────────
        btnLogout.setOnClickListener(v -> {
            sessionManager.logoutUser();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CLEAR HISTORY
    // ════════════════════════════════════════════════════════════════════════
    private void showClearHistoryDialog() {
        if (isFinishing() || isDestroyed()) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to clear all scan history? This cannot be undone.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    String userId = sessionManager.getUserId();
                    if (userId != null) {
                        databaseHelper.clearHistory(userId);   // ✅ FIXED: actual DB call
                        Toast.makeText(this, "History cleared successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Error: user not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  AUTO DELETE  — deletes records older than 30 days
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Deletes history rows older than 30 days for the current user.
     * Call this from onResume() so it runs every time the app opens
     * while auto-delete is enabled.
     *
     * @return number of records deleted
     */
    private int deleteOldHistory() {
        String userId = sessionManager.getUserId();
        if (userId == null) return 0;
        return databaseHelper.deleteOldHistory(userId, 30); // 30-day cutoff
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Auto-delete runs silently every time screen is shown (if enabled)
        if (sessionManager.isAutoDeleteEnabled()) {
            deleteOldHistory();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NOTIFICATIONS
    // ════════════════════════════════════════════════════════════════════════
    private void enableNotifications() {
        // Android 13+ (API 33) requires POST_NOTIFICATIONS permission at runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                // Already have permission
                sessionManager.setNotificationsEnabled(true);
                setupNotificationChannel();
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                // Ask for permission — result handled in notificationPermissionLauncher
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Below Android 13 — no runtime permission needed
            sessionManager.setNotificationsEnabled(true);
            setupNotificationChannel();
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
        }
    }

    /** Creates the notification channel (required for Android 8+). */
    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,          // e.g. "deepguard_channel"
                    "DeepGuard Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Scan result notifications");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** Deletes the notification channel so no more notifications appear. */
    private void disableNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.deleteNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID);
            }
        }
    }
}