package com.deepguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deepguard.app.models.AuthResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.Constants;
import com.deepguard.app.utils.ThemeManager;
import com.deepguard.app.utils.ValidationUtils;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Step 1 — User enters their registered email.
 * Backend returns their security question.
 * Then navigates to SecurityAnswerActivity.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private Button btnNext;
    private TextView tvBackToLogin;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_forgot_password);
        initViews();
        setupClickListeners();
    }

    private void initViews() {
        etEmail       = findViewById(R.id.etEmail);
        btnNext       = findViewById(R.id.btnResetPassword);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
        progressBar   = findViewById(R.id.progressBar);
        btnNext.setText("Next");
    }

    private void setupClickListeners() {
        btnNext.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (!ValidationUtils.isValidEmail(email)) {
                etEmail.setError(ValidationUtils.getEmailError(email));
                etEmail.requestFocus();
                return;
            }
            fetchSecurityQuestion(email);
        });

        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void fetchSecurityQuestion(String email) {
        showProgress(true);

        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        RetrofitClient.getInstance().getApiService()
                .getSecurityQuestion(body)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call,
                                           Response<AuthResponse> response) {
                        showProgress(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {

                            String question = response.body().getQuestion();

                            // Go to answer screen with email + question
                            Intent intent = new Intent(ForgotPasswordActivity.this,
                                    SecurityAnswerActivity.class);
                            intent.putExtra("email",    email);
                            intent.putExtra("question", question);
                            startActivity(intent);

                        } else {
                            String msg = response.body() != null
                                    ? response.body().getMessage()
                                    : "Account not found. Please check your email.";
                            Toast.makeText(ForgotPasswordActivity.this,
                                    msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        showProgress(false);
                        Toast.makeText(ForgotPasswordActivity.this,
                                Constants.ERROR_NETWORK, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnNext.setEnabled(!show);
    }
}