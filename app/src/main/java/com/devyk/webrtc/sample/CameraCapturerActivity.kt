package com.devyk.webrtc.sample

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.devyk.webrtc.library.SurfaceTextureHelper
import com.devyk.webrtc.library.VideoCapturer
import com.devyk.webrtc.library.VideoFrame
import com.devyk.webrtc.library.VideoSink
import com.devyk.webrtc.library.base.Logging
import com.devyk.webrtc.library.capturer.camera.Camera1Enumerator
import com.devyk.webrtc.library.capturer.camera.Camera2Enumerator
import com.devyk.webrtc.library.capturer.camera.CameraEnumerator
import com.devyk.webrtc.library.capturer.camera.CapturerObserver
import com.devyk.webrtc.library.opengl.egl.EglBase
import com.devyk.webrtc.library.view.SurfaceViewRenderer
import com.devyk.webrtc.sample.CameraCapturerActivity.A.TAG
import java.util.concurrent.Executors

/**
 * <pre>
 *     author  : 马克
 *     time    : 2022/8/7
 *     mailbox : make@pplabs.org
 *     desc    :
 * </pre>
 */
class CameraCapturerActivity : AppCompatActivity() {
    val EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2"
    val EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE"

    object A {
        val TAG = javaClass.simpleName
    }

    private val videoWidth = 720
    private val videoHeight = 1280
    private val videoFps = 30
    private var videoCapturer: VideoCapturer? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var videoCapturerStopped = false

    val rootEglBase: EglBase = EglBase.create()
    var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var logToast: Toast? = null
    private var mediaProjectionPermissionResultData: Intent? = null
    private var mediaProjectionPermissionResultCode = 0
    private val CAPTURE_PERMISSION_REQUEST_CODE = 1

    // List of mandatory application permissions.
    private val MANDATORY_PERMISSIONS = arrayOf(
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.RECORD_AUDIO", "android.permission.INTERNET"
    )

    private class ProxyVideoSink : VideoSink {
        private var target: VideoSink? = null

        @Synchronized
        override fun onFrame(frame: VideoFrame?) {
            if (target == null) {
                Logging.d(
                    TAG,
                    "Dropping frame in proxy because target is null."
                )
                return
            }
            target?.onFrame(frame)
        }

        @Synchronized
        fun setTarget(target: VideoSink?) {
            this.target = target
        }
    }


    private val localProxyVideoSink = ProxyVideoSink()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_capturer)
        videoCapturerStopped = true
        localProxyVideoSink.setTarget(findViewById<SurfaceViewRenderer>(R.id.local_surface));

        // Create video renderers.
        findViewById<SurfaceViewRenderer>(R.id.local_surface).init(rootEglBase.getEglBaseContext(), null)
        findViewById<AppCompatButton>(R.id.start).setOnClickListener {
            startCapturer()
        }
        findViewById<AppCompatButton>(R.id.stop).setOnClickListener {
            stopCapturer()
        }


        // Check for mandatory permissions.
        for (permission in MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                logAndToast("Permission $permission is not granted")
                setResult(RESULT_CANCELED)
                finish()
                return
            }
        }
    }

    private fun logAndToast(msg: String) {
        Log.d(TAG, msg)
        logToast?.cancel()
        logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT)
        logToast?.show()
    }
    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE) return
        mediaProjectionPermissionResultCode = resultCode
        mediaProjectionPermissionResultData = data
    }

    @TargetApi(19)
    private fun getSystemUiVisibility(): Int {
        var flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags = flags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        return flags
    }

    public fun startCapturer() {
         videoCapturer = createVideoCapturer()
        executor.execute(Runnable {
            Log.d(
                TAG,
                "Restart video source."
            )
            if (videoCapturerStopped) {
                surfaceTextureHelper =
                    SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext())
                videoCapturer?.initialize(
                    surfaceTextureHelper,
                    this,
                    object : CapturerObserver {
                        override fun onCapturerStarted(success: Boolean) {
                            videoCapturerStopped = false;
                        }
                        override fun onCapturerStopped() {
                        }
                        override fun onFrameCaptured(frame: VideoFrame?) {
                            localProxyVideoSink.onFrame(frame);
                        }
                    }
                )
                videoCapturer?.startCapture(videoWidth, videoHeight, videoFps)
            }
        })
    }

    public fun stopCapturer() {
        if (videoCapturer != null && !videoCapturerStopped) {
            try {
                videoCapturer?.stopCapture()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            videoCapturerStopped = true
            videoCapturer?.dispose()
            videoCapturer = null
        }
    }


    @Nullable
    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?

        if (useCamera2()) {
            videoCapturer = createCameraCapturer(Camera2Enumerator(this))
        } else {
            videoCapturer = createCameraCapturer(Camera1Enumerator(captureToTexture()))
        }

        return videoCapturer
    }


    @Nullable
    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames: Array<String> = enumerator.getDeviceNames()

        // First, try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // Front facing camera not found, try something else
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }


    private fun captureToTexture(): Boolean {
        return intent.getBooleanExtra(
            EXTRA_CAPTURETOTEXTURE_ENABLED,
            true
        )
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this) && intent.getBooleanExtra(
            EXTRA_CAMERA2,
            false
        )
    }
}