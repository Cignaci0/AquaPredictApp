package com.example.prueba.model

import com.google.gson.annotations.SerializedName

// lo que hace el @serialName es decirle a android
// cuando veas "ID_Lectura" (esto es enviado por la api en json) sera la variable idLectura
// y asi con todos los demas
data class Lecturas (
    @SerializedName("ID_Lectura")
    val idLectura: Int,

    @SerializedName("ID_Filtro")
    val idFiltro: Int,

    @SerializedName("Fecha")
    val fecha: String,

    @SerializedName("Valor_pH")
    val valorPH: Float,

    @SerializedName("Valor_TDS")
    val valorTDS: Int,

    @SerializedName("Valor_Flujo")
    val valorFlujo: Float
)
