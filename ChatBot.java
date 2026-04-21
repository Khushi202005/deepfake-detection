package com.deepguard.app;

import com.deepguard.app.network.ApiService;
import com.deepguard.app.models.ChatRequest;
import com.deepguard.app.models.ChatResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import com.deepguard.app.ApiConfig;

public class ChatBot {

    private ApiService apiService;

    public ChatBot() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(ApiConfig.getBaseUrl()) // ✅ USE SAME BASE
                .addConverterFactory(GsonConverterFactory.create())
                .build();


        apiService = retrofit.create(ApiService.class);
    }

    public void sendMessage(String msg, ChatCallback callback) {
        ChatRequest request = new ChatRequest(msg);

        apiService.sendMessage(request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onReply(response.body().getReply());
                } else {
                    callback.onError("No response");
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public interface ChatCallback {
        void onReply(String reply);
        void onError(String error);
    }
}
