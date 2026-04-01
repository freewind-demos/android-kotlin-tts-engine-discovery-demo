package com.example.ttsenginediscovery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.ttsenginediscovery.databinding.ItemEngineBinding
import com.example.ttsenginediscovery.databinding.ItemSectionBinding

sealed interface EngineRow {
    data class Section(val title: String) : EngineRow
    data class Engine(val title: String, val subtitle: String) : EngineRow
}

class EngineRowsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<EngineRow>()

    fun submitList(list: List<EngineRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is EngineRow.Section -> VIEW_SECTION
        is EngineRow.Engine -> VIEW_ENGINE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_SECTION -> SectionVH(ItemSectionBinding.inflate(inflater, parent, false))
            VIEW_ENGINE -> EngineVH(ItemEngineBinding.inflate(inflater, parent, false))
            else -> error("unknown viewType $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = items[position]) {
            is EngineRow.Section -> (holder as SectionVH).bind(row)
            is EngineRow.Engine -> (holder as EngineVH).bind(row)
        }
    }

    private class SectionVH(private val binding: ItemSectionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: EngineRow.Section) {
            binding.textSection.text = row.title
        }
    }

    private class EngineVH(private val binding: ItemEngineBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(row: EngineRow.Engine) {
            binding.textTitle.text = row.title
            binding.textSubtitle.text = row.subtitle
        }
    }

    companion object {
        private const val VIEW_SECTION = 0
        private const val VIEW_ENGINE = 1
    }
}
