package com.taka.muzei.imgboard.booru;

import android.net.Uri;

import com.taka.muzei.imgboard.Config;
import com.taka.muzei.imgboard.Logger;
import com.taka.muzei.imgboard.posts.BaseRawPost;
import com.taka.muzei.imgboard.posts.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;

public abstract class BaseBooru {
    private static final Logger logger = new Logger(BaseBooru.class);

    private String name;
    private String httpProto;
    private String domainName;
    private String apiEndpoint;
    private String sortTag;
    private Class<? extends BaseRawPost> rawPostClass;

    private static List<BaseBooru> boorus = new ArrayList<BaseBooru>();
    static {
        boorus.add(new Konachan());
        boorus.add(new Gelbooru());
        boorus.add(new Danbooru());
        boorus.add(new Safebooru());
        boorus.add(new YandeRe());
    }

    @NonNull
    public static BaseBooru construct(Config config) {
        final String name = config.getBooru();
        logger.i(name);
        for(BaseBooru booru : boorus) {
            if(booru.name().equals(name)) {
                logger.i("Selected source: " + booru.name());
                return booru;
            }
        }

        logger.e("No booru with name '" + name + "' registered");
        throw new RuntimeException("Unregistered source name " + name);
    }

    protected BaseBooru(String name, String httpProto, String domainName, String apiEndpoint, String sortTag, Class<? extends BaseRawPost> rawPostClass) {
        this.name = name;
        this.httpProto = httpProto;
        this.domainName = domainName;
        this.apiEndpoint = apiEndpoint;
        this.sortTag = sortTag;
        this.rawPostClass = rawPostClass;
    }

    @NonNull
    public String name() { return name; }

    @NonNull
    public String getHttpProtocol() { return httpProto; }

    @NonNull
    public String domainName() { return domainName; }

    @NonNull
    public String getApiEndpoint() { return apiEndpoint; }

    @NonNull
    public String sortTag() { return sortTag; }

    @NonNull
    public Uri getBaseUrl() {
        return new Uri.Builder()
                .scheme(getHttpProtocol())
                .authority(domainName())
                .build();
    }

    @NonNull
    public abstract Uri getPostUrl(Integer postId);

    @NonNull
    public Class<? extends BaseRawPost> getRawPostClass() { return rawPostClass; }

    public void addExtraParameters(Map<String, String> parameters) {}

    public abstract void addPageParameter(Map<String, String> parameters, int page);

    public void addLimitParameter(Map<String, String> parameters, int limit) {
        parameters.put("limit", String.valueOf(limit));
    }

    public void addTagsParameter(Map<String, String> parameters, String tags, String sortType, Boolean restrictContent) {
        String resultTagsString = tags + " " + sortTag() + ":" + sortType;

        if (restrictContent){
            resultTagsString += " rating:safe";
        }

        parameters.put("tags", resultTagsString);
    }

    @NonNull
    public abstract Post constructPost(BaseRawPost rawPost);
}
