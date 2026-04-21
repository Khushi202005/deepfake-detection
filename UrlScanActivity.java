package com.deepguard.app;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import com.deepguard.app.R;
import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.models.ScanHistory;
import com.deepguard.app.models.ScanRequest;
import com.deepguard.app.models.ScanResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.Constants;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import com.deepguard.app.utils.ValidationUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UrlScanActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private EditText etUrl;
    private TextView tvProgress, tvResult, tvConfidence;
    private Button btnScan;
    private ProgressBar progressBar;
    private CardView cardResult;
    private SessionManager sessionManager;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_url_scan);

        sessionManager = new SessionManager(this);
        databaseHelper = new DatabaseHelper(this);

        setupToolbar();
        initViews();
        setupClickListeners();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.url_scan);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initViews() {
        etUrl = findViewById(R.id.etUrl);
        tvProgress = findViewById(R.id.tvProgress);
        tvResult = findViewById(R.id.tvResult);
        tvConfidence = findViewById(R.id.tvConfidence);
        btnScan = findViewById(R.id.btnScan);
        progressBar = findViewById(R.id.progressBar);
        cardResult = findViewById(R.id.cardResult);
    }

    private void setupClickListeners() {
        btnScan.setOnClickListener(v -> {
            if (validateInput()) {
                performScan();
            }
        });
    }

    private boolean validateInput() {
        String url = etUrl.getText().toString().trim();

        if (!ValidationUtils.isValidUrl(url)) {
            etUrl.setError(getString(R.string.error_invalid_url));
            etUrl.requestFocus();
            return false;
        }

        return true;
    }

    private void performScan() {
        String url = etUrl.getText().toString().trim();

        showProgress(true);

        ScanRequest request = new ScanRequest(sessionManager.getUserId(), Constants.TYPE_URL);
        request.setUrl(url);

        RetrofitClient.getInstance().getApiService().scanUrl(request)
                .enqueue(new Callback<ScanResponse>() {
                    @Override
                    public void onResponse(Call<ScanResponse> call, Response<ScanResponse> response) {
                        showProgress(false);

                        if (response.isSuccessful() && response.body() != null) {
                            ScanResponse scanResponse = response.body();

                            if (scanResponse.isSuccess() && scanResponse.getResult() != null) {
                                displayResult(scanResponse);
                                saveScanHistory(scanResponse, url);
                            } else {
                                Toast.makeText(UrlScanActivity.this, scanResponse.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(UrlScanActivity.this, Constants.ERROR_SERVER, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ScanResponse> call, Throwable t) {
                        showProgress(false);
                        Toast.makeText(UrlScanActivity.this, Constants.ERROR_NETWORK, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void displayResult(ScanResponse scanResponse) {
        cardResult.setVisibility(View.VISIBLE);
        tvResult.setText(scanResponse.getResult().getResult());
        tvResult.setTextColor(getResources().getColor(
                scanResponse.getResult().isDeepfake() ? R.color.error : R.color.success));
        tvConfidence.setText(scanResponse.getResult().getFormattedConfidence());
        Toast.makeText(this, Constants.SUCCESS_SCAN, Toast.LENGTH_SHORT).show();
    }

    private void saveScanHistory(ScanResponse scanResponse, String url) {
        ScanHistory history = new ScanHistory();
        history.setUserId(sessionManager.getUserId());
        history.setFileType(Constants.TYPE_URL);
        history.setFileName(url);
        history.setFilePath(url);
        history.setResult(scanResponse.getResult().getResult());
        history.setConfidence(scanResponse.getResult().getConfidence());
        history.setTimestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));

        databaseHelper.addScanHistory(history);
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        tvProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        btnScan.setEnabled(!show);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}