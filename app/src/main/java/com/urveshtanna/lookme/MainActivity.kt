package com.urveshtanna.lookme

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.material.snackbar.Snackbar
import androidx.core.app.ActivityCompat.startActivityForResult
import android.R.attr.scheme
import androidx.core.app.ComponentActivity.ExtraData
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.net.Uri




class MainActivity : AppCompatActivity() {

    private val RC_HANDLE_GMS = 9001
    // permission request codes need to be < 256
    private val RC_HANDLE_CAMERA_PERM = 2;
    var btnStart: Button? = null;
    var btnStop: Button? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnStart = findViewById(R.id.btn_start);
        btnStop = findViewById(R.id.btn_stop);

        btnStart?.setOnClickListener({
            val rc: Int = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
            if (rc == PackageManager.PERMISSION_GRANTED) {
                startPeekService();
            } else {
                requestForPermission();
            }
        })

        btnStop?.setOnClickListener({
            stopService();
        })
    }

    private fun startPeekService() {
        val intent = Intent(this, PeekCountingForegroundService::class.java);
        ContextCompat.startForegroundService(this, intent);
    }

    private fun stopService() {
        val intent = Intent(this, PeekCountingForegroundService::class.java);
        stopService(intent);
    }

    private fun requestForPermission() {
        val permission: Array<String> = arrayOf(Manifest.permission.CAMERA);
        if (!ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            )
        ) {
            ActivityCompat.requestPermissions(this, permission, RC_HANDLE_CAMERA_PERM);
        }

    }

    override fun onResume() {
        super.onResume()
        checkGooglePlayservice();
    }

    private fun checkGooglePlayservice() {
        val code: Int = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (code != ConnectionResult.SUCCESS) {
            val dialog =
                GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dialog.show();
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(this.localClassName, "Got unexpected permission result: $requestCode")
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (grantResults.size != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(localClassName, "Camera permission granted - initialize the camera source")
            // we have permission, so create the camerasource
            startPeekService();
            return
        }

        Log.e(
            localClassName, "Permission not granted: results len = " + grantResults.size +
                    " Result code = " + if (grantResults.size > 0) grantResults[0] else "(empty)"
        )

        val listener = DialogInterface.OnClickListener { dialog, id -> finish() }

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Face Tracker sample")
            .setMessage("No Permission")
            .setPositiveButton("Ok", listener)
            .show()
    }
}
