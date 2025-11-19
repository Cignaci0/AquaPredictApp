package com.example.prueba.worker
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.prueba.R
import com.example.prueba.network.clienteApi
import com.example.prueba.network.ServicioApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VerificadorDeVidaUtil(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    private val servicioApi: ServicioApi = clienteApi.retrofit.create(ServicioApi::class.java)
    private val CHANNEL_ID = "filtro_alert_channel"
    private val NOTIFICACION_ID_OFFSET_PREDICCION = 2000 // ID base para estas notificaciones
    companion object {
        const val WORK_NAME = "PredictionCheckWorker"
        const val KEY_USER_ID = "USER_ID"
    }

    //El método principal donde se ejecuta el trabajo en segundo plano
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(WORK_NAME, "Worker iniciado...")
        val idUsuario = inputData.getInt(KEY_USER_ID, -1)
        if (idUsuario == -1) {
            Log.e(WORK_NAME, "Error: No se recibió el ID de Usuario.")
            return@withContext Result.failure()
        }
        try {
            val responseFiltros = servicioApi.getFiltrosPorUsuario(idUsuario)
            if (!responseFiltros.isSuccessful || responseFiltros.body() == null) {
                Log.e(WORK_NAME, "No se pudieron obtener los filtros.")
                return@withContext Result.retry()
            }
            val filtros = responseFiltros.body()!!
            Log.d(WORK_NAME, "Revisando ${filtros.size} filtros para el usuario $idUsuario")
            for (filtro in filtros) {
                try {
                    val responsePrediccion = servicioApi.getPrediccionFiltro(filtro.ID_Filtro)
                    if (responsePrediccion.isSuccessful && responsePrediccion.body() != null) {
                        val prediccion = responsePrediccion.body()!!
                        val diasRestantes = prediccion.diasRestantes.toFloatOrNull()
                        Log.d(WORK_NAME, "Filtro ${filtro.ID_Filtro}: Días restantes = $diasRestantes")
                        if (diasRestantes != null && diasRestantes <= 30) {
                            Log.w(WORK_NAME, "¡ALERTA! Filtro ${filtro.ID_Filtro} tiene $diasRestantes días.")
                            val notificationId = filtro.ID_Filtro + NOTIFICACION_ID_OFFSET_PREDICCION
                            mostrarNotificacion(
                                idFiltro = filtro.ID_Filtro,
                                dias = diasRestantes,
                                notificationId = notificationId
                            )
                        }
                    } else {
                        Log.e(WORK_NAME, "Error al predecir filtro ${filtro.ID_Filtro}: ${responsePrediccion.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(WORK_NAME, "Excepción al procesar filtro ${filtro.ID_Filtro}: ${e.message}", e)
                }
            }
            Log.d(WORK_NAME, "Worker finalizado.")
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(WORK_NAME, "Error general en el worker: ${e.message}", e)
            return@withContext Result.retry()
        }
    }

    //Construye y muestra una notificación en el dispositivo del usuario
    private fun mostrarNotificacion(idFiltro: Int, dias: Float, notificationId: Int) {
        val titulo = "¡Alerta de Mantenimiento!"
        val mensaje = "A su filtro (ID: $idFiltro) le quedan aproximadamente ${"%.1f".format(dias)} días de uso."
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Log.w(WORK_NAME, "No se tiene permiso para postear notificaciones.")
                return
            }
        }
        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
}