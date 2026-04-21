package com.deepguard.app;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.deepguard.app.Message;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    EditText input;
    Button send;
    RecyclerView chatRecycler;
    ChatAdapter adapter;
    List<Message> messages;
    ChatBot bot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        input = findViewById(R.id.input);
        send = findViewById(R.id.send);
        chatRecycler = findViewById(R.id.chatRecycler);

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        chatRecycler.setAdapter(adapter);

        bot = new ChatBot();

        send.setOnClickListener(v -> {
            String msg = input.getText().toString().trim();
            if(msg.isEmpty()) return;

            // User message
            messages.add(new Message(msg, true));
            adapter.notifyItemInserted(messages.size() - 1);
            chatRecycler.scrollToPosition(messages.size() - 1);
            input.setText("");

            // Bot reply
            bot.sendMessage(msg, new ChatBot.ChatCallback() {
                @Override
                public void onReply(String reply) {
                    runOnUiThread(() -> {
                        messages.add(new Message(reply, false));
                        adapter.notifyItemInserted(messages.size() - 1);
                        chatRecycler.scrollToPosition(messages.size() - 1);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        messages.add(new Message("Error: " + error, false));
                        adapter.notifyItemInserted(messages.size() - 1);
                        chatRecycler.scrollToPosition(messages.size() - 1);
                    });
                }
            });
        });
    }
}
