package com.example.prueba

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager

import com.example.prueba.model.Filtro
import com.example.prueba.network.clienteApi
import com.example.prueba.network.ServicioApi

import com.example.prueba.worker.VerificadorDeVidaUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.util.concurrent.TimeUnit

class vistaFiltros : AppCompatActivity() {

    // variables de interfaz
    private lateinit var recyclerView: RecyclerView
    private lateinit var filtroAdapter: FiltroAdapter

    // variables de logica
    private lateinit var servicioApi: ServicioApi
    private var listaDeFiltrosDelUsuario: List<Filtro> = emptyList()
    private var idUsuarioGlobal: Int = -1 // Almacena el ID del usuario logueado

    // constantes de notificiaciones
    private val CANAL_ID = "filtro_alert_channel"
    private val PETICION_CODIGO_PERMISO = 123


    // listas separadas para las alertas
    private val tdsYaNotificados = mutableSetOf<Int>()
    private val phYaNotificados = mutableSetOf<Int>()


    private val NOTIFICACION_ID_TDS = 1000


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vista_filtros)
        servicioApi = clienteApi.retrofit.create(ServicioApi::class.java)
        recyclerView = findViewById(R.id.recyclerViewFiltros)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val idUsuario = intent.getIntExtra("ID_USUARIO", -1)
        idUsuarioGlobal = idUsuario // Guardamos el ID
        if (idUsuarioGlobal != -1) {
            askNotificationPermission()
            cargarFiltrosDeUsuario(idUsuarioGlobal)
        } else {
            Toast.makeText(this, "Error: No se pudo identificar al usuario.", Toast.LENGTH_LONG).show()
        }
    }

    //Carga la lista de filtros del usuario desde la API
    private fun cargarFiltrosDeUsuario(idUsuario: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = servicioApi.getFiltrosPorUsuario(idUsuario)
                if (response.isSuccessful && response.body() != null) {
                    listaDeFiltrosDelUsuario = response.body()!!
                    withContext(Dispatchers.Main) {
                        if (listaDeFiltrosDelUsuario.isEmpty()) {
                            Toast.makeText(this@vistaFiltros, "Este usuario no tiene filtros asignados.", Toast.LENGTH_LONG).show()
                        } else {
                            filtroAdapter = FiltroAdapter(listaDeFiltrosDelUsuario)
                            recyclerView.adapter = filtroAdapter
                            Toast.makeText(this@vistaFiltros, "Se encontraron ${listaDeFiltrosDelUsuario.size} filtros.", Toast.LENGTH_SHORT).show()
                            iniciarPollingGlobal()
                            programarWorkerDePrediccion()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@vistaFiltros, "Error al cargar los filtros. Código: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("CargarFiltros", "Error de conexión: ${e.message}", e)
                    Toast.makeText(this@vistaFiltros, "⚠️ Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Programa la tarea [VerificadorDeVidaUtil] para que se ejecute periódicamente
    private fun programarWorkerDePrediccion() {
        Log.d("WorkManager", "Programando worker periódico de predicción...")
        val inputData = Data.Builder()
            .putInt(VerificadorDeVidaUtil.KEY_USER_ID, idUsuarioGlobal)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Solo con WiFi
            .build()
        val periodicWorkRequest = PeriodicWorkRequestBuilder<VerificadorDeVidaUtil>(
            12, TimeUnit.HOURS
        )
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            VerificadorDeVidaUtil.WORK_NAME, // Un nombre único para este trabajo
            androidx.work.ExistingPeriodicWorkPolicy.KEEP, // Mantiene el trabajo existente si ya está programado
            periodicWorkRequest
        )
        Log.d("WorkManager", "Worker programado.")
    }

    // Inicia un bucle de "polling" (revisión constante) en un hilo de fondo
    private fun iniciarPollingGlobal() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                Log.d("PollingGlobal", "Revisando ${listaDeFiltrosDelUsuario.size} filtros en segundo plano...")
                if (listaDeFiltrosDelUsuario.isNotEmpty()) {
                    revisarEstadoDeFiltros()
                }
                delay(1000L) // Revisa cada 1 segundo
            }
        }
    }

    // Revisa la última lectura de CADA filtro del usuario para alertas de pH y TDS
    private suspend fun revisarEstadoDeFiltros() {
        for (filtro in listaDeFiltrosDelUsuario) {
            try {
                val response = servicioApi.getLecturasPorfiltro(filtro.ID_Filtro)
                if (response.isSuccessful) {
                    val ultimaLectura = response.body()?.lastOrNull()
                    if (ultimaLectura != null) {
                        val idFiltro = filtro.ID_Filtro
                        val tdsExcedido = ultimaLectura.valorTDS > 500
                        val phExcedido = ultimaLectura.valorPH > 8.5f
                        if (tdsExcedido && idFiltro !in tdsYaNotificados) {
                            tdsYaNotificados.add(idFiltro)
                            withContext(Dispatchers.Main) {
                                mostrarNotificacionLocal(idFiltro, "TDS", ultimaLectura.valorTDS)
                            }
                        } else if (!tdsExcedido && idFiltro in tdsYaNotificados) {
                            tdsYaNotificados.remove(idFiltro)
                        }
                        if (phExcedido && idFiltro !in phYaNotificados) {
                            phYaNotificados.add(idFiltro)
                            withContext(Dispatchers.Main) {
                                mostrarNotificacionLocal(idFiltro, "pH", ultimaLectura.valorPH)
                            }
                        } else if (!phExcedido && idFiltro in phYaNotificados) {
                            phYaNotificados.remove(idFiltro)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PollingGlobal", "Error revisando filtro ${filtro.ID_Filtro}: ${e.message}")
            }
        }
    }

    //Pide permiso al usuario para enviar notificaciones
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    PETICION_CODIGO_PERMISO
                )
            }
        }
    }

    //Construye y muestra una notificación local (inmediata) para alertas de pH/TDS
    private fun mostrarNotificacionLocal(idFiltro: Int, tipoAlerta: String, valor: Number) {
        val titulo = "¡Alerta en Filtro ID: $idFiltro!"
        val (mensaje, notificationId) = if (tipoAlerta == "TDS") {
            Pair(
                "Valor de TDS fuera de rango: $valor ppm",
                idFiltro + NOTIFICACION_ID_TDS
            )
        } else {
            Pair(
                "Valor de pH fuera de rango: $valor",
                idFiltro
            )
        }
        val builder = NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setAutoCancel(true)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return
            }
        }
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }

    //Maneja la respuesta del usuario al pop-up de solicitud de permisos
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PETICION_CODIGO_PERMISO) {
            if (!((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))) {
                Toast.makeText(this, "Permiso denegado. No recibirás notificaciones.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

