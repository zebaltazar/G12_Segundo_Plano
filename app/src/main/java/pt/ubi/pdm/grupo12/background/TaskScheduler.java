package pt.ubi.pdm.grupo12.background;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Todos os pedidos de trabalho/alarmes num só sítio (usado pela Activity e pelo Receiver). */
public final class TaskScheduler {

    public static final String TAG_WORK = "g12_processamento";
    public static final String TAG_TYPE_PREFIX = "tipo:";
    public static final String UNIQUE_PERIODIC = "g12_periodico";
    private static final int ALARM_REQUEST_CODE = 2001;
    private static final int MAX_TEXT = 2000; // Data (WorkManager) tem limite de 10 KB

    private TaskScheduler() { }

    private static Data input(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() > MAX_TEXT) t = t.substring(0, MAX_TEXT);
        return new Data.Builder().putString(TextProcessWorker.KEY_TEXT, t).build();
    }

    /** 1) Trabalho único, sem restrições: corre assim que possível. */
    public static void enqueueNow(Context ctx, String text) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(TextProcessWorker.class)
                .setInputData(input(text))
                .addTag(TAG_WORK)
                .addTag(TAG_TYPE_PREFIX + "imediato")
                .build();
        WorkManager.getInstance(ctx).enqueue(req);
    }

    /** 2) Trabalho único com atraso inicial, restrições e política de backoff. */
    public static void enqueueConstrained(Context ctx, String text, boolean requiresCharging) {
        Constraints.Builder cb = new Constraints.Builder().setRequiresBatteryNotLow(true);
        if (requiresCharging) cb.setRequiresCharging(true);

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(TextProcessWorker.class)
                .setInputData(input(text))
                .setConstraints(cb.build())
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .addTag(TAG_WORK)
                .addTag(TAG_TYPE_PREFIX + "condicionado")
                .build();
        WorkManager.getInstance(ctx).enqueue(req);
    }

    /** 3) Trabalho periódico único (mínimo permitido: 15 minutos). */
    public static void startPeriodic(Context ctx, String text) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                TextProcessWorker.class, 15, TimeUnit.MINUTES)
                .setInputData(input(text))
                .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .addTag(TAG_WORK)
                .addTag(TAG_TYPE_PREFIX + "periodico")
                .build();
        // KEEP: se já existir, não duplica
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    /** 4) Alarme inexato que acorda o dispositivo (não precisa de permissão especial). */
    public static void scheduleAlarm(Context ctx, long delayMs) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        Intent i = new Intent(ctx, AppReceiver.class).setAction(NotificationHelper.ACTION_ALARM);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, ALARM_REQUEST_CODE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMs, pi);
    }

    public static void cancelAll(Context ctx) {
        WorkManager.getInstance(ctx).cancelAllWorkByTag(TAG_WORK);
    }
}
