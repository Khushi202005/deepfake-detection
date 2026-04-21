package com.deepguard.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.models.ScanHistory;
import com.deepguard.app.models.ScanResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.ErrorHandler;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AudioScanActivity extends AppCompatActivity {

    private static final String TAG = "AudioScanActivity";
    private static final int REQUEST_AUDIO_PICK = 101;
    private static final int PERMISSION_REQUEST_CODE = 102;

    private TextView tvFileName, tvProgress, tvResult, tvConfidence;
    private Button btnSelectFile, btnScan;
    private ProgressBar progressBar;
    private CardView cardResult;

    private Uri selectedAudioUri;
    private SessionManager sessionManager;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_audio_scan);

        sessionManager = new SessionManager(this);
        databaseHelper = new DatabaseHelper(this);

        setupToolbar();
        initViews();
        setupClickListeners();
        checkPermissions();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Audio Scan");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        tvFileName    = findViewById(R.id.tvFileName);
        tvProgress    = findViewById(R.id.tvProgress);
        tvResult      = findViewById(R.id.tvResult);
        tvConfidence  = findViewById(R.id.tvConfidence);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnScan       = findViewById(R.id.btnScan);
        progressBar   = findViewById(R.id.progressBar);
        cardResult    = findViewById(R.id.cardResult);

        cardResult.setVisibility(View.GONE);
        btnScan.setEnabled(false);
    }

    private void setupClickListeners() {
        btnSelectFile.setOnClickListener(v -> openAudioPicker());
        btnScan.setOnClickListener(v -> {
            if (selectedAudioUri != null) {
                performScan();
            } else {
                Toast.makeText(this, "Please select an audio file first", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO},
                        PERMISSION_REQUEST_CODE);
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission required to select audio files",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openAudioPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_AUDIO_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_AUDIO_PICK && resultCode == RESULT_OK && data != null) {
            selectedAudioUri = data.getData();
            if (selectedAudioUri != null) {
                String fileName = getFileName(selectedAudioUri);
                tvFileName.setText(fileName);
                tvFileName.setVisibility(View.VISIBLE);
                btnScan.setEnabled(true);
                cardResult.setVisibility(View.GONE);
            }
        }
    }

    private String getFileName(Uri uri) {
        String fileName = "audio_file";
        try {
            String[] projection = {android.provider.MediaStore.Audio.Media.DISPLAY_NAME};
            android.database.Cursor cursor = getContentResolver().query(
                    uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(
                        android.provider.MediaStore.Audio.Media.DISPLAY_NAME);
                fileName = cursor.getString(columnIndex);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting filename", e);
        }
        return fileName;
    }

    private void performScan() {
        if (selectedAudioUri == null) {
            Toast.makeText(this, "Please select an audio file first", Toast.LENGTH_SHORT).show();
            return;
        }

        showProgress(true);

        try {
            File audioFile = getFileFromUri(selectedAudioUri);
            if (audioFile == null || !audioFile.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                showProgress(false);
                return;
            }

            RequestBody requestFile = RequestBody.create(MediaType.parse("audio/*"), audioFile);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", audioFile.getName(), requestFile);
            RequestBody userIdBody = RequestBody.create(MediaType.parse("text/plain"), sessionManager.getUserId());

            RetrofitClient.getInstance().getApiService().scanAudio(userIdBody, filePart)
                    .enqueue(new Callback<ScanResponse>() {
                        @Override
                        public void onResponse(Call<ScanResponse> call, Response<ScanResponse> response) {
                            showProgress(false);

                            if (!response.isSuccessful()) {
                                ErrorHandler.handleApiError(AudioScanActivity.this, response);
                                if (audioFile.exists()) audioFile.delete();
                                return;
                            }

                            if (response.body() != null) {
                                ScanResponse scanResponse = response.body();
                                if (scanResponse.isSuccess() && scanResponse.getResult() != null) {
                                    displayResult(scanResponse);
                                    saveScanHistory(scanResponse, audioFile.getName()); // ✅ save here
                                } else {
                                    Toast.makeText(AudioScanActivity.this,
                                            "Error: " + scanResponse.getMessage(),
                                            Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(AudioScanActivity.this,
                                        "Scan failed: " + response.message(),
                                        Toast.LENGTH_SHORT).show();
                            }

                            if (audioFile.exists()) audioFile.delete();
                        }

                        @Override
                        public void onFailure(Call<ScanResponse> call, Throwable t) {
                            showProgress(false);
                            Log.e(TAG, "Network error", t);
                            Toast.makeText(AudioScanActivity.this,
                                    "Network error: " + t.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            if (audioFile.exists()) audioFile.delete();
                        }
                    });

        } catch (Exception e) {
            showProgress(false);
            Log.e(TAG, "Error preparing scan", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private File getFileFromUri(Uri uri) {
        try {
            File tempFile = new File(getCacheDir(), "temp_audio_" + System.currentTimeMillis() + ".wav");

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;

            FileOutputStream outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();

            return tempFile;

        } catch (IOException e) {
            Log.e(TAG, "Error converting URI to file", e);
            return null;
        }
    }

    private void displayResult(ScanResponse scanResponse) {
        cardResult.setVisibility(View.VISIBLE);

        String result = scanResponse.getResult().getResult();
        double confidence = scanResponse.getResult().getConfidence();

        tvResult.setText(result);
        tvConfidence.setText(String.format("Confidence: %.2f%%", confidence * 100));

        if ("REAL".equalsIgnoreCase(result)) {
            tvResult.setTextColor(getColor(R.color.success_green));
        } else {
            tvResult.setTextColor(getColor(R.color.error_red));
        }
    }

    // ✅ Separate method — same pattern as VideoScanActivity
    private void saveScanHistory(ScanResponse scanResponse, String fileName) {
        ScanHistory history = new ScanHistory();
        history.setUserId(sessionManager.getUserId());
        history.setFileType("audio");
        history.setFileName(fileName);
        history.setFilePath("");
        history.setResult(scanResponse.getResult().getResult());
        history.setConfidence(scanResponse.getResult().getConfidence());
        history.setTimestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date()));

        databaseHelper.addScanHistory(history);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        tvProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        tvProgress.setText(show ? "Analyzing audio..." : "");
        btnScan.setEnabled(!show);
        btnSelectFile.setEnabled(!show);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}