package com.example.prueba.model

import com.google.gson.annotations.SerializedName

// lo que hace el @serialName es decirle a android
// cuando veas "filtro_id" (esto es enviado por la api en json) sera la variable filtroId
// y asi con todos los demas
data class RespuestaPrediccion(
    @SerializedName("filtro_id")
    val filtroId: Int,

    @SerializedName("ultimo_ph_registrado")
    val ultimoPh: Float,

    @SerializedName("ultimo_tds_registrado")
    val ultimoTds: Int,

    @SerializedName("dias_restantes_aprox")
    val diasRestantes: String
)

