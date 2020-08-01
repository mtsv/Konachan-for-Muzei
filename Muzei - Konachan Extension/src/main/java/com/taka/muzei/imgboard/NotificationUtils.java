package com.taka.muzei.imgboard;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtils {
    private static final Logger logger = new Logger(NotificationUtils.class);

    public static NotificationCompat.Builder constructNotificationBuilder(String title, String text, Context context) {
        createNotificationChannel(context);
        return new NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true);
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            logger.i("Setting up notification channel");
            final String name = context.getString(R.string.notification_channel);
            final String description = context.getString(R.string.notification_channel_description);
            final String channelId = context.getString(R.string.notification_channel_id);

            NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            channel.enableVibration(false);
            channel.enableLights(false);
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void notify(String title, String text, Context context, int id){
        NotificationCompat.Builder builder = constructNotificationBuilder(title, text, context);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(id, builder.build());
    }

}
