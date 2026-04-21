package com.deepguard.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deepguard.app.R;
import com.deepguard.app.models.AuthResponse;
import com.deepguard.app.network.RetrofitClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateNoticeActivity extends AppCompatActivity {

    private ImageView btnBack;
    private TextInputEditText etNoticeTitle, etNoticeMessage;
    private SwitchMaterial switchNotification;
    private MaterialButton btnPublish;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_notice);

        prefs = getSharedPreferences("DeepGuardPrefs", MODE_PRIVATE);

        // Initialize views
        btnBack = findViewById(R.id.btnBack);
        etNoticeTitle = findViewById(R.id.etNoticeTitle);
        etNoticeMessage = findViewById(R.id.etNoticeMessage);
        switchNotification = findViewById(R.id.switchNotification);
        btnPublish = findViewById(R.id.btnPublish);

        btnBack.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publishNotice());
    }

    private void publishNotice() {
        String title = etNoticeTitle.getText().toString().trim();
        String message = etNoticeMessage.getText().toString().trim();
        boolean sendNotification = switchNotification.isChecked();

        // Validation
        if (TextUtils.isEmpty(title)) {
            etNoticeTitle.setError("Title is required");
            etNoticeTitle.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(message)) {
            etNoticeMessage.setError("Message is required");
            etNoticeMessage.requestFocus();
            return;
        }

        // Disable button
        btnPublish.setEnabled(false);
        btnPublish.setText("Publishing...");

        String token = prefs.getString("token", "");

        Map<String, Object> noticeData = new HashMap<>();
        noticeData.put("title", title);
        noticeData.put("message", message);
        noticeData.put("send_notification", sendNotification);

        RetrofitClient.getInstance().getApiService()
                .createNotice("Bearer " + token, noticeData)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        btnPublish.setEnabled(true);
                        btnPublish.setText("Publish Notice");

                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(CreateNoticeActivity.this,
                                    "✅ Notice published successfully!", Toast.LENGTH_SHORT).show();

                            // Clear fields
                            etNoticeTitle.setText("");
                            etNoticeMessage.setText("");

                            // Go back
                            finish();
                        } else {
                            Toast.makeText(CreateNoticeActivity.this,
                                    "Failed to publish notice", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        btnPublish.setEnabled(true);
                        btnPublish.setText("Publish Notice");
                        Toast.makeText(CreateNoticeActivity.this,
                                "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}