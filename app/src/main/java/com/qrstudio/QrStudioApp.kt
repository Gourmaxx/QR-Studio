package com.qrstudio

import android.app.Application
import com.qrstudio.core.data.HistoryRepository
import com.qrstudio.widget.QrWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class QrStudioApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        HistoryRepository.init(this)
        // Keep the home-screen widget in sync with the most recent pinned entry.
        // The first emission also refreshes the widget on process start, which
        // is a no-op when none is placed.
        appScope.launch {
            HistoryRepository.items
                .map { items -> QrWidgetProvider.pinnedEntry(items) }
                .distinctUntilChanged()
                .collect { QrWidgetProvider.updateAll(this@QrStudioApp) }
        }
    }
}
