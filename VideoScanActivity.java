package com.deepguard.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.models.ScanHistory;
import com.deepguard.app.models.ScanResponse;
import com.deepguard.app.models.ScanResult;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.Constants;
import com.deepguard.app.utils.FileUtils;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideoScanActivity extends AppCompatActivity {

    // ✅ TAG added for logging
    private static final String TAG = "VideoScanActivity";

    private Toolbar toolbar;
    private VideoView videoPreview;
    private TextView tvFileName, tvProgress, tvResult, tvConfidence;
    private Button btnSelectFile, btnScan;
    private ProgressBar progressBar;
    private CardView cardResult;

    // ✅ ADDED: frames related views
    private CardView cardFrames;
    private LinearLayout framesContainer;

    private SessionManager sessionManager;
    private DatabaseHelper databaseHelper;

    private Uri selectedVideoUri;
    private File selectedVideoFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_video_scan);

        sessionManager = new SessionManager(this);
        databaseHelper = new DatabaseHelper(this);

        setupToolbar();
        initViews();
        setupClickListeners();
        checkPermissions();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.video_scan);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initViews() {
        videoPreview    = findViewById(R.id.videoPreview);
        tvFileName      = findViewById(R.id.tvFileName);
        tvProgress      = findViewById(R.id.tvProgress);
        tvResult        = findViewById(R.id.tvResult);
        tvConfidence    = findViewById(R.id.tvConfidence);
        btnSelectFile   = findViewById(R.id.btnSelectFile);
        btnScan         = findViewById(R.id.btnScan);
        progressBar     = findViewById(R.id.progressBar);
        cardResult      = findViewById(R.id.cardResult);

        // ✅ ADDED: frames views
        cardFrames      = findViewById(R.id.cardFrames);
        framesContainer = findViewById(R.id.framesContainer);
    }

    private void setupClickListeners() {
        btnSelectFile.setOnClickListener(v -> openVideoPicker());

        btnScan.setOnClickListener(v -> {
            if (selectedVideoFile != null) {
                performScan();
            } else {
                Toast.makeText(this, R.string.error_no_file, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    Constants.REQUEST_PERMISSION);
        }
    }

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, Constants.REQUEST_VIDEO_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.REQUEST_VIDEO_PICK && resultCode == RESULT_OK && data != null) {
            selectedVideoUri = data.getData();

            if (selectedVideoUri != null) {
                videoPreview.setVideoURI(selectedVideoUri);
                videoPreview.start();

                String fileName = FileUtils.getFileName(this, selectedVideoUri);
                tvFileName.setText(fileName);

                long fileSize = FileUtils.getFileSize(this, selectedVideoUri);
                if (!FileUtils.isFileSizeValid(fileSize, Constants.TYPE_VIDEO)) {
                    Toast.makeText(this, Constants.ERROR_FILE_TOO_LARGE, Toast.LENGTH_SHORT).show();
                    selectedVideoFile = null;
                    btnScan.setEnabled(false);
                    return;
                }

                selectedVideoFile = FileUtils.copyFileToAppStorage(this, selectedVideoUri, fileName);
                btnScan.setEnabled(true);
                cardResult.setVisibility(View.GONE);

                // ✅ ADDED: frames extract karo jab video select ho
                extractVideoFrames();
            }
        }
    }

    // ✅ ADDED: Yeh poora function naya hai — video ke frames extract karta hai
    private void extractVideoFrames() {
        new Thread(() -> {
            try {
                Log.d(TAG, "🎬 Extracting frames from video...");

                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(this, selectedVideoUri);

                String durationStr   = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String frameCountStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);

                long durationMs  = Long.parseLong(durationStr);
                int totalFrames  = frameCountStr != null ? Integer.parseInt(frameCountStr) : 0;

                Log.d(TAG, "📊 Video duration: " + durationMs + "ms");
                Log.d(TAG, "📊 Total frames: " + totalFrames);

                int framesToExtract = Math.min(12, Math.max(10, totalFrames / 20));
                long intervalMs     = durationMs / framesToExtract;

                Log.d(TAG, "🎯 Will extract " + framesToExtract + " frames");

                // Pehle container clear karo
                runOnUiThread(() -> framesContainer.removeAllViews());

                int extractedCount = 0;

                for (int i = 0; i < framesToExtract; i++) {
                    try {
                        long timeUs  = (i * intervalMs) * 1000;
                        Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST);

                        if (frame == null) {
                            frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                        }

                        if (frame != null) {
                            extractedCount++;
                            final Bitmap finalFrame = frame;
                            final int frameNum      = i + 1;

                            runOnUiThread(() -> {
                                ImageView imageView = new ImageView(VideoScanActivity.this);
                                int sizePx = (int) (90 * getResources().getDisplayMetrics().density);
                                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
                                params.setMargins(6, 0, 6, 0);

                                imageView.setLayoutParams(params);
                                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                imageView.setImageBitmap(finalFrame);
                                imageView.setBackgroundResource(R.drawable.bg_edittext);
                                imageView.setClipToOutline(true);
                                imageView.setPadding(2, 2, 2, 2);

                                framesContainer.addView(imageView);
                                Log.d(TAG, "✅ Frame " + frameNum + " extracted");
                            });

                            Thread.sleep(50);
                        } else {
                            Log.w(TAG, "⚠️ Frame " + (i + 1) + " is null, skipping");
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "❌ Error extracting frame " + (i + 1), e);
                    }
                }

                retriever.release();

                final int finalExtractedCount = extractedCount;
                runOnUiThread(() -> {
                    if (finalExtractedCount > 0) {
                        cardFrames.setVisibility(View.VISIBLE);
                        Log.d(TAG, "🎉 Extracted " + finalExtractedCount + " frames");
                    } else {
                        cardFrames.setVisibility(View.GONE);
                        Log.w(TAG, "⚠️ No frames extracted");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "❌ Critical error in frame extraction", e);
                runOnUiThread(() -> {
                    cardFrames.setVisibility(View.GONE);
                    Toast.makeText(VideoScanActivity.this,
                            "Could not extract video frames", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void performScan() {
        showProgress(true);

        RequestBody userId   = RequestBody.create(MediaType.parse("text/plain"), sessionManager.getUserId());
        RequestBody fileBody = RequestBody.create(MediaType.parse("video/*"), selectedVideoFile);
        MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", selectedVideoFile.getName(), fileBody);

        RetrofitClient.getInstance().getApiService().scanVideo(userId, filePart)
                .enqueue(new Callback<ScanResponse>() {
                    @Override
                    public void onResponse(Call<ScanResponse> call, Response<ScanResponse> response) {
                        showProgress(false);

                        if (response.isSuccessful() && response.body() != null) {
                            ScanResponse scanResponse = response.body();

                            if (scanResponse.isSuccess() && scanResponse.getResult() != null) {
                                displayResult(scanResponse);
                                saveScanHistory(scanResponse);
                            } else {
                                Toast.makeText(VideoScanActivity.this,
                                        scanResponse.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(VideoScanActivity.this,
                                    Constants.ERROR_SERVER, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ScanResponse> call, Throwable t) {
                        showProgress(false);
                        Toast.makeText(VideoScanActivity.this,
                                Constants.ERROR_NETWORK, Toast.LENGTH_SHORT).show();
                        t.printStackTrace();
                    }
                });
    }

    private void displayResult(ScanResponse scanResponse) {
        cardResult.setVisibility(View.VISIBLE);
        ScanResult result = scanResponse.getResult();

        tvResult.setText(result.getResult());
        tvConfidence.setText(result.getFormattedConfidence());
        tvResult.setTextColor(getResources().getColor(
                result.isDeepfake() ? R.color.error : R.color.success));
    }

    private void saveScanHistory(ScanResponse scanResponse) {
        ScanResult result = scanResponse.getResult();

        ScanHistory history = new ScanHistory();
        history.setUserId(sessionManager.getUserId());
        history.setFileType(Constants.TYPE_VIDEO);
        history.setFileName(selectedVideoFile.getName());
        history.setFilePath(selectedVideoFile.getAbsolutePath());
        history.setResult(result.getResult());
        history.setConfidence(result.getConfidence());
        history.setTimestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()).format(new Date()));

        databaseHelper.addScanHistory(history);

        if (sessionManager.isAutoDeleteEnabled()) {
            FileUtils.deleteFile(selectedVideoFile);
        }
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        tvProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnScan.setEnabled(!show);
        btnSelectFile.setEnabled(!show);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}