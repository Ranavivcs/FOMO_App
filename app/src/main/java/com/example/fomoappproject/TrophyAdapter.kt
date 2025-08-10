package com.example.fomoappproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrophyAdapter(private val trophies: List<Trophy>) :
    RecyclerView.Adapter<TrophyAdapter.TrophyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrophyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trophy, parent, false)
        return TrophyViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrophyViewHolder, position: Int) {
        holder.bind(trophies[position])
    }

    override fun getItemCount(): Int = trophies.size

    inner class TrophyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emoji: TextView = itemView.findViewById(R.id.textViewTrophyEmoji)
        private val title: TextView = itemView.findViewById(R.id.textViewTrophyTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.textViewTrophySubtitle)
        private val meta: TextView = itemView.findViewById(R.id.textViewTrophyMeta)

        fun bind(t: Trophy) {
            emoji.text = "🏆"

            title.text = when (t.type.lowercase()) {
                "group" -> "${t.groupName} — Group Winner"
                else -> {
                    val sub = t.subName?.takeIf { it.isNotBlank() } ?: "Sub-competition"
                    "${t.groupName} — $sub"
                }
            }

            val winner = t.winnerName.ifBlank { "You" }
            subtitle.text = "Winner: $winner"

            val rightLabel = if (t.type.equals("group", true)) "Wins" else "Activities"
            meta.text = buildString {
                append("Ended on: ${t.endDate.ifBlank { "Unknown" }}")
                append(" • ")
                append("$rightLabel: ${t.activityCount}")
            }
        }
    }
}
