package online.b5solucoes.slide;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import java.io.*;

/** Grants the system installer read-only access to exactly one verified update APK. */
public final class UpdateProvider extends ContentProvider {
    static File apk(Context c) { return new File(new File(c.getCacheDir(),"updates"),"update.apk"); }
    static Uri uri(Context c) { return Uri.parse("content://"+c.getPackageName()+".updates/update.apk"); }
    @Override public boolean onCreate() { return true; }
    void validate(Uri uri) throws FileNotFoundException { if(!"/update.apk".equals(uri.getPath())||uri.getQuery()!=null) throw new FileNotFoundException("Arquivo inválido"); }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException { validate(uri); if(!mode.equals("r")) throw new FileNotFoundException("Somente leitura"); return ParcelFileDescriptor.open(apk(getContext()),ParcelFileDescriptor.MODE_READ_ONLY); }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        try { validate(uri); } catch(FileNotFoundException e) { return null; }
        String[] fields=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor c=new MatrixCursor(fields,1); Object[] row=new Object[fields.length];
        for(int i=0;i<fields.length;i++) { if(fields[i].equals(OpenableColumns.DISPLAY_NAME)) row[i]="Slide-TD98.apk"; else if(fields[i].equals(OpenableColumns.SIZE)) row[i]=apk(getContext()).length(); }
        c.addRow(row); return c;
    }
    @Override public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException(); }
}
