package com.deepguard.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.deepguard.app.profile.ProfileActivity;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class DashboardActivity extends AppCompatActivity {

    private static final String TAG = "DashboardActivity";

    // TFLite
    private Interpreter tflite;

    // UI Elements
    private Toolbar toolbar;
    private FloatingActionButton btnChatBot;
    private CardView cardImageScan, cardVideoScan, cardAudioScan, cardUrlScan;
    private CardView cardHistory, cardAnalytics, cardSettings, cardAboutUs;
    private CardView cardProfile;
    private MaterialCardView cardNotices;

    // Helpers
    private SessionManager sessionManager;
    private ThemeManager themeManager;

    // ✅ Notification permission launcher (Android 13+)
    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    Log.d(TAG, "✅ Notification permission granted");
                } else {
                    Log.d(TAG, "❌ Notification permission denied");
                    Toast.makeText(this,
                            "Enable notifications to receive admin alerts",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        themeManager = new ThemeManager(this);
        themeManager.applyTheme();

        setContentView(R.layout.activity_dashboard);

        sessionManager = new SessionManager(this);

        setupToolbar();
        initViews();
        setupClickListeners();

        // ✅ Request notification permission on Android 13+
        requestNotificationPermission();

        // Load TFLite Model
        try {
            tflite = new Interpreter(loadModelFile());
            Log.d("MODEL", "Interpreter ready");
        } catch (Exception e) {
            Log.e("MODEL", "Interpreter error", e);
        }
    }

    // ✅ Request POST_NOTIFICATIONS at runtime (required for Android 13+)
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                Log.d(TAG, "✅ Notification permission already granted");
            }
        }
    }

    private MappedByteBuffer loadModelFile() {
        try {
            AssetFileDescriptor fileDescriptor = getAssets().openFd("deepfake_model.tflite");
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.e("ModelLoader", "Error loading model file", e);
            Toast.makeText(this, "Failed to load detection model", Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.dashboard);
        }
    }

    private void initViews() {
        cardImageScan = findViewById(R.id.cardImageScan);
        cardVideoScan = findViewById(R.id.cardVideoScan);
        cardAudioScan = findViewById(R.id.cardAudioScan);
        cardUrlScan   = findViewById(R.id.cardUrlScan);
        cardHistory   = findViewById(R.id.cardHistory);
        cardAnalytics = findViewById(R.id.cardAnalytics);
        cardSettings  = findViewById(R.id.cardSettings);
        cardAboutUs   = findViewById(R.id.cardAboutUs);
        cardProfile   = findViewById(R.id.cardProfile);
        cardNotices   = findViewById(R.id.cardNotices);
        btnChatBot    = findViewById(R.id.btnChatBot);
    }

    private void setupClickListeners() {
        cardImageScan.setOnClickListener(v -> startActivity(new Intent(this, ImageScanActivity.class)));
        cardVideoScan.setOnClickListener(v -> startActivity(new Intent(this, VideoScanActivity.class)));
        cardAudioScan.setOnClickListener(v -> startActivity(new Intent(this, AudioScanActivity.class)));
        cardUrlScan.setOnClickListener(v   -> startActivity(new Intent(this, UrlScanActivity.class)));
        cardHistory.setOnClickListener(v   -> startActivity(new Intent(this, HistoryActivity.class)));
        cardAnalytics.setOnClickListener(v -> startActivity(new Intent(this, AnalyticsActivity.class)));
        cardSettings.setOnClickListener(v  -> startActivity(new Intent(this, SettingsActivity.class)));
        cardAboutUs.setOnClickListener(v   -> startActivity(new Intent(this, AboutUsActivity.class)));
        cardProfile.setOnClickListener(v   -> startActivity(new Intent(this, ProfileActivity.class)));

        if (cardNotices != null) {
            cardNotices.setOnClickListener(v ->
                    startActivity(new Intent(DashboardActivity.this, NoticeActivity.class)));
        }

        btnChatBot.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, ChatActivity.class)));
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.dashboard_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            showLogoutDialog();
            return true;
        } else if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showLogoutDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes", (dialog, which) -> performLogout()) // ✅ FIX
                .setNegativeButton("No", null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // Button color white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
    }

    private void performLogout() {
        sessionManager.logoutUser();
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton(R.string.yes, (dialog, which) -> super.onBackPressed())
                .setNegativeButton(R.string.no, null);

        AlertDialog dialog = builder.create();
        dialog.show();

        // YES/NO white karne ke liye
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
    }
}