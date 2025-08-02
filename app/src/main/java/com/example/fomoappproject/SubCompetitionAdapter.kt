package com.example.fomoappproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubCompetitionAdapter(
    private val competitions: List<SubCompetition>,
    private val onClick: (SubCompetition) -> Unit
) : RecyclerView.Adapter<SubCompetitionAdapter.SubCompetitionViewHolder>() {

    class SubCompetitionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.textViewSubCompetitionName)
        val themeText: TextView = itemView.findViewById(R.id.textViewSubCompetitionTheme)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubCompetitionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sub_competition, parent, false)
        return SubCompetitionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubCompetitionViewHolder, position: Int) {
        val subCompetition = competitions[position]
        holder.nameText.text = subCompetition.name
        holder.themeText.text = "Theme: ${subCompetition.theme}"

        holder.itemView.setOnClickListener { onClick(subCompetition) }
    }

    override fun getItemCount(): Int = competitions.size
}
