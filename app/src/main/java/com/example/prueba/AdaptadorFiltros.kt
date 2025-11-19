package com.example.prueba

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.prueba.model.Filtro

class FiltroAdapter(private val listaFiltros: List<Filtro>) : RecyclerView.Adapter<FiltroAdapter.FiltroViewHolder>() {

    // Esta clase interna representa la vista de un solo item (item_filtro.xml)
    class FiltroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvFiltroId: TextView = itemView.findViewById(R.id.tvFiltroId)
    }

    // Crea una nueva vista (invocado por el layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FiltroViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_filtro, parent, false)
        return FiltroViewHolder(view)
    }

    // Reemplaza el contenido de una vista (invocado por el layout manager)
    override fun onBindViewHolder(holder: FiltroViewHolder, position: Int) {
        val filtroActual = listaFiltros[position]
        val context = holder.itemView.context
        holder.tvFiltroId.text = "Filtro ID: ${filtroActual.ID_Filtro}"
        holder.itemView.setOnClickListener {
            val intent = Intent(context, LecturasActivity::class.java)
            intent.putExtra("ID_FILTRO_SELECCIONADO",filtroActual.ID_Filtro)
            context.startActivity(intent)
        }
    }
    // Devuelve el tamaño de tu dataset (invocado por el layout manager)
    override fun getItemCount() = listaFiltros.size
}