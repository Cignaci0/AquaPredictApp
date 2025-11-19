package com.example.prueba.network

// Importa todos los "moldes" (Data Classes) que la API va a recibir o devolver
import com.example.prueba.model.Filtro
import com.example.prueba.model.Lecturas
import com.example.prueba.model.RespuestaPrediccion
import com.example.prueba.model.RespuestaLogin
import com.example.prueba.model.Usuario
// Importa las herramientas de Retrofit
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path


// el ServicioApi es donde le dices a android estudio
// la ruta y el metodo http por el que llegara el endpoint
interface ServicioApi {
    @POST("inicioSesion")
    fun login(@Body usuario: Usuario): Call<RespuestaLogin>

    @GET("usuarios/{id}/filtros")
    suspend fun getFiltrosPorUsuario(@Path("id") idUsuario: Int): Response<List<Filtro>>

    @GET("filtros/{id}/lecturas")
    suspend fun getLecturasPorfiltro(@Path("id") idFiltro:Int): Response<List<Lecturas>>

    @GET("filtros/{id}/predecir")
    suspend fun getPrediccionFiltro(@Path("id") idFiltro: Int): Response<RespuestaPrediccion>
}
