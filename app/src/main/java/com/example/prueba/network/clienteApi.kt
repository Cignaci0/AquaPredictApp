package com.example.prueba.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object clienteApi {
    private const val BASE_URL = "https://api-hoqw.onrender.com/"

    // Crea un Cliente OkHttp con timeouts largos (150 segundos)
    // Esto es necesario para manejar el "Arranque en Frío" (Cold Start)
    // de los servicios gratuitos de render que puede tardar entre 30 a 50 segundos.
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(150, TimeUnit.SECONDS) // 150 segundos para conectar
            .readTimeout(150, TimeUnit.SECONDS)    // 150 segundos para leer
            .writeTimeout(150, TimeUnit.SECONDS)   // 150 segundos para escribir
            .build()
    }
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) //
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
