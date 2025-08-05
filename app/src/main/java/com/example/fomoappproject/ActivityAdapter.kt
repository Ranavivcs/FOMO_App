package com.example.fomoappproject

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class ActivityAdapter(private val activityList: List<UserActivityWithGroup>) :
    RecyclerView.Adapter<ActivityAdapter.ActivityViewHolder>() {

    class ActivityViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.textViewItemTitle)
        val categoryText: TextView = itemView.findViewById(R.id.textViewItemCategory)
        val groupText: TextView = itemView.findViewById(R.id.textViewItemGroup)
        val dateText: TextView = itemView.findViewById(R.id.textViewItemDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActivityViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_activity, parent, false)
        return ActivityViewHolder(view)
    }

    override fun onBindViewHolder(holder: ActivityViewHolder, position: Int) {
        val activity = activityList[position]

        holder.titleText.text = activity.description.ifBlank { "No Description" }
        holder.categoryText.text = activity.category.ifBlank { "No Category" }
        holder.groupText.text = "Group: ${activity.groupName}"

        val date = activity.timestamp.toDate()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.dateText.text = dateFormat.format(date)
    }

    override fun getItemCount(): Int = activityList.size
}


