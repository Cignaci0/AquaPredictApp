package com.example.prueba.model

//Define la estructura de datos (Data Class) para un Filtro.
//Esta plantilla le dice a GSON cómo convertir el JSON de la API
data class Filtro (
    val ID_Filtro: Int,
    val ID_Usuario: Int
)