package com.taka.muzei.imgboard.booru;

import android.net.Uri;

import com.taka.muzei.imgboard.posts.BaseRawPost;
import com.taka.muzei.imgboard.posts.Post;

import java.util.Map;

import androidx.annotation.NonNull;

public class YandeRe extends BaseBooru {
    protected YandeRe() {
        super("yande.re",
                "https",
                "yande.re",
                "post.json",
                "order",
                YandeReRawPost.class);
    }

    public static class YandeReRawPost extends BaseRawPost {
        public String md5;
        public String file_url;
        public String tags;
        public String author;
        public String file_size;
    }

    @Override @NonNull
    public Uri getPostUrl(Integer postId) {
        return new Uri.Builder()
                .scheme(getHttpProtocol())
                .authority(domainName())
                .appendPath("post")
                .appendPath("show")
                .appendPath(postId.toString())
                .build();
    }

    @Override
    public void addPageParameter(Map<String, String> parameters, int page) {
        parameters.put("page", Integer.toString(page + 1));
    }

    @Override @NonNull
    public Post constructPost(BaseRawPost rawPost) {
        if(rawPost instanceof YandeReRawPost) {
            YandeReRawPost typedRawPost = (YandeReRawPost)rawPost;
            return new Post(typedRawPost.id,
                    typedRawPost.md5,
                    typedRawPost.author,
                    typedRawPost.tags,
                    null == typedRawPost.file_url ? null : Uri.parse(typedRawPost.file_url),
                    getPostUrl(rawPost.id),
                    typedRawPost.file_size);
        }
        throw new RuntimeException("Invalid post type");
    }
}
