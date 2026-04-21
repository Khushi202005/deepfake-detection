package com.deepguard.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepguard.app.adapters.NoticeAdapter;
import com.deepguard.app.models.Notice;
import com.deepguard.app.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NoticeActivity extends AppCompatActivity {

    private ImageView btnBack, btnRefresh;
    private TextView tvNoticeCount, tvEmpty;  // ✅ FIXED: Changed to TextView
    private RecyclerView rvNotices;
    private ProgressBar progressBar;
    private NoticeAdapter adapter;
    private List<Notice> noticesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notice);

        initViews();
        setupRecyclerView();
        setupClickListeners();
        loadNotices();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnRefresh = findViewById(R.id.btnRefresh);
        tvNoticeCount = findViewById(R.id.tvNoticeCount);
        rvNotices = findViewById(R.id.rvNotices);
        tvEmpty = findViewById(R.id.tvEmpty);  // ✅ FIXED: Correct type
        progressBar = findViewById(R.id.progressBar);

        noticesList = new ArrayList<>();
    }

    private void setupRecyclerView() {
        adapter = new NoticeAdapter(noticesList, this);
        rvNotices.setLayoutManager(new LinearLayoutManager(this));
        rvNotices.setAdapter(adapter);
    }

    private void setupClickListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> loadNotices());
        }
    }

    private void loadNotices() {
        if (tvNoticeCount != null) {
            tvNoticeCount.setText("Loading...");
        }

        // Show loading
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (rvNotices != null) rvNotices.setVisibility(View.GONE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);

        RetrofitClient.getInstance()
                .getApiService()
                .getAllNotices()
                .enqueue(new Callback<List<Notice>>() {
                    @Override
                    public void onResponse(Call<List<Notice>> call, Response<List<Notice>> response) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);

                        if (response.isSuccessful() && response.body() != null) {
                            noticesList.clear();
                            noticesList.addAll(response.body());

                            if (noticesList.isEmpty()) {
                                showEmptyState("No notices yet");
                            } else {
                                showNotices();
                            }
                        } else {
                            showError("Failed to load notices");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Notice>> call, Throwable t) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        showError("Connection failed: " + t.getMessage());
                    }
                });
    }

    private void showNotices() {
        if (rvNotices != null) rvNotices.setVisibility(View.VISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (tvNoticeCount != null) {
            tvNoticeCount.setText(noticesList.size() + " notice" + (noticesList.size() == 1 ? "" : "s"));
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void showEmptyState(String message) {
        if (rvNotices != null) rvNotices.setVisibility(View.GONE);
        if (tvEmpty != null) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(message);
        }
        if (tvNoticeCount != null) {
            tvNoticeCount.setText("0 notices");
        }
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        showEmptyState("Failed to load");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotices();
    }
}