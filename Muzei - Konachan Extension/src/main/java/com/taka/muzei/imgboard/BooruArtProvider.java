package com.taka.muzei.imgboard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;
import com.intentfilter.androidpermissions.PermissionManager;
import com.intentfilter.androidpermissions.models.DeniedPermissions;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import static java.util.Collections.singleton;


public class BooruArtProvider extends MuzeiArtProvider {
    private final Logger logger = new Logger(BooruArtProvider.class);
    private static final String DOWNLOAD_TITLE = "Muzei - Booru: Downloading Wallpaper";
    private static final Integer DOWNLOAD_COMMAND = 555;

    static final int mID = 134;

    @Override
    protected void onLoadRequested(boolean initial) {
        logger.i("Muzei requested new artwork");
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_ROAMING).build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(BooruLoadWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance().enqueue(workRequest);
    }

    @Override
    public boolean onCreate() {
        super.onCreate();

        MuzeiBooruApplication.setUpLogging();
        return true;
    }

    @Override
    @NonNull
    protected List<UserCommand> getCommands(@NonNull final Artwork artwork) {
        logger.i("getCommands called");
        List<UserCommand> result = new ArrayList<>();
        result.add(new UserCommand(DOWNLOAD_COMMAND, "Download"));
        return result;
    }

    @Override
    protected void onCommand(@NonNull final Artwork artwork, int id) {
        if(id == DOWNLOAD_COMMAND) {
            downloadArtwork(artwork);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            logger.i("Setting up notification channel");
            final String name = getContext().getString(R.string.notification_channel);
            final String description = getContext().getString(R.string.notification_channel_description);
            final String channelId = getContext().getString(R.string.notification_channel_id);

            NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            channel.enableVibration(false);
            channel.enableLights(false);
            final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

            notificationManager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder constructNotificationBuilder(String title, String text) {
        createNotificationChannel();
        return new NotificationCompat.Builder(getContext(), getContext().getString(R.string.notification_channel_id))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true);
    }

    private void notify(String title, String text){
        NotificationCompat.Builder builder = constructNotificationBuilder(title, text);
        NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(mID, builder.build());
    }

    private File download(Uri url, File fDir, String resultFilename,
                          NotificationCompat.Builder mBuilder,
                          NotificationManager mNotificationManager,
                          int retryCount) throws IOException {
        File file = new File(fDir, resultFilename);

        BooruHttpClient.downloadImage(url, file, percentComplete -> {
            mBuilder.setProgress(100, Math.round(percentComplete), false);
            mBuilder.setContentText(Integer.toString((int) Math.round(percentComplete)) + "% complete");
            mBuilder.setSound(null);
            mNotificationManager.notify(mID, mBuilder.build());
        }, retryCount);

        return file;
    }

    private String constructLocalFileName(Artwork dlArt) {
        return Utils.cleanFileName(dlArt.getTitle().substring(0, Math.min(200, dlArt.getTitle().length()))) + '.' +
                Utils.extractFileExtension(dlArt.getPersistentUri().toString());
    }

    private void rescanMedia(File file) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        getContext().sendBroadcast(intent);
    }

    private void notifyDownloadComplete(File file, NotificationCompat.Builder builder, NotificationManager notificationManager) {
        Intent resultIntent = new Intent();
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        resultIntent.setAction(Intent.ACTION_VIEW);
        Uri imageUri = FileProvider.getUriForFile(getContext(), BuildConfig.APPLICATION_ID + ".provider", file);
        resultIntent.setDataAndType(imageUri, "image/*");
        PendingIntent resultPendingIntent = PendingIntent.getActivity(getContext() , 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent)
                .setContentText("Download complete")
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setLargeIcon(decodeSampledBitmapFromFile(file, 150, 150))
                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(decodeSampledBitmapFromFile(file,675, 337)));
        Notification note = builder.build();
        notificationManager.notify(mID, note);
    }

    private void doWithPermission(String permission, Runnable callback) {
        PermissionManager permissionManager = PermissionManager.getInstance(getContext());
        permissionManager.checkPermissions(singleton(permission), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {
                logger.i("Permissions granted: " + permission);
                callback.run();
            }

            @Override
            public void onPermissionDenied(DeniedPermissions deniedPermissions) {
                logger.w("Permissions denied: " + permission);
            }
        });
    }

    public void downloadArtwork(final Artwork dlArt){
        doWithPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, () -> {
            try {
                final Uri artImageUri = dlArt.getPersistentUri();
                if (null == artImageUri) {
                    logger.w("Persistent URI for image is NULL");
                    return;
                }
                logger.i("Downloading image from URL " + artImageUri);
                final NotificationCompat.Builder builder = constructNotificationBuilder(DOWNLOAD_TITLE, "Download in progress");
                final NotificationManager notificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(mID, builder.build());
                new Thread(() -> {
                    try {
                        final Config config = new Config(getContext());

                        final File fDir = Utils.createDirOrCheckAccess(config.getImageStoreDirectory());

                        final String resultFilename = constructLocalFileName(dlArt);
                        logger.i("Result file name: " + resultFilename);

                        File file;
                        if (artImageUri.toString().startsWith("file://")) {
                            file = new File(fDir, resultFilename);
                            Utils.copyFile(new File(artImageUri.getPath()), file);
                        } else {
                            file = download(artImageUri, fDir, resultFilename, builder, notificationManager, config.getHttpRetryCount());
                        }

                        rescanMedia(file);

                        notifyDownloadComplete(file, builder, notificationManager);
                    } catch (Throwable th) {
                        logger.e("Download failed", th);
                        notify(DOWNLOAD_TITLE, "Download failed: " + th.getMessage());
                    }
                }
                ).start();
            } catch (Throwable th) {
                logger.e("Download failed", th);
                notify(DOWNLOAD_TITLE, "Download failed: " + th.getMessage());
            }
        });
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromFile(File file, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }
}
