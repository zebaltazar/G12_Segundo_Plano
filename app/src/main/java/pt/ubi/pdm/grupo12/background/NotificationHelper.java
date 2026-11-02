package pt.ubi.pdm.grupo12.background;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Centraliza canais e construção de notificações.
 * minSdk = 29, logo os canais (API 26) existem sempre: não é preciso testar a versão.
 */
public final class NotificationHelper {

    private static final String TAG = "G12Notif";

    public static final String CHANNEL_RESULTS = "canal_resultados";
    public static final String CHANNEL_ALARMS = "canal_alarmes";

    public static final int ID_RESULT = 1001;
    public static final int ID_ALARM = 1002;

    public static final String ACTION_REPEAT = "pt.ubi.pdm.grupo12.background.ACTION_REPEAT";
    public static final String ACTION_ALARM = "pt.ubi.pdm.grupo12.background.ACTION_ALARM";
    public static final String EXTRA_RESULT = "extra_resultado";

    private NotificationHelper() { }

    /** Cria (ou atualiza) os canais. É idempotente: pode ser chamado várias vezes. */
    public static void createChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        NotificationChannel results = new NotificationChannel(
                CHANNEL_RESULTS,
                ctx.getString(R.string.canal_resultados_nome),
                NotificationManager.IMPORTANCE_DEFAULT);
        results.setDescription(ctx.getString(R.string.canal_resultados_desc));

        NotificationChannel alarms = new NotificationChannel(
                CHANNEL_ALARMS,
                ctx.getString(R.string.canal_alarmes_nome),
                NotificationManager.IMPORTANCE_HIGH);
        alarms.setDescription(ctx.getString(R.string.canal_alarmes_desc));

        nm.createNotificationChannel(results);
        nm.createNotificationChannel(alarms);
    }

    /** Notificação "processamento concluído": toque abre a app, ação "Repetir". */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() é verificado antes de notify()
    public static void showDone(Context ctx, String summary) {
        createChannels(ctx);
        NotificationManagerCompat nmc = NotificationManagerCompat.from(ctx);
        if (!nmc.areNotificationsEnabled()) {
            Log.w(TAG, "Notificações desativadas/negadas: nada a mostrar.");
            return;
        }

        // Toque na notificação -> abre a MainActivity com o resultado
        Intent open = new Intent(ctx, MainActivity.class);
        open.putExtra(EXTRA_RESULT, summary);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Botão "Repetir" -> difusão explícita para o AppReceiver
        Intent repeat = new Intent(ctx, AppReceiver.class).setAction(ACTION_REPEAT);
        PendingIntent repeatPi = PendingIntent.getBroadcast(
                ctx, 1, repeat,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_RESULTS)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(ctx.getString(R.string.notif_titulo_concluido))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_popup_sync,
                        ctx.getString(R.string.notif_acao_repetir), repeatPi)
                .build();

        nmc.notify(ID_RESULT, n);
    }

    /** Notificação disparada pelo alarme (canal de importância alta). */
    @SuppressLint("MissingPermission")
    public static void showAlarm(Context ctx) {
        createChannels(ctx);
        NotificationManagerCompat nmc = NotificationManagerCompat.from(ctx);
        if (!nmc.areNotificationsEnabled()) {
            Log.w(TAG, "Notificações desativadas/negadas: alarme sem aviso.");
            return;
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPi = PendingIntent.getActivity(
                ctx, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ALARMS)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(ctx.getString(R.string.notif_titulo_alarme))
                .setContentText(ctx.getString(R.string.notif_texto_alarme))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .build();

        nmc.notify(ID_ALARM, n);
    }
}
