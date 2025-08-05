package com.example.fomoappproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrophyAdapter(private val trophies: List<Trophy>) : RecyclerView.Adapter<TrophyAdapter.TrophyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrophyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trophy, parent, false)
        return TrophyViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrophyViewHolder, position: Int) {
        val trophy = trophies[position]
        holder.bind(trophy)
    }

    override fun getItemCount(): Int = trophies.size

    inner class TrophyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val groupNameTextView: TextView = itemView.findViewById(R.id.textViewGroupName)
        private val endDateTextView: TextView = itemView.findViewById(R.id.textViewEndDate)
        private val activityCountTextView: TextView = itemView.findViewById(R.id.textViewActivityCount)

        fun bind(trophy: Trophy) {
            groupNameTextView.text = trophy.groupName
            endDateTextView.text = "Ended on: ${trophy.endDate}"
            activityCountTextView.text = "Activities: ${trophy.activityCount}"
        }
    }
}