package com.taka.muzei.imgboard;

import android.net.Uri;

import androidx.annotation.NonNull;

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
        this.proxy = null == proxy ? null : removeQueryParameter(proxy, proxyUrlParameter);

        client = constructHttpClient((request, response) -> {
            if(null != proxy)
                checkResponseContentType(response, "application/json");
        });
    }

    private static void checkResponseContentType(Response response, String expected) throws IOException {
        String contentType = response.header("Content-Type");
        if(null == contentType)
            contentType = "NULL";
        logger.i("Response content type: " + contentType);
        if(!contentType.startsWith(expected)) {
            logger.e("Expected Content-Type: " + expected + ", got: " + contentType);
            throw new IOException("Took too long to response? Returned Content-Type:" + contentType);
        }
    }

    private interface onHttpSuccessfulResponse {
        void call(Request request, Response response) throws IOException;
    }

    private static OkHttpClient constructHttpClient(onHttpSuccessfulResponse callback) {
        return new OkHttpClient.Builder()
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
                .addInterceptor(chain -> {
                    Request request = chain.request();
                    Response response = chain.proceed(request);

                    if(response.isSuccessful()) {
                        logger.i("reply success: " + response.toString());
                        callback.call(request, response);
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
        if (proxy == null) {
            return uri;
        }

        logger.i("Proxifying URL " + uri.toString() + " Proxy URL: " + proxy);
        Uri result =  proxy.buildUpon()
                .appendQueryParameter(proxyUrlParameter, uri.toString())
                .build();
        logger.i("Proxified URL: " + result);
        return result;
    }

    @NonNull
    public List<Post> getPosts(BaseBooru booru, String tags, String sortType, int page, int limit, Boolean restrictContent, int numRetry) throws IOException {
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

        logger.i("Getting posts. API endpoint: " + booru.getApiEndpoint());
        logger.i("Parameters:");
        for(String p : new TreeSet<>(parameters.keySet())) {
            logger.i(p + "=" + parameters.get(p));
            builder.appendQueryParameter(p, parameters.get(p));
        }

        final Uri booruUrl = builder.build();

        logger.i("Getting posts. Booru endpoint: " + booruUrl);

        Uri url = proxify(booruUrl);

        try(final Response response = doRequest(client, url.toString(), numRetry)) {
            final ResponseBody body = response.body();
            final String bodyStr = body.string();
            return parsePosts(bodyStr, booru);
        }
    }

    private static Response doRequest(OkHttpClient client, String url, int numRetry) throws IOException {
        int retryIdx = 0;
        while(true) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .build();
                return client.newCall(request).execute();
            } catch (IOException e) {
                ++retryIdx;
                if (retryIdx == numRetry) {
                    if(numRetry > 0)
                        logger.e("All retries failed. Rethrowing");
                    throw e;
                }
                logger.e("Posts request failed: " + e.getMessage()+ "; Doing retry #" + retryIdx);
            }
        }
    }

    @NonNull
    private List<Post> parsePosts(String body, BaseBooru booru) throws IOException {
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

    public static void downloadImage(Uri uri, File file, fileDownloadProgress callback, int numRetry) throws IOException {
        final OkHttpClient client = constructHttpClient((request, response) -> checkResponseContentType(response, "image/"));
        downloadImpl(client, uri, file, callback, numRetry);
    }

    public void downloadImage(Post post, File file, fileDownloadProgress callback, int numRetry) throws IOException {
        final Uri url = proxify(post.getDirectImageUrl());
        downloadImage(url, file, callback, numRetry);
    }

    private static void downloadImpl(OkHttpClient client, Uri uri, File file, fileDownloadProgress callback, int numRetry) throws IOException {
        try(final Response response = doRequest(client, uri.toString(), numRetry)) {
            final ResponseBody body = response.body();
            final long conLength = body.contentLength();
            logger.d("Content length: " + conLength);
            BufferedInputStream bis = new BufferedInputStream(body.byteStream());

            byte[] data = new byte[1024];

            // Nougat+ restricts notification update rate: https://saket.me/android-7-nougat-rate-limiting-notifications/
            long t = System.currentTimeMillis();
            long notifyStep = 200;

            try (FileOutputStream fOut = new FileOutputStream(file)) {
                float percentComplete;
                int count;
                int received = 0;
                while ((count = bis.read(data)) != -1) {
                    received += count;
                    percentComplete = Math.round(((received * 100) / conLength));
                    fOut.write(data, 0, count);
                    long nt = System.currentTimeMillis();
                    if(nt - t > notifyStep) {
                        t = nt;
                        callback.notifyProgress(percentComplete);
                    }
                }
            }
        }
    }
}
