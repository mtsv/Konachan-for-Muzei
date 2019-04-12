package com.taka.muzei.imgboard;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.widget.Toast;

import com.google.android.apps.muzei.api.Artwork;
import com.google.android.apps.muzei.api.RemoteMuzeiArtSource;
import com.google.android.apps.muzei.api.UserCommand;
import com.taka.muzei.imgboard.booru.BaseBooru;
import com.taka.muzei.imgboard.posts.Post;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class BooruArtSource extends RemoteMuzeiArtSource {
    private final Logger logger = new Logger(BooruArtSource.class);
    private static final String DOWNLOAD_TITLE = "Muzei - Booru: Downloading Wallpaper";
    private static final String SOURCE_NAME = "Booru";
    private static final Integer DOWNLOAD_COMMAND = 555;

    static final int mID = 134;
    static final int MAX_FILE_SIZE = 1024*1024*10;

    public BooruArtSource() {
        super(SOURCE_NAME);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        GlobalApplication.setUpLogging();

        List<UserCommand> cmList = new ArrayList<UserCommand>();
        cmList.add(new UserCommand(BUILTIN_COMMAND_ID_NEXT_ARTWORK));
        cmList.add(new UserCommand(DOWNLOAD_COMMAND, "Download"));
        setUserCommands(cmList);
    }

    @Override
    public void onCustomCommand(int i){
        if(i == DOWNLOAD_COMMAND) {
            downloadArtwork(getCurrentArtwork());
        }
    }

    private NotificationCompat.Builder constructNotificationBuilder(String title, String text) {
        return new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text);
    }

    private void notify(String title, String text){
        NotificationCompat.Builder builder = constructNotificationBuilder(title, text);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(mID, builder.build());
    }

    private File checkDownloadDir(String fPath) throws IOException {
        logger.i( "Save to dir: " + fPath);
        File fDir = new File(fPath);
        if (!fDir.exists()){
            logger.i("Creating dir");
            if(!fDir.mkdir()) {
                logger.e("Can not create dir " + fPath);
                throw new IOException("Failed to create local dir");
            }
        }
        if(!fDir.canWrite()) {
            logger.e("Can not write to dir " + fPath);
            throw new IOException("Can not write to local dir " + fPath + ". Check permissions");
        }

        return fDir;
    }

    private File download(final Artwork dlArt, File fDir,
                          final NotificationCompat.Builder mBuilder,
                          final NotificationManager mNotificationManager) throws IOException {
        final String resultFilename =
                Utils.cleanFileName(dlArt.getTitle().substring(0, Math.min(200, dlArt.getTitle().length()))) + '.' +
                        Utils.extractFileExtension(dlArt.getImageUri().toString());
        logger.i("Result file name: " + resultFilename);
        File file = new File(fDir, resultFilename);

        BooruHttpClient.download(dlArt.getImageUri(), file, percentComplete -> {
            mBuilder.setProgress(100, Math.round(percentComplete), false);
            mBuilder.setContentText(Integer.toString((int) Math.round(percentComplete)) + "% complete");
            mNotificationManager.notify(mID, mBuilder.build());
        });

        return file;
    }

    public void downloadArtwork(final Artwork dlArt){
        try {
            logger.i("Downloading image from URL " + dlArt.getImageUri());
            final NotificationCompat.Builder mBuilder = constructNotificationBuilder(DOWNLOAD_TITLE,"Download in progress");
            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            new Thread(() -> {
                    try {
                        final Config config = new Config(this);

                        File fDir = checkDownloadDir(config.getImageStoreDirectory());

                        File file = download(dlArt, fDir, mBuilder, mNotificationManager);

                        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        intent.setData(Uri.fromFile(file));
                        sendBroadcast(intent);

                        Intent resultIntent = new Intent();
                        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        resultIntent.setAction(Intent.ACTION_VIEW);
                        //Uri imageUri = Uri.fromFile(file);
                        Uri imageUri = FileProvider.getUriForFile(getApplicationContext(), BuildConfig.APPLICATION_ID + ".provider", file);
                        resultIntent.setDataAndType(imageUri, "image/*");
                        PendingIntent resultPendingIntent = PendingIntent.getActivity(getApplicationContext() , 0, resultIntent,PendingIntent.FLAG_UPDATE_CURRENT);
                        mBuilder.setContentIntent(resultPendingIntent)
                                .setContentText("Download complete")
                                .setProgress(0, 0, false)
                                .setLargeIcon(decodeSampledBitmapFromFile(file, 150, 150))
                                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(decodeSampledBitmapFromFile(file,675, 337)));
                        Notification note = mBuilder.build();
                        note.flags = Notification.FLAG_AUTO_CANCEL;
                        mNotificationManager.notify(mID, note);
                    } catch(Throwable th) {
                         logger.e("Download failed", th);
                         notify(DOWNLOAD_TITLE, "Download failed: " + th.getMessage());
                    }
                }
            ).start();
        } catch (Throwable th) {
            logger.e("Download failed", th);
            notify(DOWNLOAD_TITLE, "Download failed: " + th.getMessage());
        }
    }

    private void showToast(final String text) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    private void checkConnection(Config config) {
        if(!NetworkUtils.isConnected(getApplicationContext())) {
            logger.w( "Not connected to Internet");
            throw new RuntimeException("No internet connection");
        }

        if (config.prettyflyforaWifi().equals(true) && !NetworkUtils.isWifiConnected(getApplicationContext())) {
            logger.w( "Not connected to WiFi");
            throw new RuntimeException("WiFi is not connected");
        }
    }

    private boolean isPostValid(Post post) {
        if(!post.isValid()) {
            logger.e("invalid post: " + post);
            return false;
        }

        if(!post.isExtensionValid()) {
            logger.w( "wrong image extension: " + post);
            return false;
        }

        if(post.getFileSize() > MAX_FILE_SIZE) {
            logger.w( "image size " + post.getFileSize() + " exceeds limit of  " + MAX_FILE_SIZE + "b");
            return false;
        }

        return true;
    }

    @NonNull
    Post selectNewPost(BooruHttpClient booruHttpClient, BaseBooru booru, Config config, SQLiteDatabase db, Boolean showInfo) throws IOException {
        Post post = null;
        Post firstPost = null;
        int pageCounter = 0;
        int imageCounter = 0;
        int validImageCounter = 0;
        final Set<String> hashes = new HashSet<>();

        while (null == post) {
            logger.i("Requesting popular posts, page #" + (pageCounter + 1));
            List<Post> response;
            try {
                response = booruHttpClient.getPopularPosts(booru, config.getTags(), config.getSortType(),
                        pageCounter, config.getPostLimit(), config.getRestrictContentFlag(), 3);
            } catch (Throwable th) {
                logger.e("Failed to get popular posts", th);
                throw new IOException("Popular posts retrieval failed: " + th.getMessage(), th);
            }

            logger.i("Response size: " + response.size());

            if (response.isEmpty()) {
                break;
            }

            for(int i = 0; i < response.size() && post == null; ++i) {
                ++imageCounter;
                Post post_i = response.get(i);
                logger.i("response #" + Integer.toString(i) + "; post id: " + post_i.getId());

                if(!isPostValid(post_i)) {
                    continue;
                }

                ++validImageCounter;

                if(null == firstPost)
                    firstPost = post_i;

                final String hash = post_i.getHash();
                hashes.add(hash);
                if(!Database.hasHash(db, hash)) {
                    logger.d("This image is not stored in DB");
                    post = post_i;
                }
            }

            ++pageCounter;
        }

        final boolean allRotated = null == post && null != firstPost;
        if(allRotated) { //all posts with these tags rotated
            validImageCounter = 1;
            post = firstPost;
            Database.removeHashes(db, hashes);
        }

        if(null == post) {
            throw new RuntimeException(imageCounter > 0 ? "No valid posts returned for selected tags" : "No posts returned for selected tags");
        }

        if(showInfo)
            showToast("Image #" + validImageCounter + " selected" + (allRotated ? ". All Posts with this tag rotated" : ""));

        Database.addHash(db, post.getHash());

        return post;
    }

    @Override
    protected void onTryUpdate(int reason) throws RetryException {
        logger.i("onTryUpdate() called, reason: " + reason);
        final boolean showInfo = reason == UPDATE_REASON_USER_NEXT;
        final Config config = new Config(this);
        try {
            checkConnection(config);

            final BaseBooru booru = BaseBooru.construct(config);

            if (showInfo)
                showToast("Next image requested from " + booru.name() + ". Tags: " + config.getTags());

            final BooruHttpClient booruHttpClient = new BooruHttpClient(booru.getBaseUrl(), config.proxyUrl());

            try (Database.DatabaseHelper dbHelper = new Database.DatabaseHelper(this);
                 SQLiteDatabase db = dbHelper.getWritableDatabase()) {

                Database.removeStaleMD5(db, config.getMD5ClearMillis());

                final Post post = selectNewPost(booruHttpClient, booru, config, db, showInfo);

                final String resultImageUrl = booruHttpClient.proxify(post.getDirectImageUrl()).toString();
                final String postUrl = booruHttpClient.proxify(post.getPostUrl()).toString();

                logger.i("Selected post: " + post.toString());
                logger.i("Image URL: " + resultImageUrl);
                logger.i("Post URL: " + postUrl);

                publishArtwork(new Artwork.Builder()
                        .title(post.getTags())
                        .byline(post.getAuthor())
                        .imageUri(Uri.parse(resultImageUrl))
                        .token(Integer.toString(post.getId()))
                        .viewIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(postUrl)))
                        .build());
            }

            scheduleUpdate(System.currentTimeMillis() + config.getRotateTimeMillis());

            config.setLastLoadStatus(true);
        } catch (Throwable th) {
            logger.e("onTryUpdate() failed", th);
            if (showInfo)
                showToast("Failed to get new wallpaper: " + th.getMessage());
            logger.i("Throwing RetryException to schedule retry");

            config.setLastLoadStatus(false);

            throw new RetryException(th);
        }
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
