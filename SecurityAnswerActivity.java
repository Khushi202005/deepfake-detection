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

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Step 2 — Shows the user's security question.
 * User types their answer. If correct → goes to ResetPasswordActivity.
 */
public class SecurityAnswerActivity extends AppCompatActivity {

    private TextView tvQuestion;
    private EditText etAnswer;
    private Button btnVerify;
    private ProgressBar progressBar;

    private String email;
    private String question;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_security_answer);

        email    = getIntent().getStringExtra("email");
        question = getIntent().getStringExtra("question");
        if (email == null || question == null) { finish(); return; }

        initViews();
    }

    private void initViews() {
        tvQuestion   = findViewById(R.id.tvQuestion);
        etAnswer     = findViewById(R.id.etAnswer);
        btnVerify    = findViewById(R.id.btnVerify);
        progressBar  = findViewById(R.id.progressBar);

        tvQuestion.setText(question);

        btnVerify.setOnClickListener(v -> {
            String answer = etAnswer.getText().toString().trim();
            if (answer.isEmpty()) {
                etAnswer.setError("Please enter your answer");
                etAnswer.requestFocus();
                return;
            }
            verifyAnswer(answer);
        });
    }

    private void verifyAnswer(String answer) {
        showProgress(true);

        Map<String, String> body = new HashMap<>();
        body.put("email",  email);
        body.put("answer", answer);

        RetrofitClient.getInstance().getApiService()
                .verifySecurityAnswer(body)
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call,
                                           Response<AuthResponse> response) {
                        showProgress(false);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {

                            // ✅ Correct answer → reset password screen
                            Intent intent = new Intent(SecurityAnswerActivity.this,
                                    ResetPasswordActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                            finish();

                        } else {
                            String msg = response.body() != null
                                    ? response.body().getMessage()
                                    : "Incorrect answer. Please try again.";
                            Toast.makeText(SecurityAnswerActivity.this,
                                    msg, Toast.LENGTH_LONG).show();
                            etAnswer.setText("");
                            etAnswer.requestFocus();
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        showProgress(false);
                        Toast.makeText(SecurityAnswerActivity.this,
                                Constants.ERROR_NETWORK, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnVerify.setEnabled(!show);
    }
}