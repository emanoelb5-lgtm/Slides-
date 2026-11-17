package online.b5solucoes.slide;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;

final class LibraryStore {
    static final long MAX_FILE=512L*1024*1024;
    static final class Item { String file,name; int page,count; Item(String f,String n,int p,int c) { file=f; name=n; page=p; count=c; } }
    final Context context; final File dir; final SharedPreferences prefs;
    LibraryStore(Context c) { context=c.getApplicationContext(); dir=new File(c.getFilesDir(),"presentations"); dir.mkdirs(); prefs=c.getSharedPreferences("slide",Context.MODE_PRIVATE); }
    synchronized List<Item> items() {
        List<Item> out=new ArrayList<>();
        try { JSONArray arr=new JSONArray(prefs.getString("recent","[]")); for(int i=0;i<arr.length();i++) { JSONObject j=arr.getJSONObject(i); String f=j.getString("file"); if(!f.contains("/")&&!f.contains("\\")&&new File(dir,f).isFile()) out.add(new Item(f,j.getString("name"),j.optInt("page",0),j.optInt("count",0))); } } catch(Exception ignored) {}
        return out;
    }
    synchronized void touch(Item item) {
        List<Item> all=items(); all.removeIf(i->i.file.equals(item.file)); all.add(0,item); JSONArray arr=new JSONArray();
        try { for(Item i:all.subList(0,Math.min(24,all.size()))) { JSONObject j=new JSONObject(); j.put("file",i.file); j.put("name",i.name); j.put("page",i.page); j.put("count",i.count); arr.put(j); } } catch(JSONException ignored) {}
        prefs.edit().putString("recent",arr.toString()).apply();
    }
    synchronized Item find(String file) { for(Item i:items()) if(i.file.equals(file)) return i; return null; }
    Item importUri(Uri uri) throws Exception {
        String name=null;
        if("content".equals(uri.getScheme())) try(Cursor cur=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) { if(cur!=null&&cur.moveToFirst()) name=cur.getString(0); } catch(Exception ignored) {}
        if(name==null) name=uri.getLastPathSegment(); if(name==null) name="apresentacao";
        try(InputStream in=context.getContentResolver().openInputStream(uri)) { if(in==null) throw new IOException("Não foi possível abrir o arquivo"); return copy(in,name); }
    }
    Item importFile(File file) throws Exception { try(InputStream in=new FileInputStream(file)) { return copy(in,file.getName()); } }
    Item copy(InputStream in,String name) throws Exception {
        File tmp=File.createTempFile("import-",".tmp",dir);
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); byte[] buffer=new byte[65536]; int n; long total=0;
            try(OutputStream out=new FileOutputStream(tmp)) {
                while((n=in.read(buffer))!=-1) { total+=n; if(total>MAX_FILE) throw new IOException("O arquivo excede 512 MB. Divida a apresentação em partes."); if(dir.getUsableSpace()<n+8*1024*1024) throw new IOException("Armazenamento insuficiente no projetor"); digest.update(buffer,0,n); out.write(buffer,0,n); }
            }
            if(total==0) throw new IOException("O arquivo está vazio");
            name=name.replaceAll("[\\x00-\\x1f]",""); if(name.length()>200) name=name.substring(name.length()-200);
            String ext=Formats.extension(name);
            if(!Formats.supported(name)) {
                try(InputStream check=new FileInputStream(tmp)) { byte[] head=new byte[8]; int len=check.read(head); if(len>=4&&head[0]=='%'&&head[1]=='P'&&head[2]=='D'&&head[3]=='F') ext="pdf"; else if(len>=4&&head[0]=='P'&&head[1]=='K') { try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(tmp)) { ext=zip.getEntry("ppt/presentation.xml")!=null?"pptx":zip.getEntry("content.xml")!=null?"odp":"zip"; } } else if(len>=2&&(head[0]&255)==0xd0&&(head[1]&255)==0xcf) ext="ppt"; else throw new IOException("Extensão não reconhecida: "+name+". Use PDF, PowerPoint, ODP, imagens ou ZIP de imagens."); }
            }
            String hash=hex(digest.digest()).substring(0,32),filename=hash+"."+ext;
            File target=new File(dir,filename); if(!target.exists()&&!tmp.renameTo(target)) throw new IOException("Falha ao guardar a apresentação");
            Item prior=find(filename),item=new Item(filename,name,prior==null?0:prior.page,prior==null?0:prior.count); touch(item); return item;
        } finally { tmp.delete(); }
    }
    static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for(byte value:b) s.append(String.format(Locale.ROOT,"%02x",value&255)); return s.toString(); }
}
