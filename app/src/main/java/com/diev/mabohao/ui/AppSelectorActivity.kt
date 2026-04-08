package com.diev.mabohao.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.diev.mabohao.R
import com.diev.mabohao.databinding.ActivityAppSelectorBinding
import com.diev.mabohao.databinding.ItemAppBinding
import com.diev.mabohao.util.AppCacheHelper
import kotlinx.coroutines.launch

class AppSelectorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAppSelectorBinding
    private lateinit var adapter: AppAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private var currentQuery: String = ""
    private var previousListSize: Int = 0
    private var shouldPreserveScrollPosition: Boolean = false

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()
        setupSearch()
        setupFilterButton()
        observeApps()
        loadApps()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 状态栏高度的 padding 加给 AppBarLayout
            val appBarLayout = binding.root.getChildAt(0)
            appBarLayout.updatePadding(top = insets.top)
            
            // 为主内容区底部增加导航栏高度的 padding
            val contentView = binding.root.getChildAt(1) // LinearLayout
            contentView.updatePadding(bottom = insets.bottom)
            
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter { appInfo ->
            val resultIntent = Intent().apply {
                putExtra("package_name", appInfo.packageName)
                putExtra("app_name", appInfo.appName)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        
        binding.rvApps.layoutManager = layoutManager
        binding.rvApps.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadApps()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s.toString()
                AppCacheHelper.applyFilterAndSort(currentQuery)
            }
        })
    }

    private fun setupFilterButton() {
        binding.btnFilter.setOnClickListener { view ->
            showFilterMenu(view)
        }
    }

    private fun showFilterMenu(view: android.view.View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.menu_app_list, popupMenu.menu)
        
        // 设置当前状态
        popupMenu.menu.findItem(R.id.menu_show_system).isChecked = AppCacheHelper.showSystemApps
        when (AppCacheHelper.sortMethod) {
            AppCacheHelper.SortMethod.BY_LABEL -> popupMenu.menu.findItem(R.id.menu_sort_by_label).isChecked = true
            AppCacheHelper.SortMethod.BY_PACKAGE_NAME -> popupMenu.menu.findItem(R.id.menu_sort_by_package_name).isChecked = true
            AppCacheHelper.SortMethod.BY_INSTALL_TIME -> popupMenu.menu.findItem(R.id.menu_sort_by_install_time).isChecked = true
            AppCacheHelper.SortMethod.BY_UPDATE_TIME -> popupMenu.menu.findItem(R.id.menu_sort_by_update_time).isChecked = true
        }
        popupMenu.menu.findItem(R.id.menu_reverse_order).isChecked = AppCacheHelper.reverseOrder
        
        popupMenu.setOnMenuItemClickListener { item ->
            // 只有倒序操作才保持滚动位置
            shouldPreserveScrollPosition = item.itemId == R.id.menu_reverse_order
            
            when (item.itemId) {
                R.id.menu_show_system -> {
                    item.isChecked = !item.isChecked
                    AppCacheHelper.showSystemApps = item.isChecked
                }
                R.id.menu_sort_by_label -> {
                    item.isChecked = true
                    AppCacheHelper.sortMethod = AppCacheHelper.SortMethod.BY_LABEL
                }
                R.id.menu_sort_by_package_name -> {
                    item.isChecked = true
                    AppCacheHelper.sortMethod = AppCacheHelper.SortMethod.BY_PACKAGE_NAME
                }
                R.id.menu_sort_by_install_time -> {
                    item.isChecked = true
                    AppCacheHelper.sortMethod = AppCacheHelper.SortMethod.BY_INSTALL_TIME
                }
                R.id.menu_sort_by_update_time -> {
                    item.isChecked = true
                    AppCacheHelper.sortMethod = AppCacheHelper.SortMethod.BY_UPDATE_TIME
                }
                R.id.menu_reverse_order -> {
                    item.isChecked = !item.isChecked
                    AppCacheHelper.reverseOrder = item.isChecked
                }
            }
            AppCacheHelper.applyFilterAndSort(currentQuery)
            true
        }
        popupMenu.show()
    }

    /**
     * 保存当前滚动位置的相对比例
     */
    private fun saveScrollPosition(): ScrollPosition {
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)
        val offset = firstVisibleView?.top ?: 0
        val totalItemCount = adapter.itemCount
        
        return ScrollPosition(
            firstVisiblePosition = firstVisiblePosition,
            offset = offset,
            totalItemCount = totalItemCount
        )
    }

    /**
     * 恢复滚动位置，根据比例计算新位置
     */
    private fun restoreScrollPosition(position: ScrollPosition) {
        val newTotalItemCount = adapter.itemCount
        if (position.totalItemCount == 0 || newTotalItemCount == 0) return
        
        // 计算当前可见位置在列表中的相对比例
        val ratio = position.firstVisiblePosition.toFloat() / position.totalItemCount.toFloat()
        
        // 根据比例计算新位置
        val newFirstVisiblePosition = (ratio * newTotalItemCount).toInt().coerceIn(0, newTotalItemCount - 1)
        
        // 计算偏移量的比例
        val offsetRatio = position.offset.toFloat()
        
        // 滚动到新位置
        layoutManager.scrollToPositionWithOffset(newFirstVisiblePosition, offsetRatio.toInt())
    }

    data class ScrollPosition(
        val firstVisiblePosition: Int,
        val offset: Int,
        val totalItemCount: Int
    )

    private fun observeApps() {
        lifecycleScope.launch {
            AppCacheHelper.appList.collect { apps ->
                // 保存滚动位置（仅当需要保持位置时）
                val scrollPosition = if (shouldPreserveScrollPosition && previousListSize > 0) {
                    saveScrollPosition()
                } else {
                    null
                }
                
                val appInfoList = apps.map { cachedApp ->
                    AppInfo(
                        packageName = cachedApp.packageName,
                        appName = cachedApp.appName,
                        icon = cachedApp.icon
                    )
                }
                
                adapter.submitList(appInfoList) {
                    // 列表更新完成后恢复滚动位置
                    if (scrollPosition != null) {
                        restoreScrollPosition(scrollPosition)
                        shouldPreserveScrollPosition = false
                    }
                    previousListSize = appInfoList.size
                }
            }
        }
        
        lifecycleScope.launch {
            AppCacheHelper.isRefreshing.collect { isRefreshing ->
                binding.swipeRefresh.isRefreshing = isRefreshing
            }
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            AppCacheHelper.loadApps(packageManager)
        }
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