package com.taka.muzei.imgboard;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.Closeable;
import java.util.Collection;

public class Database {
    private static final Logger logger = new Logger(Database.class);
    private static final String TEXT_TYPE = " TEXT";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + DatabaseContract.ImagesEntry.TABLE_NAME + " (" +
                    DatabaseContract.ImagesEntry.COLUMN_NAME_MD5_ID + " INTEGER PRIMARY KEY," +
                    DatabaseContract.ImagesEntry.COLUMN_NAME_MD5 + TEXT_TYPE + COMMA_SEP +
                    DatabaseContract.ImagesEntry.COLUMN_NAME_TIMESTAMP + TEXT_TYPE + " )";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + DatabaseContract.ImagesEntry.TABLE_NAME;
    public static class DatabaseHelper extends SQLiteOpenHelper implements Closeable {
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "Images.db";

        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL(SQL_DELETE_ENTRIES);
            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }


    public static void removeStaleMD5(SQLiteDatabase dbWrite, long clearTime) {
        final String query = "DELETE FROM images WHERE timestamp <= ?";
        logger.d("Query: " + query);
        dbWrite.execSQL(query, new String[]{Long.toString(System.currentTimeMillis() - clearTime)});
    }

    public static void removeHashes(SQLiteDatabase dbWrite, Collection<String> hashes) {
        for(String hash : hashes) {
            final String query = "DELETE FROM images WHERE md5='" + hash + "'";
            logger.d("Query: " + query);
            dbWrite.execSQL(query);
        }
    }

    public static boolean hasHash(SQLiteDatabase dbWrite, String hash) {
        final String query = "SELECT md5, timestamp FROM images WHERE md5='" + hash + "'";
        logger.d("Query: " + query);
        try(Cursor cursor = dbWrite.rawQuery(query, null)) {
            return cursor.getCount() > 0;
        }
    }

    public static void addHash(SQLiteDatabase dbWrite, String hash) {
        final String query = "INSERT INTO images (md5, timestamp) VALUES (?, ?)";
        logger.d("Query: " + query);
        dbWrite.execSQL(query, new String[]{hash, Long.toString(System.currentTimeMillis())});
    }

    public static void truncateStoredImages(SQLiteDatabase dbWrite) {
        dbWrite.execSQL("DELETE FROM images");
    }
}
