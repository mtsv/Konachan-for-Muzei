package com.taka.muzei.imgboard;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.RemoteActionCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.apps.muzei.api.provider.Artwork;
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public class BooruArtProvider extends MuzeiArtProvider {
    private final Logger logger = new Logger(BooruArtProvider.class);
    private int requestCodeCounter = 0;

    private static class Command {
        final int id;
        final int icon;
        final int title;
        final int description;
        Command(int id, int icon, int title, int description) {
            this.id = id;
            this.icon = icon;
            this.title = title;
            this.description = description;
        }
    }

    private static Set<Command> commands = new LinkedHashSet<>();
    static {
        commands.add(new Command(CommandService.COMMAND_DOWNLOAD, android.R.drawable.stat_sys_download_done, R.string.command_download_title, R.string.command_download_description));
        commands.add(new Command(CommandService.COMMAND_DELETE, android.R.drawable.ic_menu_delete, R.string.command_delete_title, R.string.command_delete_description));
    }

    @Override
    public void onLoadRequested(boolean initial) {
        logger.i("Muzei requested new artwork");
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_ROAMING).build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(BooruLoadWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance().enqueue(workRequest);
    }

    @Override
    public boolean onCreate() {
        super.onCreate();

        MuzeiBooruApplication.setUpLogging();
        return true;
    }

    @Override
    @NonNull
    public List<RemoteActionCompat> getCommandActions(@NonNull final Artwork artwork) {
        List<RemoteActionCompat> result = new ArrayList<>();
        Context context = getContext();
        if(null == context)
            return result;



        logger.i("getCommandActions called. Artwork id:" + artwork.getId());

        commands.forEach(e -> {
            Intent intent = new Intent(context, com.taka.muzei.imgboard.CommandService.class);
            intent.putExtra("command", e.id);
            intent.putExtra("title", artwork.getTitle());
            intent.putExtra("uri", artwork.getPersistentUri());
            intent.putExtra("id", artwork.getId());
            intent.putExtra("content_uri", getContentUri());
            result.add(new RemoteActionCompat(IconCompat.createWithResource(context, e.icon),
                    context.getString(e.title),
                    context.getString(e.description),
                    PendingIntent.getService(context, requestCodeCounter, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            ));

            ++requestCodeCounter;
        });
        return result;
    }
}
