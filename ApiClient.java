package com.deepguard.app;

import com.deepguard.app.network.ApiService;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static Retrofit retrofit = null;


    public static ApiService getApiService() {
        if (retrofit == null) {
            // 1. Logging interceptor
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // 2. OkHttpClient with timeout
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(ApiConfig.CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
                    .readTimeout(ApiConfig.SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                    .writeTimeout(ApiConfig.SOCKET_TIMEOUT, TimeUnit.MILLISECONDS)
                    .addInterceptor(logging)
                    .build();

            // 3. Retrofit instance
            retrofit = new Retrofit.Builder()
                    .baseUrl(ApiConfig.getBaseUrl())
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        // 4. Return ApiService
        return retrofit.create(ApiService.class);
    }
}
