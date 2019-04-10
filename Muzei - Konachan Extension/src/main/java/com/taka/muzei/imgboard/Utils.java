package com.taka.muzei.imgboard;

import android.support.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Utils {
    public static <T> String pojoToJsonString(T t) {
        ObjectMapper mapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        try {
            return mapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            return String.valueOf(t.hashCode());
        }
    }

    public static String extractFileExtension(String path) {
        final int lastDotPos = path.lastIndexOf('.');
        if(lastDotPos < 0)
            return path;
        return path.substring(lastDotPos + 1);
    }

    public static String cleanFileName(String name) {
        return name.replaceAll("[^\\d\\s\\w.\\-+\\[\\]()]", "_");
    }

    public static String stacktraceToString(@NonNull Throwable th) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        th.printStackTrace(pw);
        return sw.toString();
    }
}
