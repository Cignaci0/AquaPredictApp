package com.example.prueba

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import android.Manifest
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart // <-- Importa PieChart
import com.github.mikephil.charting.data.PieData     // <-- Importa PieData
import com.github.mikephil.charting.data.PieDataSet  // <-- Importa PieDataSet
import com.github.mikephil.charting.data.PieEntry    // <-- Importa PieEntry
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.prueba.model.RespuestaPrediccion
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.prueba.model.Lecturas
import com.example.prueba.network.clienteApi
import com.example.prueba.network.ServicioApi
import com.example.prueba.util.FormateoFechas
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LecturasActivity : AppCompatActivity() {

    // Variables de la interfaz
    private var filtroId: Int = -1 // El ID del filtro que esta pantalla está mostrando
    private lateinit var chartTDS: LineChart // El gráfico de líneas para TDS
    private lateinit var chartPH: LineChart // El gráfico de líneas para pH
    private lateinit var btnDescargarReporte: Button // El botón para el CSV
    private lateinit var doughnutTDS: PieChart // El gráfico de dona para TDS
    private lateinit var doughnutPH: PieChart // El gráfico de dona para pH
    private lateinit var doughnutFlujo: PieChart // El grafico de dona para flujo
    private lateinit var btnPredecirML: Button // El botón para la predicción manual
    private lateinit var progressPrediccion: ProgressBar // La ruedita de "cargando"

    // Variables de logica
    private lateinit var servicioApi: ServicioApi // El objeto de Retrofit para hablar con la API
    private var alertaMostrada = false // Bandera para no mostrar la alerta "urgente" 1000 veces
    private var prediccionYaNotificada = false // Bandera para no notificar lo de "30 días" 1000 veces
    private var csvContent: String? = null // Almacena el CSV mientras el usuario elige la ruta

    // Constantes, valores fijos
    private val MIN_TDS_OPTIMO = 50f
    private val MAX_TDS_OPTIMO = 500f
    private val MIN_PH_OPTIMO = 6.5f
    private val MAX_PH_OPTIMO = 8.5f
    private val CHANNEL_ID = "filtro_alert_channel" // ID del canal de notificación (tiene que ser el mismo de Login.kt)
    private val REQUEST_CODE_PERMISSION = 123
    private val NOTIFICACION_ID_PREDICCION = 2000 // ID base para alertas de predicción

    // lanzador de actividad (para el cvs)
    private val crearArchivoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                csvContent?.let { content ->
                    escribirArchivo(uri, content)
                }
            }
        } else {
            Toast.makeText(this, "Guardado cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_lecturas) //Carga el layout XML
        setupWindowInsets()

        //Conecta las variables de Kotlin con los componentes del XML
        servicioApi = clienteApi.retrofit.create(ServicioApi::class.java) //Inicializa el servicio de API
        chartTDS = findViewById(R.id.chartTDS)
        chartPH = findViewById(R.id.chartPH)
        btnPredecirML = findViewById(R.id.btnPredecirML)
        progressPrediccion = findViewById(R.id.progressPrediccion)
        btnDescargarReporte = findViewById(R.id.btnDescargarReporte)
        doughnutTDS = findViewById(R.id.doughnutTDS)
        doughnutPH = findViewById(R.id.doughnutPH)
        doughnutFlujo = findViewById(R.id.doughnutFlujo)

        //Obtiene el ID del filtro que FiltroAdapter le envió
        filtroId = intent.getIntExtra("ID_FILTRO_SELECCIONADO", -1)
        if (filtroId == -1) {
            Toast.makeText(this, "Error: ID de filtro no encontrado", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        //Llama a las funciones de configuración para "embellecer" los gráficos
        setupTdsChart()
        setupPhChart()
        setupDoughnutTds()
        setupDoughnutPh()
        setupDoughnutFlujo()

        //Asigna las acciones a los botones
        btnPredecirML.setOnClickListener {
            onPredecirManualmenteClicked()  //Llama a la función de predicción
        }
        btnDescargarReporte.setOnClickListener {
            solicitarYGenerarReporte() //Llama a la función de CSV
        }

        //Inicia todos los procesos en segundo plano
        askNotificationPermission() // Pedir permiso para notificar
        iniciarPollingGraficos() // Para los gráficos (cada 1 seg)
        iniciarPollingPrediccionAutomatica() //prediccion cada 1 minuto para el ML
    }

    private fun solicitarYGenerarReporte() {
        Log.d("ReporteCSV", "Iniciando generación de reporte para filtro: $filtroId")

        lifecycleScope.launch {
            try {
                // Mostrar ProgressBar
                withContext(Dispatchers.Main) {
                    progressPrediccion.visibility = View.VISIBLE
                    btnDescargarReporte.isEnabled = false
                    btnDescargarReporte.text = "Generando..."
                }

                // 1. Obtener TODAS las lecturas (igual que para los gráficos)
                val response = servicioApi.getLecturasPorfiltro(filtroId)
                if (response.isSuccessful && response.body() != null) {
                    val lecturas = response.body()!!

                    if (lecturas.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@LecturasActivity, "No hay datos para exportar", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    // 2. Generar el contenido del archivo CSV
                    csvContent = generarContenidoCSV(lecturas)

                    // 3. Lanzar el "guardar como..." (debe ser en el Hilo Principal)
                    withContext(Dispatchers.Main) {
                        lanzarIntentGuardarArchivo()
                    }

                } else {
                    Log.e("ReporteCSV", "Error al obtener lecturas: ${response.message()}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LecturasActivity, "Error al obtener datos", Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: Exception) {
                Log.e("ReporteCSV", "Excepción al generar reporte: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LecturasActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // Ocultar ProgressBar
                withContext(Dispatchers.Main) {
                    progressPrediccion.visibility = View.GONE
                    btnDescargarReporte.isEnabled = true
                    btnDescargarReporte.text = "Descargar Reporte (Excel)"
                }
            }
        }
    }


    private fun generarContenidoCSV(lecturas: List<Lecturas>): String {
        val csv = StringBuilder()
        csv.append("ID_Filtro,Fecha,Valor_pH,Valor_TDS,Valor_Flujo\n")
        val inputFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        val outputFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        lecturas.forEach { lec ->
            val fechaFormateada = try {
                val date = inputFormatter.parse(lec.fecha)
                if (date != null) outputFormatter.format(date) else lec.fecha
            } catch (e: Exception) {
                lec.fecha // Usar la fecha original si falla el parseo
            }
            csv.append("${lec.idFiltro},\"${fechaFormateada}\",${lec.valorPH},${lec.valorTDS},${lec.valorFlujo}\n")
        }
        Log.d("ReporteCSV", "Contenido CSV generado (${lecturas.size} filas)")
        return csv.toString()
    }
    private fun lanzarIntentGuardarArchivo() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val nombreArchivo = "reporte_filtro_${filtroId}_${timestamp}.csv"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, nombreArchivo)
        }
        crearArchivoLauncher.launch(intent)
    }
    private fun escribirArchivo(uri: Uri, content: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            Toast.makeText(this, "✅ Reporte guardado con éxito", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("ReporteCSV", "Error al escribir el archivo", e)
            Toast.makeText(this, "❌ Error al guardar el archivo", Toast.LENGTH_SHORT).show()
        } finally {
            csvContent = null
        }
    }
    private fun setupDoughnutTds() {
        doughnutTDS.apply {
            isDrawHoleEnabled = true
            holeRadius = 70f
            transparentCircleRadius = 75f
            setHoleColor(Color.TRANSPARENT)

            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(false)

            setCenterTextSize(14f)
            setCenterTextColor(Color.BLACK)
            centerText = "TDS\n---" }
    }

    private fun setupDoughnutPh() {
        doughnutPH.apply {
            isDrawHoleEnabled = true
            holeRadius = 70f
            transparentCircleRadius = 75f
            setHoleColor(Color.TRANSPARENT)

            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(false)

            setCenterTextSize(14f)
            setCenterTextColor(Color.BLACK)
            centerText = "PH\n---"
        }
    }

    private fun setupDoughnutFlujo() {
        doughnutFlujo.apply {
            isDrawHoleEnabled = true
            holeRadius = 70f
            transparentCircleRadius = 75f
            setHoleColor(Color.TRANSPARENT)

            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(false)
            setDrawEntryLabels(false)

            setCenterTextSize(14f)
            setCenterTextColor(Color.BLACK)
            centerText = "FLUJO\n---"
        }
    }

    private fun onPredecirManualmenteClicked() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    progressPrediccion.visibility = View.VISIBLE
                    btnPredecirML.isEnabled = false
                    btnPredecirML.text = "Calculando..."
                }

                val response = servicioApi.getPrediccionFiltro(filtroId)
                withContext(Dispatchers.Main) {
                    progressPrediccion.visibility = View.GONE
                    btnPredecirML.isEnabled = true
                    btnPredecirML.text = "Calcular Predicción ML"
                }

                if (response.isSuccessful && response.body() != null) {
                    val prediccion: RespuestaPrediccion = response.body()!!
                    mostrarResultadoPrediccion(prediccion.diasRestantes)
                } else {
                    val errorMsg = "Error al predecir: ${response.message()}"
                    Log.e("PrediccionManual", errorMsg)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LecturasActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressPrediccion.visibility = View.GONE
                    btnPredecirML.isEnabled = true
                    btnPredecirML.text = "Calcular Predicción ML"
                }
                val errorMsg = "Error de red al predecir: ${e.message}"
                Log.e("PrediccionManual", errorMsg, e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LecturasActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun mostrarResultadoPrediccion(dias: String) {
        AlertDialog.Builder(this)
            .setTitle("Predicción de Días Restantes")
            .setMessage("Le quedan aproximadamente $dias días de uso a su filtro.")
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Entendido") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
    private fun iniciarPollingGraficos() {
        lifecycleScope.launch {
            while (true) {
                Log.d("PollingGraficos", "Actualizando gráfico en tiempo real...")
                fetchAndDrawData()
                delay(1000L) // 1 segundo (rápido para gráficos)
            }
        }
    }
    private fun iniciarPollingPrediccionAutomatica() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                Log.d("PollingPrediccion", "Revisando predicción automática en segundo plano...")
                try {
                    val response = servicioApi.getPrediccionFiltro(filtroId)
                    if (response.isSuccessful && response.body() != null) {
                        val diasStr = response.body()!!.diasRestantes
                        val dias = diasStr.toFloatOrNull()

                        if (dias != null) {
                            if (dias <= 30 && !prediccionYaNotificada) {
                                prediccionYaNotificada = true
                                withContext(Dispatchers.Main) {
                                    mostrarNotificacionPrediccion(dias)
                                }
                            } else if (dias > 30) {
                                // Si los días vuelven a ser > 30 (ej. se cambió el filtro)
                                prediccionYaNotificada = false // Resetear la bandera
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PollingPrediccion", "Error en polling automático de predicción: ${e.message}")
                }

                // Esperar 1 minutos (60,000 ms) para el siguiente chequeo
                delay(60000L)
            }
        }
    }


    // --- Funciones de Notificación ---
    private fun mostrarNotificacionPrediccion(dias: Float) {
        val titulo = "¡Aviso de Mantenimiento (Filtro $filtroId)!"
        val mensaje = "¡Atención! Le quedan menos de 30 días (aprox. ${"%.1f".format(dias)} días) de vida útil a su filtro."
        val notificationId = filtroId + NOTIFICACION_ID_PREDICCION
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
                Log.w("NotificacionPred", "No se tiene permiso para mostrar notificación.")
                return
            }
        }
        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            if (!((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED))) {
                Toast.makeText(this, "Permiso denegado. No recibirás notificaciones.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun fetchAndDrawData() {
        try {
            val response = servicioApi.getLecturasPorfiltro(filtroId)
            if (response.isSuccessful && response.body() != null) {
                val lecturas: List<Lecturas> = response.body()!!
                if (lecturas.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        poblarGraficos(lecturas)
                    }
                } else {
                    Log.w("PollingLocal", "No hay lecturas para este filtro")
                }
            } else {
                Log.e("PollingLocal", "Error al cargar datos: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("PollingLocal", "Error de red: ${e.message}", e)
        }
    }

    private fun setupTdsChart() {
        chartTDS.description.text = "Historial de TDS (ppm) - Filtro $filtroId"
        chartTDS.legend.isEnabled = false
        chartTDS.axisRight.isEnabled = false
        chartTDS.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartTDS.xAxis.setLabelCount(6, true)
    }
    private fun setupPhChart() {
        chartPH.description.text = "Historial de pH - Filtro $filtroId"
        chartPH.legend.isEnabled = false
        chartPH.axisRight.isEnabled = false
        chartPH.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chartPH.xAxis.setLabelCount(6, true)
    }

    private fun poblarGraficos(lecturas: List<Lecturas>) {
        if (lecturas.isEmpty()) {
            Log.w("PoblarGraficos", "No hay lecturas para mostrar.")
            return
        }
        val entriesTDS = ArrayList<Entry>()
        val entriesPH = ArrayList<Entry>()
        val timestamps = ArrayList<String>()
        var tdsExcedido = false
        var phExcedido = false
        for ((index, lectura) in lecturas.withIndex()) {
            entriesTDS.add(Entry(index.toFloat(), lectura.valorTDS.toFloat()))
            entriesPH.add(Entry(index.toFloat(), lectura.valorPH))
            timestamps.add(lectura.fecha)
        }
        val ultimaLectura = lecturas.last()
        if (ultimaLectura.valorTDS > MAX_TDS_OPTIMO || ultimaLectura.valorTDS < MIN_TDS_OPTIMO) tdsExcedido = true
        if (ultimaLectura.valorPH > MAX_PH_OPTIMO || ultimaLectura.valorPH < MIN_PH_OPTIMO) phExcedido = true
        if ((tdsExcedido || phExcedido) && !alertaMostrada) {
            alertaMostrada = true
            mostrarAlertaUrgente()
        } else if (!tdsExcedido && !phExcedido) {
            alertaMostrada = false
        }
        val xAxisFormatter = FormateoFechas(timestamps)

        val colorTDS = if (tdsExcedido) Color.RED else Color.rgb(0, 150, 136)
        val dataSetTDS = LineDataSet(entriesTDS, "TDS")
        dataSetTDS.color = colorTDS
        dataSetTDS.setCircleColor(colorTDS)
        chartTDS.xAxis.valueFormatter = xAxisFormatter
        chartTDS.data = LineData(dataSetTDS)
        chartTDS.invalidate()
        val colorPH = if (phExcedido) Color.RED else Color.rgb(170, 0, 255)
        val dataSetPH = LineDataSet(entriesPH, "pH")
        dataSetPH.color = colorPH
        dataSetPH.setCircleColor(colorPH)
        chartPH.xAxis.valueFormatter = xAxisFormatter
        chartPH.data = LineData(dataSetPH)
        chartPH.invalidate()
        actualizarDoughnutTDS(ultimaLectura.valorTDS.toFloat(), tdsExcedido)
        actualizarDoughnutPH(ultimaLectura.valorPH, phExcedido)
        actualizarDoughnutFlujo(ultimaLectura.valorFlujo)
    }


    private fun actualizarDoughnutTDS(valorActual: Float, excedido: Boolean) {
        val rangoTotal = MAX_TDS_OPTIMO - MIN_TDS_OPTIMO
        val valorEnRango = (valorActual - MIN_TDS_OPTIMO).coerceIn(0f, rangoTotal)
        val restanteEnRango = rangoTotal - valorEnRango
        val entries = ArrayList<PieEntry>().apply {
            add(PieEntry(valorEnRango))   // Parte "llena"
            add(PieEntry(restanteEnRango)) // Parte "vacía"
        }
        val dataSet = PieDataSet(entries, "TDS")
        val colorLleno = if (excedido) Color.RED else Color.rgb(0, 200, 0)
        val colorVacio = Color.rgb(230, 230, 230)
        dataSet.colors = listOf(colorLleno, colorVacio)
        dataSet.setDrawValues(false)
        doughnutTDS.data = PieData(dataSet)
        doughnutTDS.centerText = "TDS\n${String.format("%.0f", valorActual)} ppm"
        doughnutTDS.invalidate()
    }
    private fun actualizarDoughnutPH(valorActual: Float, excedido: Boolean) {
        val rangoTotal = MAX_PH_OPTIMO - MIN_PH_OPTIMO // (8.5 - 6.5 = 2.0)
        val valorEnRango = (valorActual - MIN_PH_OPTIMO).coerceIn(0f, rangoTotal)
        val restanteEnRango = rangoTotal - valorEnRango
        val entries = ArrayList<PieEntry>().apply {
            add(PieEntry(valorEnRango))
            add(PieEntry(restanteEnRango))
        }
        val dataSet = PieDataSet(entries, "PH")
        val colorLleno = if (excedido) Color.RED else Color.rgb(0, 200, 0)
        val colorVacio = Color.rgb(230, 230, 230)
        dataSet.colors = listOf(colorLleno, colorVacio)
        dataSet.setDrawValues(false)
        doughnutPH.data = PieData(dataSet)
        doughnutPH.centerText = "PH\n${String.format("%.1f", valorActual)}"
        doughnutPH.invalidate()
    }
    private fun actualizarDoughnutFlujo(valorActual: Float) {
        val entries = ArrayList<PieEntry>().apply {
            // Una sola entrada para que el círculo esté "lleno"
            add(PieEntry(1f))
        }
        val dataSet = PieDataSet(entries, "FLUJO")
        val colorVerde = Color.rgb(0, 200, 0)
        dataSet.colors = listOf(colorVerde)
        dataSet.setDrawValues(false)
        doughnutFlujo.data = PieData(dataSet)
        doughnutFlujo.centerText = "FLUJO\n${String.format("%.1f", valorActual)} L/m"
        doughnutFlujo.invalidate()
    }


    private fun mostrarAlertaUrgente() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("¡Alerta de Filtro!")
                .setMessage("Debe cambiar de filtro urgentemente")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Entendido") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .create()
                .show()
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}