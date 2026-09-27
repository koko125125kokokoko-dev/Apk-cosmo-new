package com.example.data.api

import com.example.data.model.Category
import com.example.data.model.StoreApp
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface CosmoApiService {
    @GET("api/apps")
    suspend fun getApps(): List<StoreApp>

    @GET("api/categories")
    suspend fun getCategories(): List<Category>

    @GET("api/apps/{id}")
    suspend fun getAppById(@Path("id") id: Long): StoreApp

    companion object {
        const val BASE_URL = "https://holy-firefly-9726.play125store.workers.dev/"

        fun create(): CosmoApiService {
            val userAgentInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "CosmoGameStore/1.0 (Linux; Android)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return retrofit.create(CosmoApiService::class.java)
        }
    }
}
