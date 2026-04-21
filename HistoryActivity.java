package com.deepguard.app;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.deepguard.app.R;
import com.deepguard.app.adapters.HistoryAdapter;
import com.deepguard.app.database.DatabaseHelper;
import com.deepguard.app.models.ScanHistory;
import com.deepguard.app.utils.SessionManager;
import com.deepguard.app.utils.ThemeManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity implements HistoryAdapter.OnHistoryClickListener {

    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private LinearLayout emptyView;

    private HistoryAdapter adapter;
    private DatabaseHelper databaseHelper;
    private SessionManager sessionManager;
    private List<ScanHistory> historyList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        new ThemeManager(this).applyTheme();
        setContentView(R.layout.activity_history);

        sessionManager = new SessionManager(this);
        databaseHelper = new DatabaseHelper(this);

        setupToolbar();
        initViews();
        loadHistory();
    }

    private void setupToolbar() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.history);
        }

        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        toolbar.inflateMenu(R.menu.history_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_history) {
                showClearHistoryDialog();
                return true;
            }
            return false;
        });
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyView = findViewById(R.id.emptyView);


        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyList = new ArrayList<>();
        adapter = new HistoryAdapter(this, historyList, this);
        recyclerView.setAdapter(adapter);
    }

    private void loadHistory() {
        progressBar.setVisibility(View.VISIBLE);

        String userId = sessionManager.getUserId();
        historyList = databaseHelper.getAllHistory(userId);

        progressBar.setVisibility(View.GONE);

        if (historyList.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);

            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);

            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateData(historyList);
        }
    }

    @Override
    public void onHistoryClick(ScanHistory history) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Scan Details")
                .setMessage(
                        "File: " + history.getFileName() + "\n" +
                                "Type: " + history.getFileType() + "\n" +
                                "Result: " + history.getResult() + "\n" +
                                "Confidence: " + history.getFormattedConfidence() + "\n" +
                                "Date: " + history.getTimestamp()
                )
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onDeleteClick(ScanHistory history) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Scan")
                .setMessage("Are you sure you want to delete this scan?")
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    databaseHelper.deleteHistory(history.getId());
                    loadHistory();
                    Toast.makeText(this, "Scan deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void showClearHistoryDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    databaseHelper.clearHistory(sessionManager.getUserId());
                    loadHistory();
                    Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}