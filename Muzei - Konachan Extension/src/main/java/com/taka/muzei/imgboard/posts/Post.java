package com.taka.muzei.imgboard.posts;

import android.net.Uri;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.taka.muzei.imgboard.Utils;

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

    public Uri getPostUrl() { return null == postUrl ? getDirectImageUrl() : postUrl; }

    public boolean isValid() {
        return null != getHash() && null != getDirectImageUrl();
    }

    public boolean isExtensionValid() {
        Uri imageUrl = getDirectImageUrl();
        if(null == imageUrl)
            return false;
        String file_url_lower = imageUrl.toString().toLowerCase();
        return file_url_lower.endsWith(".png") || file_url_lower.endsWith(".jpeg") || file_url_lower.endsWith(".jpg");
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
