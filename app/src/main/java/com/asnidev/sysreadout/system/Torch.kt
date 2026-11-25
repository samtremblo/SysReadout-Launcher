package com.asnidev.sysreadout.system

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/**
 * The flashlight, for the swipe gesture. Needs no permission: Android lets any
 * app switch the torch. Its state is tracked through the system callback, so a
 * torch switched on from quick settings is switched off by the next swipe.
 */
object Torch {
    private var cameraId: String? = null
    private var on = false
    private var listening = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) on = enabled
        }
    }

    fun available(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    /** False when the phone has no flash or the camera is busy. */
    fun toggle(context: Context): Boolean {
        val cm = context.getSystemService(CameraManager::class.java) ?: return false
        return try {
            val id = cameraId ?: cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }?.also { cameraId = it } ?: return false
            if (!listening) {
                cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
                listening = true
            }
            cm.setTorchMode(id, !on)
            on = !on
            true
        } catch (e: Exception) { // CameraAccessException, IllegalArgumentException
            false
        }
    }
}
