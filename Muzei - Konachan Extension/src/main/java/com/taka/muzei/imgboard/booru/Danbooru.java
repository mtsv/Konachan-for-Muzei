package com.taka.muzei.imgboard.booru;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.taka.muzei.imgboard.Logger;
import com.taka.muzei.imgboard.posts.BaseRawPost;
import com.taka.muzei.imgboard.posts.Post;

import java.util.Map;

public class Danbooru extends BaseBooru {
    private static final Logger logger = new Logger(Danbooru.class);

    protected Danbooru() {
        super("danbooru.donmai.us",
                "https",
                "danbooru.donmai.us",
                "posts.json",
                "order",
                DanbooruRawPost.class);
    }

    public static class DanbooruRawPost extends BaseRawPost {
        public String md5;
        public String file_url;
        public String tag_string;
        public String uploader_name;
        public String file_size;
    }

    @Override @NonNull
    public Uri getPostUrl(Integer postId) {
        return new Uri.Builder()
                .scheme(getHttpProtocol())
                .authority(domainName())
                .appendPath("posts")
                .appendPath(postId.toString())
                .build();
    }

    @Override
    public void addPageParameter(Map<String, String> parameters, int page) {
        parameters.put("page", Integer.toString(page + 1));
    }

    @Override @NonNull
    public Post constructPost(BaseRawPost rawPost) {
        if(rawPost instanceof DanbooruRawPost) {
            DanbooruRawPost typedRawPost = (DanbooruRawPost)rawPost;

            String fileUrl = typedRawPost.file_url;

            if(fileUrl != null && !fileUrl.startsWith(getHttpProtocol())) {
                logger.i("Fixing URL: " + fileUrl);
                fileUrl = getBaseUrl() + typedRawPost.file_url;
                logger.i("Fixed URL: " + fileUrl);
            }

            return new Post(typedRawPost.id,
                    typedRawPost.md5,
                    typedRawPost.uploader_name,
                    typedRawPost.tag_string,
                    null == fileUrl ? null : Uri.parse(fileUrl),
                    getPostUrl(rawPost.id),
                    typedRawPost.file_size);
        }
        throw new RuntimeException("Invalid post type");
    }
}
