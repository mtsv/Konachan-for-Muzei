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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;


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

    private File download(Uri url, File fDir, String resultFilename,
                          NotificationCompat.Builder mBuilder,
                          NotificationManager mNotificationManager,
                          int retryCount) throws IOException {
        File file = new File(fDir, resultFilename);

        BooruHttpClient.download(url, file, percentComplete -> {
            mBuilder.setProgress(100, Math.round(percentComplete), false);
            mBuilder.setContentText(Integer.toString((int) Math.round(percentComplete)) + "% complete");
            mNotificationManager.notify(mID, mBuilder.build());
        }, retryCount);

        return file;
    }

    private String constructLocalFileName(Artwork dlArt) {
        return Utils.cleanFileName(dlArt.getTitle().substring(0, Math.min(200, dlArt.getTitle().length()))) + '.' +
                Utils.extractFileExtension(dlArt.getImageUri().toString());
    }

    public void downloadArtwork(final Artwork dlArt){
        try {
            logger.i("Downloading image from URL " + dlArt.getImageUri());
            final NotificationCompat.Builder mBuilder = constructNotificationBuilder(DOWNLOAD_TITLE,"Download in progress");
            final NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            new Thread(() -> {
                    try {
                        final Config config = new Config(this);

                        File fDir = Utils.createDirOrCheckAccess(config.getImageStoreDirectory());

                        final String resultFilename = constructLocalFileName(dlArt);
                        logger.i("Result file name: " + resultFilename);

                        File file;
                        if(dlArt.getImageUri().toString().startsWith("file://")) {
                            file = new File(fDir, resultFilename);
                            Utils.copyFile(new File(dlArt.getImageUri().getPath()), file);
                        } else {
                            file = download(dlArt.getImageUri(), fDir, resultFilename, mBuilder, mNotificationManager, config.getHttpRetryCount());
                        }

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
        if(new Config(this).showUserInfo()) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
                toast.show();
            });
        }
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

    private boolean isPostValid(Post post, int postIdx) {
        if(!post.isValid()) {
            logger.d("Post #" + postIdx +" is not valid: " + post);
            return false;
        }

        if(!post.isExtensionValid()) {
            logger.d( "Post #" + postIdx +": wrong image extension: " + Utils.extractFileExtension(post.getDirectImageUrl().toString()));
            return false;
        }

        if(post.getFileSize() > MAX_FILE_SIZE) {
            logger.d( "Post #" + postIdx +": image size " + post.getFileSize() + " exceeds limit of  " + MAX_FILE_SIZE + "b");
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
            logger.i("Requesting posts, page #" + (pageCounter + 1));
            List<Post> response;
            try {
                response = booruHttpClient.getPosts(booru, config.getTags(), config.getSortType(),
                        pageCounter, config.getPostLimit(), config.getRestrictContentFlag(), 3);
            } catch (Throwable th) {
                logger.e("Failed to get posts", th);
                throw new IOException("Posts retrieval failed: " + th.getMessage(), th);
            }

            logger.i("Response size: " + response.size());

            if (response.isEmpty()) {
                break;
            }

            for(int i = 0; i < response.size() && post == null; ++i) {
                ++imageCounter;
                Post post_i = response.get(i);
                logger.d("Post #" + Integer.toString(i+1) + "/" + imageCounter + "; post id: " + post_i.getId());

                if(!isPostValid(post_i, i+1)) {
                    continue;
                }

                ++validImageCounter;

                if(null == firstPost)
                    firstPost = post_i;

                final String hash = post_i.getHash();
                hashes.add(hash);
                if(!Database.hasHash(db, hash)) {
                    logger.i("Post #" + Integer.toString(i+1) + "/" + imageCounter + " not stored in DB");
                    post = post_i;
                }
            }

            if(null == post)
                logger.i("All posts on page #" + (pageCounter + 1) + " are used, trying next page");

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

                applyPost(config, post, booruHttpClient);
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

    private boolean tryApplyLocal(Config config, Post post, BooruHttpClient booruHttpClient) {
        final String wallpapersDir = config.getWallpapersDirectory();
        try {
            Utils.createDirOrCheckAccess(wallpapersDir);
        } catch (IOException e) {
            logger.e("Local directory " + wallpapersDir + " access error", e);
            return false;
        }

        logger.i("Cleaning up old files in dir " + wallpapersDir);
        try {
            final SortedMap<Long, String> prevImages = new TreeMap<>();
            Utils.listFiles(new File(wallpapersDir), file ->{
                if(!file.isFile())
                    return;

                final String extension = Utils.extractFileExtension(file.getName());
                if(!Post.allowedExtensions.contains(extension))
                    return;

                final long lastModified = file.lastModified();
                prevImages.put(lastModified, file.getAbsolutePath());
            });

            if(!prevImages.isEmpty()) {
                final long newestLastModified = prevImages.lastKey();
                final String prevWallpaperFile = prevImages.get(newestLastModified);
                logger.i("Previous wallpaper file is " + prevWallpaperFile);
                for(String file : prevImages.values()) {
                    if(!file.equals(prevWallpaperFile)) {
                        logger.i("Deleting old file " + file);
                        new File(file).delete();
                    }
                }
            }
        } catch (IOException e) {
            logger.e("Cleaning up old files fail", e);
        }

        final String newWallpaperFilePath = wallpapersDir + "/" +
                "wallpaper_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" + new Random().nextInt(1000) + "." + post.getImageExtension();

        try {
            logger.i("Loading image from " + post.getDirectImageUrl() + " to file " + newWallpaperFilePath);
            booruHttpClient.download(post, new File(newWallpaperFilePath), percentComplete -> {
                logger.v("Downloaded " + percentComplete + "%");
            }, config.getHttpRetryCount());
            logger.i("Image loaded successfully to file " + newWallpaperFilePath);
        } catch (IOException e) {
            logger.e("Failed to download image to file " + newWallpaperFilePath, e);
            return false;
        }

        publish(post, Uri.fromFile(new File(newWallpaperFilePath)), booruHttpClient);

        return true;
    }

    private void applyPost(Config config, Post post, BooruHttpClient booruHttpClient) {
        logger.i("Selected post: " + post.toString());

        if(config.useLocalWallpaper()) {
            logger.i("Trying to load file locally and provide Muzei 'file://...' URL");
            if(tryApplyLocal(config, post, booruHttpClient)) {
               return;
            }
            logger.i("Falling back to applying wallpaper by Web URL");
        }

        final Uri resultImageUrl = booruHttpClient.proxify(post.getDirectImageUrl());

        publish(post, resultImageUrl, booruHttpClient);
    }

    private void publish(Post post, Uri imageUrl, BooruHttpClient booruHttpClient) {
        final Uri postUrl = booruHttpClient.proxify(post.getPostUrl());
        logger.i("Publishing post " + post + "; image URL: " + imageUrl);
        publishArtwork(new Artwork.Builder()
                .title(post.getTags())
                .byline(post.getAuthor())
                .imageUri(imageUrl)
                .token(Integer.toString(post.getId()))
                .viewIntent(new Intent(Intent.ACTION_VIEW, postUrl))
                .build());
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
