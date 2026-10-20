/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.app.KeyguardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

class TouchBlocker(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)!!
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var blockerLayout: View? = null
    private var isBlockerAdded = false

    private val backCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        OnBackInvokedCallback {
            Log.d(TAG, "Back gesture intercepted and consumed by PocketTouchBlocker")
        }
    } else {
        null
    }

    private fun createBlockerView(): View {
        return object : View(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> return true
                    KeyEvent.KEYCODE_VOLUME_UP,
                    KeyEvent.KEYCODE_VOLUME_DOWN,
                    KeyEvent.KEYCODE_VOLUME_MUTE -> return false
                }
                return super.dispatchKeyEvent(event)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                val w = right - left
                val h = bottom - top
                if (w > 0 && h > 0) {
                    systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
                    try {
                        findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                            backCallback
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to register OnBackInvokedCallback", e)
                    }
                }
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null) {
                    try {
                        findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(backCallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to unregister OnBackInvokedCallback", e)
                    }
                }
            }
        }.apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setOnTouchListener { _, _ -> true }
            setOnGenericMotionListener { _, _ -> true }
            setOnHoverListener { _, _ -> true }
        }
    }

    fun enable() {
        if (keyguardManager?.isKeyguardLocked == false) {
            Log.d(TAG, "enable() skipped: device is not locked")
            return
        }
        handler.post {
            if (keyguardManager?.isKeyguardLocked == false) {
                Log.d(TAG, "enable() skipped on UI thread: device is not locked")
                return@post
            }
            if (!isBlockerAdded) {
                blockerLayout = createBlockerView()
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSPARENT,
                ).apply {
                    title = "PocketTouchBlocker"
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                try {
                    windowManager.addView(blockerLayout, params)
                    isBlockerAdded = true
                    Log.d(TAG, "Touch blocker view added to WindowManager (TYPE_SYSTEM_ERROR + SHOW_WHEN_LOCKED)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add with TYPE_SYSTEM_ERROR, falling back to TYPE_APPLICATION_OVERLAY", e)
                    try {
                        params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        windowManager.addView(blockerLayout, params)
                        isBlockerAdded = true
                        Log.d(TAG, "Touch blocker view added to WindowManager (TYPE_APPLICATION_OVERLAY + SHOW_WHEN_LOCKED)")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to add touch blocker view", e2)
                        blockerLayout = null
                    }
                }
            }
        }
    }

    fun disable() {
        handler.post {
            if (isBlockerAdded && blockerLayout != null) {
                try {
                    windowManager.removeView(blockerLayout)
                    Log.d(TAG, "Touch blocker view removed from WindowManager")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove touch blocker view", e)
                } finally {
                    isBlockerAdded = false
                    blockerLayout = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "TouchBlocker"
    }
}
