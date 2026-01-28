package com.oz.assistant.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object DomUtils {

    // =====================
    // SEARCH
    // =====================

    fun hasText(root: AccessibilityNodeInfo?, text: String): Boolean {
        if (root == null) return false
        if (root.text?.toString()?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until root.childCount) {
            if (hasText(root.getChild(i), text)) return true
        }
        return false
    }

    fun findAllNodesByText(root: AccessibilityNodeInfo?, text: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result

        if (root.text?.toString()?.contains(text, ignoreCase = true) == true) {
            result.add(root)
        }

        for (i in 0 until root.childCount) {
            result.addAll(findAllNodesByText(root.getChild(i), text))
        }

        return result
    }

    fun findFirstNodeByAnyText(root: AccessibilityNodeInfo?, texts: List<String>): AccessibilityNodeInfo? {
        if (root == null) return null

        val t = root.text?.toString()
        if (t != null) {
            for (s in texts) {
                if (t.contains(s, ignoreCase = true)) {
                    return root
                }
            }
        }

        for (i in 0 until root.childCount) {
            val found = findFirstNodeByAnyText(root.getChild(i), texts)
            if (found != null) return found
        }

        return null
    }

    fun findScrollable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root

        for (i in 0 until root.childCount) {
            val found = findScrollable(root.getChild(i))
            if (found != null) return found
        }
        return null
    }

    fun scrollDown(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun findNodeById(root: AccessibilityNodeInfo?, idPart: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val id = root.viewIdResourceName
        if (id != null && id.contains(idPart)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val found = findNodeById(root.getChild(i), idPart)
            if (found != null) return found
        }

        return null
    }

    fun hasCheckedChild(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.isCheckable && node.isChecked) return true

        for (i in 0 until node.childCount) {
            if (hasCheckedChild(node.getChild(i))) return true
        }

        return false
    }

    fun findAllNodesByAnyText(root: AccessibilityNodeInfo?, texts: List<String>): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result

        val t = root.text?.toString()
        if (t != null) {
            for (s in texts) {
                if (t.contains(s, ignoreCase = true)) {
                    result.add(root)
                    break
                }
            }
        }

        for (i in 0 until root.childCount) {
            result.addAll(findAllNodesByAnyText(root.getChild(i), texts))
        }

        return result
    }

    fun findNodeByDesc(root: AccessibilityNodeInfo?, descPart: String): AccessibilityNodeInfo? {
        if (root == null) return null

        val desc = root.contentDescription?.toString()
        if (desc != null && desc.contains(descPart, ignoreCase = true)) {
            return root
        }

        for (i in 0 until root.childCount) {
            val found = findNodeByDesc(root.getChild(i), descPart)
            if (found != null) return found
        }

        return null
    }

    fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var n = node
        while (n != null) {
            if (n.isClickable) return n
            n = n.parent
        }
        return null
    }

    // =====================
    // ACTIONS
    // =====================

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        Log.d("OZ_BOT", "clickNode on ${node.className}")

        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        val parent = findClickableParent(node)
        if (parent != null) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        return false
    }

    fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)

        val x = r.centerX().toFloat()
        val y = r.centerY().toFloat()

        Log.d("OZ_BOT", "tapNode at: $x , $y")

        val path = Path()
        path.moveTo(x, y)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()

        return service.dispatchGesture(gesture, null, null)
    }

    fun tapNodeByBounds(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        return tapNode(service, node)
    }
}
