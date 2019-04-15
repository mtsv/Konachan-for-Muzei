package com.taka.muzei.imgboard.booru;

import android.net.Uri;

import com.taka.muzei.imgboard.posts.BaseRawPost;
import com.taka.muzei.imgboard.posts.Post;

import java.util.Map;

import androidx.annotation.NonNull;

public class Gelbooru extends BaseBooru {

    protected Gelbooru() {
        super("gelbooru.com",
                "https",
                "gelbooru.com",
                "index.php",
                "sort",
                GelbooruRawPost.class);
    }

    public static class GelbooruRawPost extends BaseRawPost {
        public String hash;
        public String file_url;
        public String tags;
        public String owner;
    }

    @Override @NonNull
    public Uri getPostUrl(Integer postId) {
        return new Uri.Builder()
                .scheme(getHttpProtocol())
                .authority(domainName())
                .appendPath("index.php")
                .appendQueryParameter("page", "post")
                .appendQueryParameter("s", "view")
                .appendQueryParameter("id", postId.toString())
                .build();
    }

    @Override
    public void addExtraParameters(Map<String, String> parameters) {
        parameters.put("page", "dapi");
        parameters.put("s","post");
        parameters.put("q","index");
        parameters.put("json","1");
    }

    @Override
    public void addPageParameter(Map<String, String> parameters, int page) {
        parameters.put("pid", Integer.toString(page));
    }

    @Override @NonNull
    public Post constructPost(BaseRawPost rawPost) {
        if(rawPost instanceof GelbooruRawPost) {
            GelbooruRawPost typedRawPost = (GelbooruRawPost)rawPost;
            return new Post(typedRawPost.id,
                    typedRawPost.hash,
                    typedRawPost.owner,
                    typedRawPost.tags,
                    null == typedRawPost.file_url ? null : Uri.parse(typedRawPost.file_url),
                    getPostUrl(rawPost.id),
                    null);
        }
        throw new RuntimeException("Invalid post type");
    }
}
