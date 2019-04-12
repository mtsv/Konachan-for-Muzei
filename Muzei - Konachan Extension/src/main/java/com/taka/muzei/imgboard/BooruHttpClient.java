package com.taka.muzei.imgboard;

import android.net.Uri;
import android.support.annotation.NonNull;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taka.muzei.imgboard.booru.BaseBooru;
import com.taka.muzei.imgboard.booru.BaseErrorReply;
import com.taka.muzei.imgboard.posts.BaseRawPost;
import com.taka.muzei.imgboard.posts.Post;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;


public class BooruHttpClient {
    private static final Logger logger = new Logger(BooruHttpClient.class);

    private OkHttpClient client;
    private Uri baseUrl;
    private Uri proxy;
    private String proxyUrlParameter = "u";

    private Uri removeQueryParameter(Uri uri, String parameter) {
        Uri.Builder proxyUriBuilder = uri.buildUpon();

        proxyUriBuilder.clearQuery();
        for(String p : uri.getQueryParameterNames()) {
            if(!p.equals(parameter))
                proxyUriBuilder.appendQueryParameter(p, uri.getQueryParameter(p));
        }

        return proxyUriBuilder.build();
    }

    BooruHttpClient(Uri baseUrl, Uri proxy) {
        this.baseUrl = baseUrl;
        this.proxy = null == proxy ? proxy : removeQueryParameter(proxy, proxyUrlParameter);

        initApiClient();
    }

    private void initApiClient() {
        client = new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Response response = chain.proceed(request);

                    if(response.isSuccessful()) {
                        logger.i("reply success: " + response.toString());
                        if(null != proxy) {
                            final String urlParameter = request.url().queryParameter(proxyUrlParameter);
                            final String urlParameterEncoded = encodeUrl(urlParameter);
                            final String responseRequestUrl = response.request().url().toString();
                            if(!responseRequestUrl.contains(urlParameterEncoded)) {
                                logger.e("parameter with encoded URL " + urlParameterEncoded + " missing in reply URL " + responseRequestUrl);
                                throw new IOException("Took too long to response? Returned URL: " + responseRequestUrl);
                            }
                        }
                    } else {
                        throw new IOException("HTTP request failed. Code: " + response.code() + ". Message: " + response.message());
                    }

                    return response;
                })
                .build();
    }

    private String encodeUrl(Uri uri) {
        return encodeUrl(uri.toString());
    }

    private String encodeUrl(String uri) {
        try {
            return URLEncoder.encode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to encode url: " + e.toString());
        }
    }

    public Uri proxify(Uri uri) {
        logger.i("Proxifying URL " + uri.toString());
        if (proxy == null) {
            logger.i("Proxy is empty, URL remains the same");
            return uri;
        }

        logger.i("Proxy URL: " + proxy);
        Uri result =  proxy.buildUpon()
                .appendQueryParameter(proxyUrlParameter, uri.toString())
                .build();
        logger.i("Proxified URL: " + result);
        return result;
    }

    @NonNull
    public List<Post> getPopularPosts(BaseBooru booru, String tags, String sortType, int page, int limit, Boolean restrictContent) throws IOException {
        Map<String, String> parameters = new HashMap<>();

        booru.addTagsParameter(parameters, tags, sortType, restrictContent);
        booru.addLimitParameter(parameters, limit);
        booru.addExtraParameters(parameters);

        if(page > 0)
            booru.addPageParameter(parameters, page);

        Uri.Builder builder = new Uri.Builder();

        builder.scheme(baseUrl.getScheme());
        builder.authority(baseUrl.getAuthority());
        builder.path(booru.getApiEndpoint());

        logger.i("Getting popular posts. API endpoint: " + booru.getApiEndpoint());
        logger.i("Parameters:");
        for(String p : new TreeSet<>(parameters.keySet())) {
            logger.i(p + "=" + parameters.get(p));
            builder.appendQueryParameter(p, parameters.get(p));
        }

        final String booruUrl = builder.build().toString();

        logger.i("Getting popular posts. Booru endpoint: " + booruUrl);

        String url;
        if(null == proxy) {
            url = booruUrl;
        } else {
            url = proxy
                    .buildUpon()
                    .appendQueryParameter(proxyUrlParameter, booruUrl)
                    .build()
                    .toString();
            logger.i("Proxied endpoint: " + url);
        }

        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        final String body = response.body().string();

        return parsePopularPosts(body, booru);
    }

    @NonNull
    private List<Post> parsePopularPosts(String body, BaseBooru booru) throws IOException {
        List<Post> result = new ArrayList<>();

        if(body.isEmpty())
            return result;

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            List<? extends BaseRawPost> rawPosts = mapper.readValue(body, mapper.getTypeFactory().constructCollectionType(List.class, booru.getRawPostClass()));
            logger.d("Received " + rawPosts.size() + " posts");
            for (BaseRawPost rp : rawPosts) {
                Post p = booru.constructPost(rp);
                result.add(p);
            }
        } catch(JsonParseException e) {
            throw new IOException("Failed to parse server reply to json: " + e.getMessage(), e);
        } catch (JsonMappingException e) {
            try {
                BaseErrorReply errorReply = mapper.readValue(body, BaseErrorReply.class);
                throw new IOException("Server returned error: " + errorReply, e);
            } catch (JsonMappingException ex) {
                throw new IOException("Failed to parse server reply to json: " + ex.getMessage(), ex);
            }
        }

        return result;
    }

    public interface fileDownloadProgress {
        void notifyProgress(float percentComplete);
    }

    public static void download(Uri uri, File file, fileDownloadProgress callback) throws IOException {
        Request request = new Request.Builder()
                .url(uri.toString())
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(request).execute();

        try(ResponseBody body = response.body()) {
            final long conLength = body.contentLength();
            BufferedInputStream bis = new BufferedInputStream(body.byteStream());

            byte[] data = new byte[1024];

            try (FileOutputStream fOut = new FileOutputStream(file)) {
                float percentComplete = 0;
                int count = 0;
                int received = 0;
                while ((count = bis.read(data)) != -1) {
                    received += count;
                    percentComplete = Math.round(((received * 100) / conLength));
                    fOut.write(data, 0, count);
                    callback.notifyProgress(percentComplete);
                }
            }
        }
    }
}