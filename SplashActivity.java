package com.deepguard.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.views.ShieldDotView;
import com.deepguard.app.views.ParticleView;

public class SplashActivity extends AppCompatActivity {

    private ShieldDotView shieldDotView;
    private ParticleView  particleView;
    private android.view.View logoContainer;
    private TextView      tvSplashName;
    private TextView      tvSplashTagline;

    private SessionManager sessionManager;

    // ─────────────────────────────────────────────────────────
    // ANIMATION TIMELINE
    // T=0ms    : Particles start floating
    // T=300ms  : Logo zooms in (scale 0.1 → 1.0, overshoot)
    // T=1100ms : Shield border dots start tracing
    // T=1500ms : App name scales in — "Deep" white "Guard" cyan
    // T=2000ms : Tagline fades in
    // T=3500ms : Fade out → navigate
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        makeFullScreen();
        setContentView(R.layout.activity_splash);

        sessionManager  = new SessionManager(this);
        shieldDotView   = findViewById(R.id.shieldDotView);
        particleView    = findViewById(R.id.particleView);
        logoContainer   = findViewById(R.id.logoContainer);
        tvSplashName    = findViewById(R.id.tvSplashName);
        tvSplashTagline = findViewById(R.id.tvSplashTagline);

        // Set "Deep" white, "Guard" cyan — SpannableString
        setAppNameColor();

        startAnimation();
    }

    /**
     * "Deep" = white, "Guard" = neon cyan (#00E5FF)
     * Exactly like the screenshot reference
     */
    private void setAppNameColor() {
        String fullName = "DeepGuard";
        SpannableString spannable = new SpannableString(fullName);

        // "Deep" part — white
        spannable.setSpan(
                new ForegroundColorSpan(Color.WHITE),
                0, 4,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // "Guard" part — neon cyan
        spannable.setSpan(
                new ForegroundColorSpan(Color.parseColor("#00E5FF")),
                4, 9,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        tvSplashName.setText(spannable);
    }

    private void startAnimation() {
        Handler h = new Handler();

        // ── Particles start immediately ──
        particleView.startParticles();

        // ── T=300ms : Logo zoom in ──
        logoContainer.setAlpha(0f);
        logoContainer.setScaleX(0.1f);
        logoContainer.setScaleY(0.1f);
        h.postDelayed(() ->
                        logoContainer.animate()
                                .alpha(1f).scaleX(1f).scaleY(1f)
                                .setDuration(750)
                                .setInterpolator(new OvershootInterpolator(1.4f))
                                .start()
                , 300);

        // ── T=1100ms : Shield border dots start ──
        h.postDelayed(() -> shieldDotView.startDots(), 1100);

        // ── T=1500ms : App name scales in ──
        tvSplashName.setAlpha(0f);
        tvSplashName.setScaleX(0.35f);
        tvSplashName.setScaleY(0.35f);
        h.postDelayed(() ->
                        tvSplashName.animate()
                                .alpha(1f).scaleX(1f).scaleY(1f)
                                .setDuration(650)
                                .setInterpolator(new OvershootInterpolator(1.2f))
                                .start()
                , 1500);

        // ── T=2000ms : Tagline fades in ──
        tvSplashTagline.setAlpha(0f);
        h.postDelayed(() ->
                        tvSplashTagline.animate()
                                .alpha(1f)
                                .setDuration(500)
                                .setInterpolator(new DecelerateInterpolator())
                                .start()
                , 2000);

        // ── T=3500ms : Fade out and navigate ──
        h.postDelayed(this::exitSplash, 6500);
    }

    private void exitSplash() {
        findViewById(android.R.id.content)
                .animate()
                .alpha(0f)
                .setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(this::navigateToNext)
                .start();
    }

    private void navigateToNext() {
        shieldDotView.stopDots();
        particleView.stopParticles();

        Intent intent = sessionManager.isLoggedIn()
                ? new Intent(this, DashboardActivity.class)
                : new Intent(this, HomeActivity.class);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void makeFullScreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        try {
            WindowInsetsControllerCompat ctrl =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            ctrl.hide(WindowInsetsCompat.Type.systemBars());
            ctrl.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() { /* disable back on splash */ }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shieldDotView != null) shieldDotView.stopDots();
        if (particleView  != null) particleView.stopParticles();
    }
}