package com.voicehelp.commands.actions

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.voicehelp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PhotoAction(private val context: Context) {

    suspend fun execute(): String = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext context.getString(
                R.string.reply_no_permission, context.getString(R.string.perm_camera)
            )
        }
        try {
            val bytes = takePicture() ?: return@withContext context.getString(R.string.reply_photo_failed)
            saveToGallery(bytes)
            context.getString(R.string.reply_photo_taken)
        } catch (e: Exception) {
            context.getString(R.string.reply_photo_failed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun takePicture(): ByteArray? {
        val manager = context.getSystemService(CameraManager::class.java)
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull() ?: return null

        val characteristics = manager.getCameraCharacteristics(cameraId)
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG) ?: return null
        val size = sizes.firstOrNull() ?: return null

        val handlerThread = HandlerThread("photo").apply { start() }
        val handler = Handler(handlerThread.looper)

        var device: CameraDevice? = null
        val openLatch = CountDownLatch(1)
        val openError = AtomicReference<Exception?>(null)

        try {
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    openLatch.countDown()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    openError.set(IllegalStateException("Камера отключена"))
                    openLatch.countDown()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    openError.set(IllegalStateException("Ошибка камеры: $error"))
                    openLatch.countDown()
                }
            }, handler)

            if (!openLatch.await(5, TimeUnit.SECONDS)) return null
            val opened = device ?: return null

            val result = AtomicReference<ByteArray?>(null)
            val captureLatch = CountDownLatch(1)
            val imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    result.set(bytes)
                    image.close()
                }
                captureLatch.countDown()
            }, handler)

            val sessionLatch = CountDownLatch(1)
            opened.createCaptureSession(
                listOf(imageReader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        sessionLatch.countDown()
                        val request = opened.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            .apply {
                                addTarget(imageReader.surface)
                                set(CaptureRequest.JPEG_ORIENTATION, 90)
                            }
                        session.capture(request.build(), null, handler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        sessionLatch.countDown()
                        captureLatch.countDown()
                    }
                },
                handler
            )

            sessionLatch.await(5, TimeUnit.SECONDS)
            captureLatch.await(5, TimeUnit.SECONDS)
            return result.get()
        } catch (e: Exception) {
            openError.set(e)
            return null
        } finally {
            try {
                device?.close()
            } catch (_: Exception) {
            }
            handlerThread.quitSafely()
        }
    }

    private fun saveToGallery(bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "VoiceHelp_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VoiceHelp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
        } finally {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }
}