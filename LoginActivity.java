package com.deepguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deepguard.app.admin.AdminDashboardActivity;
import com.deepguard.app.models.AuthResponse;
import com.deepguard.app.models.LoginRequest;
import com.deepguard.app.models.LoginResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.SessionManager;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvForgotPassword, tvSignup;
    private ProgressBar progressBar;

    // FIXED: Use SessionManager instead of raw SharedPreferences
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.activity_login);

            // FIXED: Initialize SessionManager
            sessionManager = new SessionManager(this);

            // If already logged in, skip login screen
            if (sessionManager.isLoggedIn()) {
                redirectToAppropriateScreen(sessionManager.getUserRole());
                return;
            }

            initViews();
            setupClickListeners();

        } catch (Exception e) {
            Log.e(TAG, "Error in onCreate", e);
            Toast.makeText(this, "Error loading login screen", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvSignup = findViewById(R.id.tvSignup);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            if (validateInputs()) {
                performLogin();
            }
        });

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, "Feature not available", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (tvSignup != null) {
            tvSignup.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(LoginActivity.this, SignupActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, "Feature not available", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private boolean validateInputs() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etEmail.setError("Email required");
            etEmail.requestFocus();
            return false;
        }

        if (!email.contains("@")) {
            etEmail.setError("Invalid email");
            etEmail.requestFocus();
            return false;
        }

        if (password.isEmpty()) {
            etPassword.setError("Password required");
            etPassword.requestFocus();
            return false;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return false;
        }

        return true;
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        showProgress(true);

        LoginRequest request = new LoginRequest(email, password);

        RetrofitClient.getInstance()
                .getApiService()
                .login(request)
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        showProgress(false);

                        if (response.isSuccessful() && response.body() != null) {
                            LoginResponse loginResponse = response.body();

                            if (loginResponse.isSuccess()) {
                                handleLoginSuccess(loginResponse);
                            } else {
                                Toast.makeText(LoginActivity.this,
                                        loginResponse.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    "Invalid credentials",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        showProgress(false);
                        Log.e(TAG, "Login failed", t);
                        Toast.makeText(LoginActivity.this,
                                "Network error: " + t.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void handleLoginSuccess(LoginResponse loginResponse) {
        String token = loginResponse.getToken();
        String role = loginResponse.getUser().getRole();
        String userId = loginResponse.getUser().getId();
        String name = loginResponse.getUser().getName();
        String userEmail = loginResponse.getUser().getEmail();

        Log.d(TAG, "========== LOGIN SUCCESS ==========");
        Log.d(TAG, "Token: " + token);
        Log.d(TAG, "Role: " + role);
        Log.d(TAG, "UserID: " + userId);
        Log.d(TAG, "Name: " + name);
        Log.d(TAG, "Email: " + userEmail);
        Log.d(TAG, "==================================");

        // FIXED: Save via SessionManager so all parts of the app read from the same place
        sessionManager.createLoginSession(userId, userEmail, name, token, role);

        // Also save the full user object for ProfileActivity
        // (Requires User model to match - adjust constructor if needed)
        // sessionManager.saveUser(loginResponse.getUser());

        // Get and send FCM token to backend
        updateFCMToken();

        Toast.makeText(LoginActivity.this, "Login Successful!", Toast.LENGTH_SHORT).show();

        redirectToAppropriateScreen(role);
    }

    // ==================== FCM TOKEN UPDATE ====================
    private void updateFCMToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        String fcmToken = task.getResult();
                        Log.d("FCM", "Token: " + fcmToken);

                        // Save locally via SessionManager
                        sessionManager.saveFCMToken(fcmToken);

                        // Send to backend
                        sendTokenToBackend(fcmToken);
                    } else {
                        Log.e("FCM", "Failed to get token", task.getException());
                    }
                });
    }

    private void sendTokenToBackend(String fcmToken) {
        String token = sessionManager.getAuthToken();

        Map<String, String> data = new HashMap<>();
        data.put("fcm_token", fcmToken);

        RetrofitClient.getInstance().getApiService()
                .updateFCMToken("Bearer " + token, data)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        if (response.isSuccessful()) {
                            Log.d("FCM", "✅ Token sent to backend successfully");
                        } else {
                            Log.e("FCM", "❌ Failed to send token to backend");
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        Log.e("FCM", "❌ Network error: " + t.getMessage());
                    }
                });
    }

    private void redirectToAppropriateScreen(String role) {
        Intent intent;

        if (role != null && role.equalsIgnoreCase("admin")) {
            Log.d(TAG, "✅ ADMIN DETECTED - Redirecting to AdminDashboard");
            intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
        } else {
            Log.d(TAG, "Regular user - Redirecting to Dashboard");
            intent = new Intent(LoginActivity.this, DashboardActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (btnLogin != null) {
            btnLogin.setEnabled(!show);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}