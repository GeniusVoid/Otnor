package com.brainer.otnor.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.time.Instant

object ScreenTextCapture {

    fun captureCurrentWindow(service: BrainerAccessibilityService): CaptureSnapshot? {
        val root = service.rootInActiveWindow ?: return null
        val pkgName = root.packageName?.toString() ?: service.currentPackageName.ifEmpty { "unknown" }
        val actName = service.currentActivityName
        val nodes = mutableListOf<CapturedNode>()
        traverseNodeTree(root, nodes)
        return CaptureSnapshot(
            package_name = pkgName,
            activity_name = actName,
            texts = nodes,
            capture_time = Instant.now().toString()
        )
    }

    fun traverseNodeTree(node: AccessibilityNodeInfo?, results: MutableList<CapturedNode>) {
        if (node == null) return
        val nodeText = node.text?.toString()?.trim()
        val contentDesc = node.contentDescription?.toString()?.trim()
        val effectiveText = when {
            !nodeText.isNullOrEmpty() -> nodeText
            !contentDesc.isNullOrEmpty() -> contentDesc
            else -> null
        }

        if (!effectiveText.isNullOrEmpty()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            results.add(
                CapturedNode(
                    text = effectiveText,
                    view_id = node.viewIdResourceName,
                    `class` = node.className?.toString(),
                    bounds = BoundsRect.fromRect(rect),
                    clickable = node.isClickable,
                    scrollable = node.isScrollable,
                    editable = node.isEditable,
                    content_description = contentDesc
                )
            )
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            } ?: continue
            traverseNodeTree(child, results)
        }
    }

    fun findNodesByText(
        service: AccessibilityService,
        query: String,
        exactMatch: Boolean
    ): List<CapturedNode> {
        val root = service.rootInActiveWindow ?: return emptyList()
        val allNodes = mutableListOf<CapturedNode>()
        traverseNodeTree(root, allNodes)
        return allNodes.filter { node ->
            if (exactMatch) {
                node.text.equals(query, ignoreCase = true)
            } else {
                node.text.contains(query, ignoreCase = true)
            }
        }
    }

    fun clickNode(
        service: AccessibilityService,
        text: String?,
        resourceId: String?
    ): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val targetNodes = mutableListOf<AccessibilityNodeInfo>()

        if (!resourceId.isNullOrBlank()) {
            val foundById = root.findAccessibilityNodeInfosByViewId(resourceId)
            if (!foundById.isNullOrEmpty()) {
                targetNodes.addAll(foundById)
            }
        }
        if (targetNodes.isEmpty() && !text.isNullOrBlank()) {
            val foundByText = root.findAccessibilityNodeInfosByText(text)
            if (!foundByText.isNullOrEmpty()) {
                targetNodes.addAll(foundByText)
            }
        }

        for (node in targetNodes) {
            var current: AccessibilityNodeInfo? = node
            while (current != null) {
                if (current.isClickable) {
                    val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) return true
                }
                current = current.parent
            }
            // Fallback: try direct ACTION_CLICK on the node itself
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    fun performNodeAction(
        service: AccessibilityService,
        text: String?,
        resourceId: String?,
        actionName: String,
        arguments: Map<String, String>?
    ): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val candidates = mutableListOf<AccessibilityNodeInfo>()

        if (!resourceId.isNullOrBlank()) {
            root.findAccessibilityNodeInfosByViewId(resourceId)?.let { candidates.addAll(it) }
        }
        if (candidates.isEmpty() && !text.isNullOrBlank()) {
            root.findAccessibilityNodeInfosByText(text)?.let { candidates.addAll(it) }
        }
        if (candidates.isEmpty()) {
            // If no selector provided or matched, find focused or editable node
            findFirstEditableOrFocused(root)?.let { candidates.add(it) }
        }

        val actionInt = mapActionName(actionName)
        val bundle = Bundle()
        arguments?.forEach { (k, v) ->
            bundle.putCharSequence(k, v)
        }

        for (node in candidates) {
            val ok = if (bundle.isEmpty) {
                node.performAction(actionInt)
            } else {
                node.performAction(actionInt, bundle)
            }
            if (ok) return true
        }
        return false
    }

    fun performScroll(service: AccessibilityService, direction: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val scrollable = findFirstScrollable(root) ?: root
        return when (direction.lowercase()) {
            "forward", "down" -> scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "backward", "up" -> scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "scroll_to_top" -> {
                var moved = false
                repeat(10) {
                    if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                        moved = true
                    }
                }
                moved
            }
            "scroll_to_bottom" -> {
                var moved = false
                repeat(10) {
                    if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                        moved = true
                    }
                }
                moved
            }
            else -> scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditableOrFocused(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable || node.isFocused) return node
        for (i in 0 until node.childCount) {
            val found = findFirstEditableOrFocused(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun mapActionName(name: String): Int {
        return when (name.uppercase()) {
            "ACTION_CLICK", "CLICK" -> AccessibilityNodeInfo.ACTION_CLICK
            "ACTION_LONG_CLICK", "LONG_CLICK" -> AccessibilityNodeInfo.ACTION_LONG_CLICK
            "ACTION_SET_TEXT", "SET_TEXT" -> AccessibilityNodeInfo.ACTION_SET_TEXT
            "ACTION_FOCUS", "FOCUS" -> AccessibilityNodeInfo.ACTION_FOCUS
            "ACTION_CLEAR_FOCUS" -> AccessibilityNodeInfo.ACTION_CLEAR_FOCUS
            "ACTION_SELECT" -> AccessibilityNodeInfo.ACTION_SELECT
            "ACTION_COPY" -> AccessibilityNodeInfo.ACTION_COPY
            "ACTION_PASTE" -> AccessibilityNodeInfo.ACTION_PASTE
            "ACTION_CUT" -> AccessibilityNodeInfo.ACTION_CUT
            "ACTION_SCROLL_FORWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "ACTION_SCROLL_BACKWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_CLICK
        }
    }
}
