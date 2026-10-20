/*
 * Copyright (C) 2021-2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.doze

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class DozeService : Service() {
    private lateinit var pocketSensor: PocketSensor
    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }

    private val keyguardLockedStateListener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            KeyguardManager.KeyguardLockedStateListener { isLocked ->
                Log.d(TAG, "Keyguard locked state changed: isLocked=$isLocked")
                if (Utils.isPocketEnabled(this)) {
                    if (isLocked) {
                        pocketSensor.onKeyguardLocked()
                    } else {
                        pocketSensor.onUserUnlocked()
                    }
                }
            }
        } else {
            null
        }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> onDisplayOn()
                    Intent.ACTION_SCREEN_OFF -> onDisplayOff()
                    Intent.ACTION_USER_PRESENT -> onUserUnlocked()
                }
            }
        }

    override fun onCreate() {
        Log.d(TAG, "Creating service")
        pocketSensor =
            PocketSensor(
                this,
                resources.getString(R.string.pocket_sensor_type),
                resources.getFloat(R.dimen.pocket_sensor_value),
            )

        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, screenStateFilter)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && keyguardLockedStateListener != null) {
            try {
                keyguardManager?.addKeyguardLockedStateListener(mainExecutor, keyguardLockedStateListener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register KeyguardLockedStateListener", e)
            }
        }

        updateSensors()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateSensors()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && keyguardLockedStateListener != null) {
            try {
                keyguardManager?.removeKeyguardLockedStateListener(keyguardLockedStateListener)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister KeyguardLockedStateListener", e)
            }
        }
        pocketSensor.disable()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateSensors() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager != null && !powerManager.isInteractive) {
            onDisplayOff()
        } else {
            onDisplayOn()
        }
    }

    private fun onDisplayOn() {
        if (Utils.isPocketEnabled(this)) {
            pocketSensor.onDisplayOn()
        } else {
            pocketSensor.disable()
        }
    }

    private fun onDisplayOff() {
        if (Utils.isPocketEnabled(this)) {
            pocketSensor.onDisplayOff()
        } else {
            pocketSensor.disable()
        }
    }

    private fun onUserUnlocked() {
        if (Utils.isPocketEnabled(this)) {
            pocketSensor.onUserUnlocked()
        }
    }

    companion object {
        private const val TAG = "DozeService"
    }
}
