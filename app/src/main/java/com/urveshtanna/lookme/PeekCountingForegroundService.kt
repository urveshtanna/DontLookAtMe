package com.urveshtanna.lookme

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.face.Face

class PeekCountingForegroundService : Service() {

    val NOTIFICATION_ID = 125;

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("First"));

        //do heavy work on a background thread


        //stopSelf();

        return START_NOT_STICKY;
    }

    private fun createNotification(text: String): Notification? {
        val notificationIntent = Intent(this, MainActivity::class.java);
        val pendingIntent: PendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, 0);
        return NotificationCompat.Builder(this, "test_id")
            .setContentTitle(this.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentIntent(pendingIntent)
            .build();
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text);
        val mNotificationManager: NotificationManager = this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;
        mNotificationManager.notify(NOTIFICATION_ID, notification);

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "test_id",
                "Foreground Service Channel",
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
}
