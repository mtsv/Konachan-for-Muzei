package com.taka.muzei.imgboard.booru;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.taka.muzei.imgboard.posts.BaseRawPost;
import com.taka.muzei.imgboard.posts.Post;

import java.util.Map;

public class Safebooru extends BaseBooru {

    protected Safebooru() {
        super("safebooru.org",
                "http",
                "safebooru.org",
                "index.php",
                "sort",
                SafebooruRawPost.class);
    }

    public static class SafebooruRawPost extends BaseRawPost {
        public String hash;
        public String directory;
        public String image;
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
        if(rawPost instanceof SafebooruRawPost) {
            SafebooruRawPost typedRawPost = (SafebooruRawPost)rawPost;

            String fileUrl = null;
            if(null != typedRawPost.directory && null != typedRawPost.image)
                fileUrl = getBaseUrl() + "/images/" + typedRawPost.directory + "/" + typedRawPost.image;

            return new Post(typedRawPost.id,
                    typedRawPost.hash,
                    typedRawPost.owner,
                    typedRawPost.tags,
                    null == fileUrl ? null : Uri.parse(fileUrl),
                    getPostUrl(rawPost.id),
                    null);
        }
        throw new RuntimeException("Invalid post type");
    }
}
