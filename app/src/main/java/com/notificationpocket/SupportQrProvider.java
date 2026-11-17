package com.notificationpocket;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only, URI-granted access to the single bundled support image. */
@SuppressLint("ResourceType") // AAPT stores this drawable JPEG uncompressed for openRawResourceFd.
public final class SupportQrProvider extends ContentProvider {
    private static final String IMAGE_PATH = "/wechat-qr.jpg";
    private static final String DISPLAY_NAME = "notification-pocket-wechat.jpg";

    public static Uri contentUri(Context context) {
        return new Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority(context.getPackageName() + ".support")
                .path(IMAGE_PATH)
                .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return isSupportQr(uri) ? "image/jpeg" : null;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        if (!isSupportQr(uri)) {
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
                row.add(getImageSize());
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!isSupportQr(uri) || !"r".equals(mode)) {
            throw new FileNotFoundException("Only the bundled support QR is readable");
        }
        AssetFileDescriptor descriptor = getContext().getResources()
                .openRawResourceFd(R.drawable.support_wechat_qr);
        if (descriptor == null) {
            throw new FileNotFoundException("Support QR is compressed or unavailable");
        }
        return descriptor;
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

    private static boolean isSupportQr(Uri uri) {
        return IMAGE_PATH.equals(uri.getPath());
    }

    private long getImageSize() {
        AssetFileDescriptor descriptor = null;
        try {
            descriptor = getContext().getResources()
                    .openRawResourceFd(R.drawable.support_wechat_qr);
            return descriptor.getLength();
        } finally {
            if (descriptor != null) {
                try {
                    descriptor.close();
                } catch (IOException ignored) {
                    // The read-only resource size was already obtained.
                }
            }
        }
    }
}
