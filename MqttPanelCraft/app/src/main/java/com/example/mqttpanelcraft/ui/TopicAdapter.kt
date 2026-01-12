package com.example.mqttpanelcraft.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import java.util.Locale

class TopicAdapter(context: Context, private var items: List<ComponentData>) :
    ArrayAdapter<ComponentData>(context, R.layout.item_topic_dropdown, items) {

    private val inflater = LayoutInflater.from(context)
    private var filteredItems: List<ComponentData> = items

    fun updateData(newItems: List<ComponentData>) {
        this.items = newItems
        this.filteredItems = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int = filteredItems.size
    override fun getItem(position: Int): ComponentData? = filteredItems[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_topic_dropdown, parent, false)
        val item = getItem(position) ?: return view

        val tvTopic = view.findViewById<TextView>(R.id.tvDropdownTopic)
        val tvLabel = view.findViewById<TextView>(R.id.tvDropdownLabel)

        tvTopic.text = item.topicConfig
        tvLabel.text = item.label

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val results = FilterResults()
                
                val matches = if (query.isEmpty()) {
                    items
                } else {
                    items.filter {
                        it.topicConfig.lowercase(Locale.getDefault()).contains(query) ||
                        it.label.lowercase(Locale.getDefault()).contains(query)
                    }
                }
                
                results.values = matches
                results.count = matches.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = results?.values as? List<ComponentData> ?: emptyList()
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
            
            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? ComponentData)?.topicConfig ?: ""
            }
        }
    }
}
