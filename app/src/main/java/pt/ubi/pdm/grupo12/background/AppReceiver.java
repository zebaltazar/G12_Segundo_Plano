package pt.ubi.pdm.grupo12.background;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

/**
 * Recetor de difusão. O onReceive() corre na thread principal e tem poucos segundos:
 * por isso apenas delega (WorkManager) ou mostra uma notificação.
 */
public class AppReceiver extends BroadcastReceiver {

    private static final String TAG = "G12Receiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.i(TAG, "onReceive: " + action);

        if (NotificationHelper.ACTION_REPEAT.equals(action)) {
            NotificationManagerCompat.from(context).cancel(NotificationHelper.ID_RESULT);
            TaskScheduler.enqueueNow(context, null);           // volta a processar o texto de exemplo
        } else if (NotificationHelper.ACTION_ALARM.equals(action)) {
            NotificationHelper.showAlarm(context);
        }
    }
}
