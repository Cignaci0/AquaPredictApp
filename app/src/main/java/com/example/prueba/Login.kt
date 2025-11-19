package com.example.prueba

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.prueba.model.RespuestaLogin
import com.example.prueba.model.Usuario
import com.example.prueba.network.clienteApi
import com.example.prueba.network.ServicioApi
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class Login : AppCompatActivity() {

    private lateinit var etRUT: EditText
    private lateinit var etContrasena: EditText
    private lateinit var btnLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // Forzar el modo claro
        enableEdgeToEdge() //Habilita el modo de pantalla completa
        setContentView(R.layout.activity_login)

        etRUT = findViewById(R.id.etRUT)
        etContrasena = findViewById(R.id.etContrasena)
        btnLogin = findViewById(R.id.btnLogin)
        createNotificationChannel() // Registrar el canal de notificaciones en el sistema
        btnLogin.setOnClickListener { loginUsuario() }
    }
    private fun loginUsuario() {
        val rut = etRUT.text.toString().trim()
        val contrasena = etContrasena.text.toString().trim()
        if (rut.isEmpty() || contrasena.isEmpty()) {
            Toast.makeText(this, "Ingrese RUT y contraseña", Toast.LENGTH_SHORT).show()
            return
        }
        val usuario = Usuario(rut, contrasena)
        val servicioApi: ServicioApi = clienteApi.retrofit.create(ServicioApi::class.java)
        servicioApi.login(usuario).enqueue(object : Callback<RespuestaLogin> {
            override fun onResponse(call: Call<RespuestaLogin>, response: Response<RespuestaLogin>) {
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val intent = Intent(this@Login, vistaFiltros::class.java)
                    intent.putExtra("ID_USUARIO", loginResponse.id_usuario)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@Login, "❌ Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<RespuestaLogin>, t: Throwable) {
                Toast.makeText(this@Login, "⚠️ Error de conexión: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "filtro_alert_channel"
            val name = "Alertas de Filtro"
            val descriptionText = "Notificaciones urgentes sobre el estado del filtro"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}