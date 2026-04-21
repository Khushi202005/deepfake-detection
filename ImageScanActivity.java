package com.deepguard.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.models.ScanHistory;
import com.deepguard.app.models.ScanResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.SessionManager;

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

public class ImageScanActivity extends AppCompatActivity {

    private static final String TAG = "ImageScanActivity";

    // ── Views ───────────────────────────────────────────────────
    private ImageView    selectedImageView;
    private TextView     tvFileName;
    private LinearLayout imagePlaceholder;
    private Button       btnSelectFile;
    private Button       btnScan;
    private CardView     cardResult;
    private TextView     tvResult;
    private TextView     tvConfidence;
    private ProgressBar  progressBar;
    private TextView     tvProgress;

    // ── State ───────────────────────────────────────────────────
    private Uri            selectedImageUri;
    private String         selectedFileName = "image.jpg";
    private SessionManager sessionManager;
    private DatabaseHelper databaseHelper;   // ✅ ADDED — saves to local SQLite

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_scan);

        sessionManager = new SessionManager(this);
        databaseHelper = new DatabaseHelper(this);  // ✅ ADDED

        setupToolbar();
        initViews();
        setupImagePicker();
        setupClickListeners();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle(R.string.image_scan);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }
    }

    private void initViews() {
        selectedImageView = findViewById(R.id.selectedImageView);
        tvFileName        = findViewById(R.id.tvFileName);
        imagePlaceholder  = findViewById(R.id.imagePlaceholder);
        btnSelectFile     = findViewById(R.id.btnSelectFile);
        btnScan           = findViewById(R.id.btnScan);
        cardResult        = findViewById(R.id.cardResult);
        tvResult          = findViewById(R.id.tvResult);
        tvConfidence      = findViewById(R.id.tvConfidence);
        progressBar       = findViewById(R.id.progressBar);
        tvProgress        = findViewById(R.id.tvProgress);

        cardResult.setVisibility(View.GONE);
        btnScan.setEnabled(false);
        showProgress(false);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        displaySelectedImage();
                    }
                });
    }

    private void setupClickListeners() {
        btnSelectFile.setOnClickListener(v -> openImagePicker());
        btnScan.setOnClickListener(v -> performImageScan());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void displaySelectedImage() {
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(
                    getContentResolver(), selectedImageUri);

            selectedImageView.setImageBitmap(bitmap);

            if (imagePlaceholder != null)
                imagePlaceholder.setVisibility(View.GONE);

            selectedFileName = getFileName(selectedImageUri);
            tvFileName.setText(selectedFileName);
            tvFileName.setVisibility(View.VISIBLE);

            cardResult.setVisibility(View.GONE);
            btnScan.setEnabled(true);

        } catch (IOException e) {
            Log.e(TAG, "Error loading image", e);
            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String fileName = "image_" + System.currentTimeMillis() + ".jpg";
        try {
            String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
            android.database.Cursor cursor = getContentResolver()
                    .query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                fileName = cursor.getString(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting filename", e);
        }
        return fileName;
    }

    private void performImageScan() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        btnScan.setEnabled(false);
        btnScan.setText("Scanning...");
        cardResult.setVisibility(View.GONE);
        showProgress(true);

        try {
            File imageFile = getFileFromUri(selectedImageUri);
            if (imageFile == null || !imageFile.exists()) {
                Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show();
                resetScanButton();
                return;
            }

            RequestBody requestFile = RequestBody.create(
                    MediaType.parse("image/*"), imageFile);
            MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                    "file", imageFile.getName(), requestFile);
            RequestBody userIdBody = RequestBody.create(
                    MediaType.parse("text/plain"), sessionManager.getUserId());

            RetrofitClient.getInstance()
                    .getApiService()
                    .scanImage(userIdBody, filePart)
                    .enqueue(new Callback<ScanResponse>() {
                        @Override
                        public void onResponse(Call<ScanResponse> call,
                                               Response<ScanResponse> response) {
                            resetScanButton();

                            if (response.isSuccessful() && response.body() != null) {
                                ScanResponse body = response.body();
                                if (body.isSuccess() && body.getResult() != null) {
                                    displayResult(body);
                                    saveScanHistory(body);
                                } else {
                                    Toast.makeText(ImageScanActivity.this,
                                            body.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Toast.makeText(ImageScanActivity.this,
                                        "Server error. Try again.", Toast.LENGTH_SHORT).show();
                            }

                            // clean up temp file
                            if (imageFile.exists()) imageFile.delete();
                        }

                        @Override
                        public void onFailure(Call<ScanResponse> call, Throwable t) {
                            resetScanButton();
                            Log.e(TAG, "Network error", t);
                            Toast.makeText(ImageScanActivity.this,
                                    "Network error: " + t.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            if (imageFile.exists()) imageFile.delete();
                        }
                    });

        } catch (Exception e) {
            resetScanButton();
            Log.e(TAG, "Error preparing scan", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void displayResult(ScanResponse response) {
        cardResult.setVisibility(View.VISIBLE);

        String result     = response.getResult().getResult();
        double confidence = response.getResult().getConfidence();

        tvResult.setText(result);
        tvConfidence.setText(String.format("%.0f%%", confidence * 100));

        if ("REAL".equalsIgnoreCase(result)) {
            tvResult.setTextColor(getColor(R.color.success_green));
        } else {
            tvResult.setTextColor(getColor(R.color.error_red));
        }
    }

    // ✅ ADDED — saves scan to local SQLite so History + Analytics can read it
    private void saveScanHistory(ScanResponse scanResponse) {
        ScanHistory history = new ScanHistory();
        history.setUserId(sessionManager.getUserId());
        history.setFileType("image");                               // type = "image"
        history.setFileName(selectedFileName);                      // actual file name
        history.setFilePath("");                                    // path not needed
        history.setResult(scanResponse.getResult().getResult());    // "FAKE" or "REAL"
        history.setConfidence(scanResponse.getResult().getConfidence()); // 0.0 - 1.0
        history.setTimestamp(
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(new Date()));

        databaseHelper.addScanHistory(history);
        Log.d(TAG, "✅ Scan saved to history: " + scanResponse.getResult().getResult());
    }

    private File getFileFromUri(Uri uri) {
        try {
            File tempFile = new File(getCacheDir(),
                    "scan_" + System.currentTimeMillis() + ".jpg");
            InputStream in  = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            FileOutputStream out = new FileOutputStream(tempFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            out.close();
            in.close();
            return tempFile;
        } catch (IOException e) {
            Log.e(TAG, "URI to file error", e);
            return null;
        }
    }

    private void resetScanButton() {
        showProgress(false);
        btnScan.setEnabled(true);
        btnScan.setText(getString(R.string.scan_now));
    }

    private void showProgress(boolean show) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (tvProgress != null)
            tvProgress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}