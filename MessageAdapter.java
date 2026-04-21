package com.deepguard.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<Message> messageList;

    public MessageAdapter(List<Message> messageList) {
        this.messageList = messageList;
    }

    public void addMessage(Message message){
        messageList.add(message);
        notifyItemInserted(messageList.size() - 1);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.item_user_message : R.layout.item_bot_message;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.textView.setText(messageList.get(position).getText());
    }

    @Override
    public int getItemCount() { return messageList.size(); }

    @Override
    public int getItemViewType(int position) {
        return messageList.get(position).isUser() ? 1 : 0;
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        MessageViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.messageText);
        }
    }
}
