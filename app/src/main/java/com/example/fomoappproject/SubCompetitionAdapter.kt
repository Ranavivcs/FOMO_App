package com.example.fomoappproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SubCompetitionAdapter(
    private val competitionList: List<SubCompetition>,
    private val onClick: (SubCompetition) -> Unit,
    private val onLongClick: (SubCompetition) -> Unit
) : RecyclerView.Adapter<SubCompetitionAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val competitionName: TextView = itemView.findViewById(R.id.textViewSubCompetitionName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sub_competition, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val competition = competitionList[position]
        holder.competitionName.text = competition.name

        holder.itemView.setOnClickListener {
            onClick(competition)
        }

        holder.itemView.setOnLongClickListener {
            onLongClick(competition)
            true
        }
    }

    override fun getItemCount(): Int = competitionList.size
}
