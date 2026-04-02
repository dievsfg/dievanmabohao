package com.diev.mabohao.ui

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diev.mabohao.databinding.ActivityAppSelectorBinding
import com.diev.mabohao.databinding.ItemAppBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppSelectorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppSelectorBinding
    private lateinit var adapter: AppAdapter
    private var allApps: List<AppInfo> = emptyList()

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        loadApps()
        setupSearch()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = AppAdapter { appInfo ->
            val resultIntent = Intent().apply {
                putExtra("package_name", appInfo.packageName)
                putExtra("app_name", appInfo.appName)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadApps()
        }
    }

    private fun loadApps() {
        binding.swipeRefresh.isRefreshing = true
        
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                loadAppsInBackground()
            }
            
            allApps = apps
            adapter.submitList(apps)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun loadAppsInBackground(): List<AppInfo> {
        val pm = packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isLaunchableApp(pm, it.packageName) }
            .map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    icon = pm.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    private fun isLaunchableApp(pm: PackageManager, packageName: String): Boolean {
        return pm.getLaunchIntentForPackage(packageName) != null
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase()
                val filtered = if (query.isEmpty()) {
                    allApps
                } else {
                    allApps.filter {
                        it.appName.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
                    }
                }
                adapter.submitList(filtered)
            }
        })
    }

    class AppAdapter(
        private val onSelect: (AppInfo) -> Unit
    ) : ListAdapter<AppInfo, AppAdapter.ViewHolder>(AppDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(appInfo: AppInfo) {
                binding.ivAppIcon.setImageDrawable(appInfo.icon)
                binding.tvAppName.text = appInfo.appName
                binding.tvPackage.text = appInfo.packageName
                
                binding.root.setOnClickListener {
                    onSelect(appInfo)
                }
            }
        }

        class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}