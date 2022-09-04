package com.devyk.webrtc.sample

import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

/**
 * <pre>
 *     author  : 马克
 *     time    : 2022/8/7
 *     mailbox : make@pplabs.org
 *     desc    :
 * </pre>
 */
class MainActivity : AppCompatActivity(),View.OnClickListener {
    private val PERMISSION_REQUEST = 2
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.main)
        // Check for mandatory permissions.
        requestPermissions()
        findViewById<AppCompatButton>(R.id.camera_capturer).setOnClickListener(this);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Dynamic permissions are not required before Android M.
            return
        }
        val missingPermissions = getMissingPermissions()
        if (missingPermissions.size != 0) {
            requestPermissions(
                missingPermissions,
                PERMISSION_REQUEST
            )
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun getMissingPermissions(): Array<String?> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return arrayOfNulls(0)
        }
        val info: PackageInfo
        info = try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(CameraCapturerActivity.A.TAG, "Failed to retrieve permissions.")
            return arrayOfNulls(0)
        }
        if (info.requestedPermissions == null) {
            Log.w(CameraCapturerActivity.A.TAG, "No requested permissions.")
            return arrayOfNulls(0)
        }
        val missingPermissions = ArrayList<String?>()
        for (i in info.requestedPermissions.indices) {
            if (info.requestedPermissionsFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED == 0) {
                missingPermissions.add(info.requestedPermissions[i])
            }
        }
        Log.d(CameraCapturerActivity.A.TAG, "Missing permissions: $missingPermissions")
        return missingPermissions.toTypedArray()
    }

    override fun onClick(p0: View?) {
        when(p0?.id)
        {
            R.id.camera_capturer ->{
                startActivity(Intent(this,CameraCapturerActivity::class.java))
            }
        }
    }

}