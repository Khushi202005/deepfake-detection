package com.deepguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deepguard.app.models.SignupRequest;
import com.deepguard.app.models.SignupResponse;
import com.deepguard.app.network.RetrofitClient;
import com.deepguard.app.utils.Constants;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import com.deepguard.app.utils.ValidationUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    private EditText etName, etEmail, etPassword, etConfirmPassword, etSecurityAnswer;
    private Spinner spinnerSecurityQuestion;
    private Button btnSignup;
    private TextView tvLogin;
    private ProgressBar progressBar;
    private SessionManager sessionManager;
    private ThemeManager themeManager;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeManager = new ThemeManager(this);
        themeManager.applyTheme();
        setContentView(R.layout.activity_signup);

        sessionManager = new SessionManager(this);
        initViews();
        setupSecurityQuestionSpinner();
        setupPasswordToggle(etPassword, false);
        setupPasswordToggle(etConfirmPassword, true);
        setupClickListeners();
    }

    private void initViews() {
        etName                  = findViewById(R.id.etName);
        etEmail                 = findViewById(R.id.etEmail);
        etPassword              = findViewById(R.id.etPassword);
        etConfirmPassword       = findViewById(R.id.etConfirmPassword);
        etSecurityAnswer        = findViewById(R.id.etSecurityAnswer);
        spinnerSecurityQuestion = findViewById(R.id.spinnerSecurityQuestion);
        btnSignup               = findViewById(R.id.btnSignup);
        tvLogin                 = findViewById(R.id.tvLogin);
        progressBar             = findViewById(R.id.progressBar);
    }

    private void setupSecurityQuestionSpinner() {
        // Questions array — matches strings.xml security_questions array
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.security_questions,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSecurityQuestion.setAdapter(adapter);
    }

    private void setupClickListeners() {
        btnSignup.setOnClickListener(v -> {
            if (validateInputs()) {
                performSignup();
            }
        });

        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void setupPasswordToggle(EditText editText, boolean isConfirm) {
        editText.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawX() >=
                        (editText.getRight() - editText.getCompoundDrawables()[2].getBounds().width())) {
                    if (isConfirm) {
                        isConfirmPasswordVisible = !isConfirmPasswordVisible;
                        editText.setTransformationMethod(
                                isConfirmPasswordVisible
                                        ? HideReturnsTransformationMethod.getInstance()
                                        : PasswordTransformationMethod.getInstance());
                    } else {
                        isPasswordVisible = !isPasswordVisible;
                        editText.setTransformationMethod(
                                isPasswordVisible
                                        ? HideReturnsTransformationMethod.getInstance()
                                        : PasswordTransformationMethod.getInstance());
                    }
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            }
            return false;
        });
    }

    private boolean validateInputs() {
        String name             = etName.getText().toString().trim();
        String email            = etEmail.getText().toString().trim();
        String password         = etPassword.getText().toString().trim();
        String confirmPassword  = etConfirmPassword.getText().toString().trim();
        String securityAnswer   = etSecurityAnswer.getText().toString().trim();

        if (!ValidationUtils.isValidName(name)) {
            etName.setError(ValidationUtils.getNameError(name));
            etName.requestFocus();
            return false;
        }

        if (!ValidationUtils.isValidEmail(email)) {
            etEmail.setError(ValidationUtils.getEmailError(email));
            etEmail.requestFocus();
            return false;
        }

        if (!ValidationUtils.isValidPassword(password)) {
            etPassword.setError(ValidationUtils.getPasswordError(password));
            etPassword.requestFocus();
            return false;
        }

        if (!ValidationUtils.doPasswordsMatch(password, confirmPassword)) {
            etConfirmPassword.setError(getString(R.string.error_password_mismatch));
            etConfirmPassword.requestFocus();
            return false;
        }

        // ── Validate security question answer ──────────────────────
        if (securityAnswer.isEmpty()) {
            etSecurityAnswer.setError("Please provide an answer to the security question");
            etSecurityAnswer.requestFocus();
            return false;
        }

        if (securityAnswer.length() < 2) {
            etSecurityAnswer.setError("Answer is too short");
            etSecurityAnswer.requestFocus();
            return false;
        }

        return true;
    }

    private void performSignup() {
        String name             = etName.getText().toString().trim();
        String email            = etEmail.getText().toString().trim();
        String password         = etPassword.getText().toString().trim();
        String securityQuestion = spinnerSecurityQuestion.getSelectedItem().toString();
        String securityAnswer   = etSecurityAnswer.getText().toString().trim().toLowerCase();

        showProgress(true);

        // ── Build SignupRequest with security question + answer ────
        SignupRequest request = new SignupRequest(name, email, password, securityQuestion, securityAnswer);

        RetrofitClient.getInstance().getApiService().signup(request)
                .enqueue(new Callback<SignupResponse>() {
                    @Override
                    public void onResponse(Call<SignupResponse> call,
                                           Response<SignupResponse> response) {
                        showProgress(false);

                        if (response.isSuccessful() && response.body() != null) {
                            SignupResponse signupResponse = response.body();

                            if (signupResponse.isSuccess()) {
                                sessionManager.createLoginSession(
                                        signupResponse.getUser().getId(),
                                        signupResponse.getUser().getEmail(),
                                        signupResponse.getUser().getName(),
                                        signupResponse.getToken()
                                );

                                Toast.makeText(SignupActivity.this,
                                        Constants.SUCCESS_SIGNUP,
                                        Toast.LENGTH_SHORT).show();

                                Intent intent = new Intent(SignupActivity.this, DashboardActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();

                            } else {
                                Toast.makeText(SignupActivity.this,
                                        signupResponse.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(SignupActivity.this,
                                    Constants.ERROR_USER_EXISTS,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SignupResponse> call, Throwable t) {
                        showProgress(false);
                        Toast.makeText(SignupActivity.this,
                                Constants.ERROR_NETWORK,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSignup.setEnabled(!show);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}