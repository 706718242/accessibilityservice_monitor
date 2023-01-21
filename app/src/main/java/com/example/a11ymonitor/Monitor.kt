package com.example.a11ymonitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

class Monitor: AccessibilityService() {
    companion object {
        private const val DUMP_NODE = true
        data class KeyLabel(val k: Int, val v: String)
        private const val TAG = "A11YMonitor"
        private fun bitsMaskToString(bitValue: Int, labels: List<KeyLabel>): String {
            Log.d(TAG, "bitValue is ${Integer.toHexString(bitValue)}")
            val values = mutableListOf<String>()
            for (i in labels.iterator()) {
                if (bitValue and i.k != 0) {
                    values.add(i.v)
                }
            }
            return values.joinToString { "|" }
        }
        private fun windowChangesToString(changes: Int): String {
            val kvs = listOf(
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_ADDED, "WINDOWS_CHANGE_ADDED"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_REMOVED, "WINDOWS_CHANGE_REMOVED"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_TITLE, "WINDOWS_CHANGE_TITLE"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_BOUNDS, "WINDOWS_CHANGE_BOUNDS"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_LAYER, "WINDOWS_CHANGE_LAYER"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_ACTIVE, "WINDOWS_CHANGE_ACTIVE"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_FOCUSED, "WINDOWS_CHANGE_FOCUSED"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED, "WINDOWS_CHANGE_ACCESSIBILITY_FOCUSED"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_PARENT, "WINDOWS_CHANGE_PARENT"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_CHILDREN, "WINDOWS_CHANGE_CHILDREN"),
                KeyLabel(AccessibilityEvent.WINDOWS_CHANGE_PIP, "WINDOWS_CHANGE_PIP"),
            )
            return bitsMaskToString(changes, kvs)
        }
        private fun contentChangesToString(changes: Int): String {
            val kvs = listOf(
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED, "CONTENT_CHANGE_TYPE_UNDEFINED"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE, "CONTENT_CHANGE_TYPE_SUBTREE"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT, "CONTENT_CHANGE_TYPE_TEXT"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION, "CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION, "CONTENT_CHANGE_TYPE_STATE_DESCRIPTION"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE, "CONTENT_CHANGE_TYPE_PANE_TITLE"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED, "CONTENT_CHANGE_TYPE_PANE_APPEARED"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED, "CONTENT_CHANGE_TYPE_PANE_DISAPPEARED"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_STARTED, "CONTENT_CHANGE_TYPE_DRAG_STARTED"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_DROPPED, "CONTENT_CHANGE_TYPE_DRAG_DROPPED"),
                KeyLabel(AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_CANCELLED, "CONTENT_CHANGE_TYPE_DRAG_CANCELLED"),
            )
            return bitsMaskToString(changes, kvs)
        }

        private fun nodeTreeTraversal(x: Int, root: AccessibilityNodeInfo, access: (x: Int, node: AccessibilityNodeInfo)-> Unit) {
            access(x, root)
            for (i in 0 until root.childCount) {
                val child = root.getChild(i)
                if (child != null) nodeTreeTraversal(x+1, child, access)
            }
        }

        private fun nodeTreeFindOne(root: AccessibilityNodeInfo, check: (node: AccessibilityNodeInfo)-> AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            val found = check(root)
            if (found != null) return found
            for (i in 0 until root.childCount) {
                val child = root.getChild(i)
                if (child != null) {
                    val found1 = nodeTreeFindOne(child, check)
                    if (found1 != null) return found1
                }
            }
            return null
        }
    }

    private var topPkgName: String? = null
    private var topClzName: String? = null
    private var screenRect: Rect = Rect()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "event: $event")
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val changes = windowChangesToString(event.windowChanges)
                Log.d(TAG, "changes: $changes")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    topPkgName = event.packageName.toString()
                    topClzName = event.className.toString()
                    rootInActiveWindow?.getBoundsInScreen(screenRect)
                    Log.d(TAG, "screen rect: $screenRect")
                } else {
                    if (topPkgName != event.packageName) {
                        Log.w(TAG, "topPackage changed, old is $topPkgName, new is ${event.packageName}")
                        return
                    }
                }

                Log.d(TAG, "topPkgName: $topPkgName, topClzName: $topClzName")

                if (rootInActiveWindow == null) Log.d(TAG, "rootInActiveWindow: null")
                else Log.d(TAG, "rootInActiveWindow: $rootInActiveWindow")
                if (event.source == null) Log.d(TAG, "event.source: null")
                else Log.d(TAG, "event.source: ${event.source}")

                val root = event.source ?: rootInActiveWindow
                val changes = contentChangesToString(event.contentChangeTypes)
                Log.d(TAG, "changes: $changes")
                Log.d(TAG, "root: $root")
                if (root == null) return

                if (DUMP_NODE) nodeTreeTraversal(0, root) { x, it ->
                    Log.d(TAG, "node[$x]: $it")
                }

                val found: AccessibilityNodeInfo?
                if (topPkgName == "com.tencent.android.qqdownloader" && topClzName == "com.tencent.pangu.activity.MixedAppDetailActivity") {
                    found = nodeTreeFindOne(root) once@{
                        if (it.className.toString() != "android.widget.TextView") return@once null
                        if (it.text == null) return@once null
                        if (it.text.startsWith("立即下载")) it else null
                    }
                } else if (topPkgName == "com.huawei.appmarket" && topClzName == "com.huawei.appmarket.service.distribution.deeplink.fulldetail.DisFullDetailActivity") {
                    found = nodeTreeFindOne(root) once@{
                        if (it.className.toString() != "android.widget.TextView") return@once null
                        if (it.text == null) return@once null
                        if (it.text.startsWith("安装 (")) it else null
                    }
                } else if (topPkgName == "com.xiaomi.market" && topClzName == "") {
                    found = null
                } else if (topPkgName == "com.bbk.appstore" && topClzName == "com.bbk.appstore.ui.details.AppDetailActivity") {
                    found = nodeTreeFindOne(root) once@{
                        if (it.className.toString() != "android.widget.TextView") return@once null
                        if (it.text == null) return@once null
                        if (it.text.toString() != "安装") return@once null
                        val btnRect = Rect()
                        it.getBoundsInScreen(btnRect)
                        if (abs(btnRect.centerX() - screenRect.centerX()) <= 10 // Center vertically
                            // && btnRect.width() >= 0.5*screenRect.width() // note: Large button but textview is small
                            && btnRect.centerY() >= 0.75*screenRect.centerY()) { // Bottom button
                            it
                        } else null
                    }
                } else if (topPkgName == "com.heytap.market" && topClzName == "com.heytap.cdo.client.detail.ui.ProductDetailActivity") {
                    found = nodeTreeFindOne(root) once@{
                        if (it.className.toString() != "android.widget.ImageView") return@once null
                        if (it.contentDescription == null) return@once null
                        if (it.contentDescription.toString() != "安装") return@once null
                        val btnRect = Rect()
                        it.getBoundsInScreen(btnRect)
                        if (abs(btnRect.centerX() - screenRect.centerX()) <= 10 // Center vertically
                            && btnRect.width() >= 0.5*screenRect.width() // Large button
                            && btnRect.centerY() >= 0.75*screenRect.centerY()) { // Bottom button
                            it
                        } else null
                    }
                } else if (topPkgName == "com.coolapk.market") {
                    if (topClzName == "com.coolapk.market.view.node.DynamicNodePageActivity") {
                        found = nodeTreeFindOne(root) once@{
                            if (it.className.toString() != "android.widget.TextView") return@once null
                            if (it.text == null) return@once null
                            if (it.text == "安装" || it.text == "重新下载") it else null
                        }
                    } else if (topClzName == "androidx.appcompat.app.AlertDialog") {
                        found = nodeTreeFindOne(root) once@{
                            if (it.className.toString() != "android.widget.Button") return@once null
                            if (it.text == null) return@once null
                            if (it.text == "重新下载") it else null
                        }
                    } else {
                        found = null
                    }
                } else {
                    found = null
                }

                if (found != null) {
                    Log.d(TAG, "found: $found")
                }
            }
            else -> assert(false)
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        Log.i(TAG, "onServiceConnected")
        super.onServiceConnected()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind, intent=$intent")
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
    }
}