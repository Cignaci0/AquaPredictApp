package com.example.prueba.model

//Define la estructura de datos (Data Class) para un Usuario.
//Esta plantilla le dice a GSON cómo convertir el JSON de la API
data class Usuario(
    val rut: String,
    val contrasena: String
)
