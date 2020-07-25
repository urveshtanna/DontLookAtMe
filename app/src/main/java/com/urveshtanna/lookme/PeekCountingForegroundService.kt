package com.urveshtanna.lookme

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face
import com.google.android.gms.vision.face.FaceDetector


class PeekCountingForegroundService : Service() {

    private val NOTIFICATION_ID = 125;
    private var mCameraSource: CameraSource? = null
    private val TAG = "ForegroundService";
    private val faceMap = mutableMapOf<Int, Face>();
    private val CHANNEL_ID = "test_id";
    private val handler: Handler = Handler(Looper.getMainLooper());
    private var isHandlerRunning: Boolean = false;

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createCameraSource();
        createNotificationChannel();
        startCameraSource();
        startForeground(NOTIFICATION_ID, createNotification("People: 0"));

        //do heavy work on a background thread


        //stopSelf();

        return START_NOT_STICKY;
    }

    private fun createNotification(
        text: String,
        icon: Int = R.drawable.ic_visibility_off
    ): Notification? {
        val notificationIntent = Intent(this, MainActivity::class.java);
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, 0);

        val stopSelf = Intent(this, PeekCountingForegroundService::class.java);
        val pStopSelf =
            PendingIntent.getService(this, 0, stopSelf, PendingIntent.FLAG_CANCEL_CURRENT)


        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(text)
            .setContentText("People looking at you phone (0.5/1 eye probability)")
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build();
    }

    override fun onDestroy() {
        stopCameraSource()
        cancelNotification();
        super.onDestroy()
    }

    private fun cancelNotification() {
        val mNotificationManager: NotificationManager =
            this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

    private fun updateNotification(text: String, icon: Int = R.drawable.ic_visibility_off) {
        val notification = createNotification(text, icon);
        val mNotificationManager: NotificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Background Service",
                NotificationManager.IMPORTANCE_DEFAULT
            );

            val manager = getSystemService(NotificationManager::class.java);
            manager?.createNotificationChannel(serviceChannel);
        }
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
            if (face != null) {
                faceMap.put(face.id, face);
            }
            Log.e(TAG, "onNewItem() People: ${faceMap.size}")
            updateNotification(
                "People: ${faceMap.size}",
                if (faceMap.size > 0) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            );
        }

        /**
         * Update the position/characteristics of the face within the overlay.
         */
        override fun onUpdate(detectionResults: Detector.Detections<Face>?, face: Face?) {
            Log.e(TAG, "onUpdate "+detectionResults?.detectedItems?.size())
            if (face != null) {
                faceMap.put(face.id, face);
            }
            if (!isHandlerRunning) {
                isHandlerRunning = true;
                checkFaceAndUpdate();
            }
        }

        /**
         * Hide the graphic when the corresponding face was not detected.  This can happen for
         * intermediate frames temporarily (e.g., if the face was momentarily blocked from
         * view).
         */
        override fun onMissing(detectionResults: Detector.Detections<Face>?) {
            Log.e(TAG, "onMissing() People: ${faceMap.size}")
        }

        /**
         * Called when the face is assumed to be gone for good. Remove the graphic annotation from
         * the overlay.
         */
        override fun onDone() {
            faceMap.clear();
            Log.e(TAG, "onDone() People: ${faceMap.size}")
            updateNotification(
                "People: ${faceMap.size}",
                if (faceMap.size > 0) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            );
        }
    }

    private fun checkFaceAndUpdate() {
        handler.postDelayed({
            var peekingUsers = 0;
            for ((id, face) in faceMap) {
                if (face.isLeftEyeOpenProbability > 0.5) {
                    peekingUsers++;
                } else if (face.isRightEyeOpenProbability > 0.5) {
                    peekingUsers++;
                }
            }
            Log.e(TAG, "onUpdate() People: ${peekingUsers}")
            updateNotification(
                "People: ${peekingUsers}",
                if (peekingUsers > 0) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            );
            isHandlerRunning = false;
        }, 1800);
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
            Log.e(TAG, "Face detector dependencies are not yet available.")
        }

        mCameraSource = CameraSource.Builder(context, detector)
            .setRequestedPreviewSize(640, 480)
            .setFacing(CameraSource.CAMERA_FACING_FRONT)
            .setRequestedFps(30.0f)
            .build()
    }

    private fun startCameraSource() {
        try {
            mCameraSource?.start();
        } catch (e: Exception) {
            Log.e(TAG, "Error ${e}");
            stopCameraSource()
        }
    }

    private fun stopCameraSource() {
        mCameraSource?.stop()
        mCameraSource?.release()
        mCameraSource = null
    }
}
