package com.voicehelp.commands.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.core.content.ContextCompat
import com.voicehelp.R

class FlashlightAction(private val context: Context) {

    companion object {
        @Volatile
        private var torchOn = false
    }

    fun execute(): String {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return context.getString(R.string.reply_no_flash)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return context.getString(R.string.reply_no_permission, context.getString(R.string.perm_camera))
        }
        return try {
            val manager = context.getSystemService(CameraManager::class.java)
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return context.getString(R.string.reply_no_flash)

            torchOn = !torchOn
            manager.setTorchMode(cameraId, torchOn)
            context.getString(if (torchOn) R.string.reply_flash_on else R.string.reply_flash_off)
        } catch (e: Exception) {
            context.getString(R.string.reply_no_permission, context.getString(R.string.perm_camera))
        }
    }
}