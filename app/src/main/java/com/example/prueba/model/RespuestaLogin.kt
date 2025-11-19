package com.example.prueba.model

//Define la estructura de datos (Data Class) para la respuesta del login.
//Esta plantilla le dice a GSON cómo convertir el JSON de la API
data class RespuestaLogin(
    val exito: Boolean,
    val mensaje: String,
    val id_usuario: Int,)
