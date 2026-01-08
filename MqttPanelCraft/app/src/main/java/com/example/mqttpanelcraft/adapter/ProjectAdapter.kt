package com.example.mqttpanelcraft.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.Project
import com.google.android.material.chip.Chip

import android.widget.PopupMenu

class ProjectAdapter(
    private var projects: List<Project>,
    private val onProjectClick: (Project) -> Unit,
    private val onMenuClick: (Project, String) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProjectName: TextView = itemView.findViewById(R.id.tvProjectName)
        val tvBrokerUrl: TextView = itemView.findViewById(R.id.tvBrokerUrl)
        val chipType: Chip = itemView.findViewById(R.id.chipType)
        val viewStatus: View = itemView.findViewById(R.id.viewStatus)
        val ivMenu: ImageView = itemView.findViewById(R.id.ivMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]
        holder.tvProjectName.text = project.name
        holder.tvBrokerUrl.text = project.broker
        holder.chipType.text = project.type.name

        // Status Dot Color
        val statusDrawable = if (project.isConnected) {
            R.drawable.shape_circle_green
        } else {
            R.drawable.shape_circle_red
        }
        holder.viewStatus.setBackgroundResource(statusDrawable)

        holder.itemView.setOnClickListener {
            onProjectClick(project)
        }
        
        // Handle menu click
        holder.ivMenu.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add("Edit")
            popup.menu.add("Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Edit" -> onMenuClick(project, "EDIT")
                    "Delete" -> onMenuClick(project, "DELETE")
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount() = projects.size
    
    fun updateData(newProjects: List<Project>) {
        projects = newProjects
        notifyDataSetChanged()
    }
    
    fun removeItem(project: Project) {
        val position = projects.indexOf(project)
        if (position != -1) {
            projects = projects.toMutableList().apply { removeAt(position) }
            notifyItemRemoved(position)
        }
    }
}
