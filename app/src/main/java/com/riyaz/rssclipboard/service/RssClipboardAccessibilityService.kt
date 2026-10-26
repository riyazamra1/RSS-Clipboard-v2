package com.riyaz.rssclipboard.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.riyaz.rssclipboard.data.ClipDatabase
import com.riyaz.rssclipboard.data.ClipEntity
import com.riyaz.rssclipboard.data.ContentDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RssClipboardAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastCapturedText: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            captureSystemClipboard()
        }
    }

    private fun captureSystemClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (!clipboard.hasPrimaryClip()) return
        val clipData: ClipData = clipboard.primaryClip ?: return
        if (clipData.itemCount == 0) return

        val text = clipData.getItemAt(0).coerceToText(this)?.toString()?.trim()
        if (text.isNullOrBlank() || text == lastCapturedText) return

        lastCapturedText = text
        serviceScope.launch {
            val dao = ClipDatabase.getInstance(applicationContext).clipDao()
            val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
            dao.deleteExpiredUnpinned(twentyFourHoursAgo)

            val existing = dao.findByContent(text)
            val now = System.currentTimeMillis()
            val detectedCategory = ContentDetector.detectCategory(text)

            if (existing != null) {
                dao.update(
                    existing.copy(
                        createdAt = now,
                        category = detectedCategory,
                        isSensitive = existing.isSensitive || detectedCategory == com.riyaz.rssclipboard.data.ClipCategory.SENSITIVE
                    )
                )
            } else {
                dao.insert(
                    ClipEntity(
                        content = text,
                        category = detectedCategory,
                        isPinned = false,
                        isSensitive = detectedCategory == com.riyaz.rssclipboard.data.ClipCategory.SENSITIVE,
                        isManualSave = false,
                        createdAt = now
                    )
                )
            }
        }
    }

    override fun onInterrupt() {}
}