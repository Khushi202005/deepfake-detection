package com.deepguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deepguard.app.models.AuthResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.ThemeManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Step 3 — User sets a new password after identity verified.
 */
public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etNewPassword, etConfirmPassword;
    private Button btnResetPassword;
    private ProgressBar progressBar;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_reset_password);

        email = getIntent().getStringExtra("email");
        if (email == null) { finish(); return; }

        initViews();
    }

    private void initViews() {
        etNewPassword     = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnResetPassword  = findViewById(R.id.btnResetPassword);
        progressBar       = findViewById(R.id.progressBar);

        btnResetPassword.setOnClickListener(v -> {
            String newPass     = etNewPassword.getText().toString().trim();
            String confirmPass = etConfirmPassword.getText().toString().trim();

            if (newPass.length() < 6) {
                etNewPassword.setError("Minimum 6 characters");
                etNewPassword.requestFocus();
                return;
            }
            if (!newPass.equals(confirmPass)) {
                etConfirmPassword.setError("Passwords do not match");
                etConfirmPassword.requestFocus();
                return;
            }
            resetPassword(newPass);
        });
    }

    private void resetPassword(String newPassword) {
        showProgress(true);

        Map<String, String> body = new HashMap<>();
        body.put("email",    email);
        body.put("password", newPassword);

        RetrofitClient.getInstance().getApiService()
                .resetPassword(body)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call,
                                           Response<AuthResponse> response) {
                        showProgress(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {

                            Toast.makeText(ResetPasswordActivity.this,
                                    "✅ Password reset! Please log in.",
                                    Toast.LENGTH_LONG).show();

                            Intent intent = new Intent(ResetPasswordActivity.this,
                                    LoginActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);

                        } else {
                            String msg = response.body() != null
                                    ? response.body().getMessage()
                                    : "Reset failed. Please try again.";
                            Toast.makeText(ResetPasswordActivity.this,
                                    msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        showProgress(false);
                        Toast.makeText(ResetPasswordActivity.this,
                                "Network error.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnResetPassword.setEnabled(!show);
    }
}