package com.example.prueba.util

import android.util.Log
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

// Esta clase se especializa en "traducir" los valores numéricos del eje X
// (que son índices de array, ej: 0.0, 1.0, 2.0)
// a un formato de fecha legible (ej: "10/11", "11/11", "12/11")
class FormateoFechas(private val timestamps: List<String>) : ValueFormatter() {
    private val inputFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    private val outputFormatter = SimpleDateFormat("dd/MM", Locale.getDefault())
    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val index = value.toInt()
        return if (index >= 0 && index < timestamps.size) {
            try {
                val date = inputFormatter.parse(timestamps[index])
                if (date != null) {
                    outputFormatter.format(date)
                } else {
                    index.toString()
                }
            } catch (e: ParseException) {
                Log.w("AxisFormatter", "Error parseando fecha: ${timestamps[index]}", e)
                index.toString()
            }
        } else {
            ""
        }
    }
}