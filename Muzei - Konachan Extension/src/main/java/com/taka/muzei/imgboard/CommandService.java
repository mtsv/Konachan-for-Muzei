package com.taka.muzei.imgboard;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class CommandService extends Service {
    private final Logger logger = new Logger(CommandService.class);

    public static final int COMMAND_DOWNLOAD = 555;
    public static final int COMMAND_DELETE = 556;

    static final int mID = 134;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String title = intent.getStringExtra("title");
        Uri uri = intent.getParcelableExtra("uri");
        Uri contentUri = intent.getParcelableExtra("content_uri");
        int command = intent.getIntExtra("command", -1);
        long artworkId = intent.getLongExtra("id", -1);
        logger.i("onStartCommand: command=" + command + "; title=" + title);

        switch (command) {
            case COMMAND_DOWNLOAD:
                downloadArtwork(title, uri);
                break;
            case COMMAND_DELETE:
                delete(contentUri, artworkId);
                break;
            default:
                break;
        }
        return Service.START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private String constructLocalFileName(String title, Uri uri) {
        return FileUtils.cleanFileName(title.substring(0, Math.min(200, title.length()))) + '.' +
                FileUtils.extractFileExtension(uri.toString());
    }

    private void delete(Uri contentUri, long artworkId) {
        final ContentResolver contentResolver = this.getContentResolver();
        final Uri artworkUri = ContentUris.withAppendedId(contentUri, artworkId);
        logger.i("Deleting artwork with URI " + artworkUri);
        contentResolver.delete(artworkUri, null, null);
    }

    private void downloadArtwork(final String title, final Uri artImageUri){
        logger.i("in downloadArtwork()");
        Utils.doWithPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, () -> {
            try {
                if (null == artImageUri) {
                    logger.w("Persistent URI for image is NULL");
                    return;
                }
                logger.i("Downloading image from URL " + artImageUri);
                final NotificationCompat.Builder builder = NotificationUtils.constructNotificationBuilder(this.getString(R.string.notification_download_title), "Download in progress", this);
                final NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.notify(mID, builder.build());
                new Thread(() -> {
                    try {
                        final Config config = new Config(this);

                        final File fDir = FileUtils.createDirOrCheckAccess(config.getImageStoreDirectory());

                        final String resultFilename = constructLocalFileName(title, artImageUri);
                        logger.i("Result file name: " + resultFilename);

                        File file;
                        if (artImageUri.toString().startsWith("file://")) {
                            file = new File(fDir, resultFilename);
                            FileUtils.copyFile(new File(artImageUri.getPath()), file);
                        } else {
                            file = download(artImageUri, fDir, resultFilename, builder, notificationManager, config.getHttpRetryCount());
                        }

                        rescanMedia(file);

                        notifyDownloadComplete(file, builder, notificationManager);
                    } catch (Throwable th) {
                        logger.e("Download failed", th);
                        NotificationUtils.notify(this.getString(R.string.notification_download_title), "Download failed: " + th.getMessage(), this, mID);
                    }
                }
                ).start();
            } catch (Throwable th) {
                logger.e("Download failed", th);
                NotificationUtils.notify(this.getString(R.string.notification_download_title), "Download failed: " + th.getMessage(), this, mID);
            }
        }, this);
    }

    private File download(Uri url, File fDir, String resultFilename,
                          NotificationCompat.Builder mBuilder,
                          NotificationManager mNotificationManager,
                          int retryCount) throws IOException {
        File file = new File(fDir, resultFilename);

        BooruHttpClient.downloadImage(url, file, percentComplete -> {
            mBuilder.setProgress(100, Math.round(percentComplete), false);
            mBuilder.setContentText(Math.round(percentComplete) + "% complete");
            mBuilder.setSound(null);
            mNotificationManager.notify(mID, mBuilder.build());
        }, retryCount);

        return file;
    }

    private void rescanMedia(File file) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intent.setData(Uri.fromFile(file));
        this.sendBroadcast(intent);
    }

    private void notifyDownloadComplete(File file, NotificationCompat.Builder builder, NotificationManager notificationManager) {
        Intent resultIntent = new Intent();
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        resultIntent.setAction(Intent.ACTION_VIEW);
        Uri imageUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file);
        resultIntent.setDataAndType(imageUri, "image/*");
        PendingIntent resultPendingIntent = PendingIntent.getActivity(this , 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(resultPendingIntent)
                .setContentText("Download complete")
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setLargeIcon(ImageUtils.decodeSampledBitmapFromFile(file, 150, 150))
                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(ImageUtils.decodeSampledBitmapFromFile(file,675, 337)));
        Notification note = builder.build();
        notificationManager.notify(mID, note);
    }





}
