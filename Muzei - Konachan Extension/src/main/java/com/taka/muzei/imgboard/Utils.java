package com.taka.muzei.imgboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intentfilter.androidpermissions.PermissionManager;
import com.intentfilter.androidpermissions.models.DeniedPermissions;

import java.io.PrintWriter;
import java.io.StringWriter;

import static java.util.Collections.singleton;

public class Utils {
    private static final Logger logger = new Logger(Utils.class);

    public static <T> String pojoToJsonString(T t) {
        ObjectMapper mapper = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        try {
            return mapper.writeValueAsString(t);
        } catch (JsonProcessingException e) {
            return String.valueOf(t.hashCode());
        }
    }

    public static String stacktraceToString(@NonNull Throwable th) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        th.printStackTrace(pw);
        return sw.toString();
    }

    public static void showToast(final String text) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            Toast toast = Toast.makeText(MuzeiBooruApplication.getAppContext(), text, Toast.LENGTH_LONG);
            toast.show();
        });
    }

    public static void doWithPermission(String permission, Runnable callback, Context context) {
        PermissionManager permissionManager = PermissionManager.getInstance(context);
        permissionManager.checkPermissions(singleton(permission), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {
                logger.i("Permissions granted: " + permission);
                callback.run();
            }

            @Override
            public void onPermissionDenied(DeniedPermissions deniedPermissions) {
                logger.w("Permissions denied: " + permission);
            }
        });
    }
}
