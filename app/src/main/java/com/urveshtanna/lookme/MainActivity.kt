package com.urveshtanna.lookme

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face
import com.google.android.gms.vision.face.FaceDetector
import com.google.android.material.snackbar.Snackbar
import com.urveshtanna.lookme.ui.CameraSourcePreview
import com.urveshtanna.lookme.ui.GraphicOverlay


class MainActivity : AppCompatActivity() {

    private var mCameraSource: CameraSource? = null

    private var mPreview: CameraSourcePreview? = null
    private var mGraphicOverlay: GraphicOverlay? = null

    private val RC_HANDLE_GMS = 9001
    // permission request codes need to be < 256
    private val RC_HANDLE_CAMERA_PERM = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mPreview = findViewById(R.id.preview);
        mGraphicOverlay = findViewById(R.id.faceOverlay)

        val rc: Int = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
            startPeekService();
        } else {
            requestForPermission();
        }
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

        mGraphicOverlay?.rootView?.let {
            Snackbar.make(it, "Permission", Snackbar.LENGTH_LONG).setAction("Enable", {
                ActivityCompat.requestPermissions(this, permission, RC_HANDLE_CAMERA_PERM);
            })
        }


    }

    override fun onResume() {
        super.onResume()
        startCameraSource();
    }

    private fun startCameraSource() {
        val code: Int = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (code != ConnectionResult.SUCCESS) {
            val dialog =
                GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dialog.show();
        }

        if (mCameraSource != null) {
            try {
                mPreview?.start(mCameraSource, mGraphicOverlay);
            } catch (e: Exception) {
                Log.e(this.localClassName, "Error ${e}");
                mCameraSource?.release();
                mCameraSource = null;
            }
        }
    }

    /*override fun onPause() {
        super.onPause()
        mCameraSource?.release();
        mCameraSource = null;
    }*/

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
            createCameraSource()
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

    private fun createCameraSource() {
        val context = applicationContext
        val detector = FaceDetector.Builder(context)
            .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
            .build()

        detector.setProcessor(MultiProcessor.Builder<Face>(GraphicFaceTrackerFactory()).build())

        if (!detector.isOperational) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(this.localClassName, "Face detector dependencies are not yet available.")
        }

        mCameraSource = CameraSource.Builder(context, detector)
            .setRequestedPreviewSize(640, 480)
            .setFacing(CameraSource.CAMERA_FACING_FRONT)
            .setRequestedFps(30.0f)
            .build()
    }


    //==============================================================================================
    // Graphic Face Tracker
    //==============================================================================================

    /**
     * Factory for creating a face tracker to be associated with a new face.  The multiprocessor
     * uses this factory to create face trackers as needed -- one for each individual.
     */
    private inner class GraphicFaceTrackerFactory : MultiProcessor.Factory<Face> {
        override fun create(face: Face): Tracker<Face> {
            return GraphicFaceTracker()
        }
    }

    /**
     * Face tracker for each detected individual. This maintains a face graphic within the app's
     * associated face overlay.
     */
    private inner class GraphicFaceTracker internal constructor() : Tracker<Face>() {

        /**
         * Start tracking the detected face instance within the face overlay.
         */
        override fun onNewItem(faceId: Int, face: Face?) {
            Log.e(
                "GraphicFaceTracker",
                "onNewItem  ${face?.id} face Left eye: ${face?.isLeftEyeOpenProbability} Right eye: ${face?.isRightEyeOpenProbability} Happiness ${face?.isSmilingProbability} "
            )
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        override fun onUpdate(detectionResults: Detector.Detections<Face>?, face: Face?) {
            Log.e(
                "GraphicFaceTracker",
                "onUpdate ${face?.id} face Left eye: ${face?.isLeftEyeOpenProbability} Right eye: ${face?.isRightEyeOpenProbability} Happiness ${face?.isSmilingProbability} "
            )
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        override fun onMissing(detectionResults: Detector.Detections<Face>?) {
            Log.e("GraphicFaceTracker", "onMissing")
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        override fun onDone() {
            Log.e("GraphicFaceTracker", "onDone")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
