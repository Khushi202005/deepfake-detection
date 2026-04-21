package com.deepguard.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.deepguard.app.adapters.AnalyticsAdapter;
import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import java.util.ArrayList;
import java.util.List;

public class AnalyticsActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private CardView cardEmpty;
    private AnalyticsAdapter adapter;
    private DatabaseHelper databaseHelper;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_analytics);

        sessionManager = new SessionManager(this);
        databaseHelper = new DatabaseHelper(this);

        setupToolbar();
        initViews();
        loadAnalytics();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.analytics);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        progressBar  = findViewById(R.id.progressBar);
        cardEmpty    = findViewById(R.id.cardEmpty);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private void loadAnalytics() {
        progressBar.setVisibility(View.VISIBLE);

        String userId = sessionManager.getUserId();
        int[] analytics = databaseHelper.getAnalyticsData(userId);
        double avgConfidence = databaseHelper.getAverageConfidence(userId);

        List<AnalyticsAdapter.AnalyticsStat> statsList = new ArrayList<>();

        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                getString(R.string.total_scans),
                String.valueOf(analytics[0]),
                R.drawable.ic_analytics, "info"));

        // GREEN — Real Content
        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "Real Content",
                String.valueOf(analytics[1]),
                R.drawable.ic_check, "success"));


        // RED — Fake Detected
        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "Fake Detected",
                String.valueOf(analytics[2]),
                R.drawable.ic_close, "error"));


        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "Avg Confidence",
                String.format("%.1f%%", avgConfidence * 100),
                R.drawable.ic_analytics, "warning"));

        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "Image Scans",
                String.valueOf(analytics[3]),
                R.drawable.ic_image, "info"));

        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "Video Scans",
                String.valueOf(analytics[4]),
                R.drawable.ic_video, "info"));

        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "URL Scans",
                String.valueOf(analytics[5]),
                R.drawable.ic_url, "info"));

        statsList.add(new AnalyticsAdapter.AnalyticsStat(
                "Audio Scans",
                String.valueOf(analytics[6]),
                R.drawable.ic_audio, "info"));

        progressBar.setVisibility(View.GONE);

        if (analytics[0] == 0) {
            cardEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            cardEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new AnalyticsAdapter(this, statsList);
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAnalytics();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}