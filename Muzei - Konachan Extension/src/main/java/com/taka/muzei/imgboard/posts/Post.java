package com.taka.muzei.imgboard.posts;

import android.net.Uri;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.taka.muzei.imgboard.FileUtils;
import com.taka.muzei.imgboard.Utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Post {
    private int id;
    private String hash;
    private String author;
    private String tags;
    @JsonSerialize(using = ToStringSerializer.class)
    private Uri directImageUrl;
    @JsonSerialize(using = ToStringSerializer.class)
    private Uri postUrl;
    private String fileSize;

    public static final Set<String> allowedExtensions = new HashSet<>(Arrays.asList("png", "jpeg", "jpg"));

    public Post(int id, String hash, String author, String tags, Uri directImageUrl, Uri postUrl, String fileSize) {
        this.id = id;
        this.hash = hash;
        this.author = author;
        this.tags = tags;
        this.directImageUrl = directImageUrl;
        this.postUrl = postUrl;
        this.fileSize = fileSize;
    }

    public int getId() { return id; }

    public String getHash() { return hash; }

    public String getTags() { return tags; }

    public String getAuthor() { return author; }

    public Uri getDirectImageUrl() { return  directImageUrl; }

    public String getImageExtension() { return null == directImageUrl ? null : FileUtils.extractFileExtension(directImageUrl.toString()); }

    public Uri getPostUrl() { return null == postUrl ? getDirectImageUrl() : postUrl; }

    public boolean isValid() {
        return null != getHash() && null != getDirectImageUrl();
    }

    public boolean isExtensionValid() {
        Uri imageUrl = getDirectImageUrl();
        if(null == imageUrl)
            return false;

        final String extension = FileUtils.extractFileExtension(imageUrl.toString());
        return allowedExtensions.contains(extension);
    }

    public int getFileSize() {
        try {
            return null == fileSize ? 0 : Integer.parseInt(fileSize);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private  <T> String fieldToString(T fieldValue) {
        return null == fieldValue ? "<null>" : fieldValue.toString();
    }

    @Override
    public String toString() {
        return Utils.pojoToJsonString(this);
    }
}
