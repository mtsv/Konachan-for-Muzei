package com.taka.muzei.imgboard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.ProviderContract;
import com.taka.muzei.imgboard.booru.BaseBooru;
import com.taka.muzei.imgboard.posts.Post;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class BooruLoadWorker extends Worker {
    private final Logger logger = new Logger(BooruLoadWorker.class);

    static final int MAX_FILE_SIZE = 1024*1024*10;

    public BooruLoadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        logger.i("Loading new artwork");
        final Config config = new Config(getApplicationContext());
        final boolean showInfo = config.showUserInfo();

        try {
            checkConnection(config);

            final BaseBooru booru = BaseBooru.construct(config);

            if (showInfo)
                Utils.showToast("Next image requested from " + booru.name() + ". Tags: " + config.getTags());

            final BooruHttpClient booruHttpClient = new BooruHttpClient(booru.getBaseUrl(), config.proxyUrl());

            try (Database.DatabaseHelper dbHelper = new Database.DatabaseHelper(getApplicationContext());
                 SQLiteDatabase db = dbHelper.getWritableDatabase()) {

                Database.removeStaleMD5(db, config.getMD5ClearMillis());

                final Post post = selectNewPost(booruHttpClient, booru, config, db, showInfo);
                if(null == post)
                    return Result.failure();

                final boolean success = applyPost(config, post, booruHttpClient);

                if(showInfo)
                    Utils.showToast(success ? "New artwork added" : "Failed to add new artwork");

                config.setLastLoadStatus(success);
            }

            return Result.success();
        } catch (Throwable th) {
            logger.e("doWork() failed", th);
            if (showInfo)
                Utils.showToast("Failed to get new artwork: " + th.getMessage());

            config.setLastLoadStatus(false);

            return Result.retry();
        }
    }

    private boolean applyPost(Config config, Post post, BooruHttpClient booruHttpClient) {
        logger.i("Selected post: " + post.toString());

        if(config.useLocalWallpaper()) {
            logger.i("Trying to load file locally and provide Muzei 'file://...' URL");
            if(tryApplyLocal(config, post, booruHttpClient)) {
                return true;
            }
            logger.i("Falling back to applying wallpaper by Web URL");
        }

        final Uri resultImageUrl = booruHttpClient.proxify(post.getDirectImageUrl());

        return publish(post, resultImageUrl, booruHttpClient);
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
            booruHttpClient.downloadImage(post, new File(newWallpaperFilePath), percentComplete -> {
                logger.v("Downloaded " + percentComplete + "%");
            }, config.getHttpRetryCount());
            logger.i("Image loaded successfully to file " + newWallpaperFilePath);
        } catch (IOException e) {
            logger.e("Failed to download image to file " + newWallpaperFilePath, e);
            return false;
        }

        return publish(post, Uri.fromFile(new File(newWallpaperFilePath)), booruHttpClient);
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

    private boolean publish(Post post, Uri imageUrl, BooruHttpClient booruHttpClient) {
        final Uri postUrl = booruHttpClient.proxify(post.getPostUrl());
        logger.i("Publishing post " + post + "; image URL: " + imageUrl);
        Artwork artwork = new Artwork.Builder()
                .persistentUri(imageUrl)
                .title(post.getTags())
                .byline(post.getAuthor())
                .token(Integer.toString(post.getId()))
                .webUri(postUrl)
                .build();

        logger.i("Artwork: " + artwork);

        Uri artworkUri = ProviderContract.getProviderClient(getApplicationContext(),
                BooruArtProvider.class).addArtwork(artwork);

        if(null == artworkUri) {
            logger.e("Failed to add artwork");
            return false;
        }

        logger.i("Successfully added artwork. Uri: " + artworkUri);
        return true;
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

    private Post selectNewPost(BooruHttpClient booruHttpClient, BaseBooru booru, Config config, SQLiteDatabase db, Boolean showInfo) throws IOException {
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
            return null;
        }

        if(showInfo)
            Utils.showToast("Image #" + validImageCounter + "/" + imageCounter + " selected" +
                    (allRotated ? ". All Posts with this tag rotated" : ""));

        Database.addHash(db, post.getHash());

        return post;
    }
}
