package com.diev.mabohao.ui

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diev.mabohao.R
import com.diev.mabohao.data.Rule
import com.diev.mabohao.databinding.ItemRuleBinding

class RuleAdapter(
    private val onToggle: (Rule, Boolean) -> Unit,
    private val onEdit: (Rule) -> Unit
) : ListAdapter<Rule, RuleAdapter.ViewHolder>(RuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: Rule) {
            val pm = binding.root.context.packageManager
            
            try {
                val appInfo = pm.getApplicationInfo(rule.packageName, 0)
                binding.ivAppIcon.setImageDrawable(pm.getApplicationIcon(appInfo))
                binding.tvAppName.text = pm.getApplicationLabel(appInfo)
            } catch (e: PackageManager.NameNotFoundException) {
                binding.ivAppIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                binding.tvAppName.text = rule.appLabel
            }
            
            binding.tvCode.text = binding.root.context.getString(
                R.string.code_format,
                rule.prefix,
                rule.code,
                rule.suffix
            )
            binding.tvPackage.text = rule.packageName
            
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = rule.isEnabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(rule, isChecked)
            }
            
            binding.btnEdit.setOnClickListener {
                onEdit(rule)
            }
            
            binding.root.setOnClickListener {
                onEdit(rule)
            }
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<Rule>() {
        override fun areItemsTheSame(oldItem: Rule, newItem: Rule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Rule, newItem: Rule): Boolean {
            return oldItem == newItem
        }
    }
}