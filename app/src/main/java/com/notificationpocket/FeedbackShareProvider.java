package com.notificationpocket;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Read-only, URI-granted access to the single feedback file chosen for sharing by the user. */
public final class FeedbackShareProvider extends ContentProvider {
    private static final String FILE_PATH = "/feedback.txt";
    private static final String DISPLAY_NAME = "notification-pocket-feedback.txt";

    public static Uri contentUri(Context context) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(context.getPackageName() + ".feedback")
                .path(FILE_PATH)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return isFeedback(uri) ? "text/plain" : null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (!isFeedback(uri)) {
            return null;
        }
        String[] columns = projection != null ? projection : new String[] {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
        };
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                row.add(DISPLAY_NAME);
            } else if (OpenableColumns.SIZE.equals(column)) {
                Context context = getContext();
                row.add(context == null ? 0L : FeedbackReport.shareFile(context).length());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || !isFeedback(uri) || !"r".equals(mode)) {
            throw new FileNotFoundException("Only the selected feedback report is readable");
        }
        File file = FeedbackReport.shareFile(context);
        if (!file.isFile()) {
            throw new FileNotFoundException("Feedback report is unavailable");
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }

    private boolean isFeedback(Uri uri) {
        Context context = getContext();
        return context != null && contentUri(context).equals(uri);
    }
}
