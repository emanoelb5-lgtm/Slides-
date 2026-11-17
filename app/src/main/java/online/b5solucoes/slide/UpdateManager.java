package online.b5solucoes.slide;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

final class UpdateManager implements AutoCloseable {
    static final long MAX_APK=256L*1024*1024;
    final Activity activity; final ExecutorService worker=Executors.newSingleThreadExecutor(); final Handler handler=new Handler(Looper.getMainLooper());
    volatile boolean closed; boolean checking,downloading,awaitingPermission; Info available;
    interface Checked { void done(Info info); }
    static final class Info {
        final int code; final String name,url,hash,notes; final long bytes;
        Info(JSONObject json) throws Exception {
            code=json.getInt("versionCode"); name=json.getString("versionName"); url=json.getString("downloadUrl"); hash=json.getString("sha256").toLowerCase(Locale.ROOT); bytes=json.getLong("size"); notes=json.optString("notes","");
            if(!json.getString("applicationId").equals(BuildConfig.APPLICATION_ID)||code<=0||name.length()>80||!hash.matches("[a-f0-9]{64}")||bytes<=0||bytes>MAX_APK) throw new IOException("Dados de atualização inválidos");
            trusted(url);
        }
    }
    UpdateManager(Activity a) { activity=a; }
    static URL trusted(String raw) throws IOException {
        URL u=new URL(raw); String h=u.getHost().toLowerCase(Locale.ROOT);
        if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||!(h.equals("github.com")||h.equals("api.github.com")||h.equals("objects.githubusercontent.com")||h.equals("release-assets.githubusercontent.com")||h.endsWith(".githubusercontent.com"))) throw new IOException("Endereço de atualização não autorizado");
        return u;
    }
    static HttpURLConnection connect(String url) throws IOException {
        URL next=trusted(url);
        for(int i=0;i<7;i++) {
            HttpURLConnection c=(HttpURLConnection)next.openConnection(); c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(45000); c.setRequestProperty("User-Agent","SlideTD98/"+BuildConfig.VERSION_NAME); c.setRequestProperty("Accept","application/vnd.github+json, application/octet-stream");
            int status=c.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308) { String location=c.getHeaderField("Location"); c.disconnect(); if(location==null) throw new IOException("Redirecionamento sem destino"); next=trusted(new URL(next,location).toString()); continue; }
            if(status==404) { c.disconnect(); throw new FileNotFoundException("Nenhuma versão pública foi encontrada no GitHub."); }
            if(status<200||status>=300) { c.disconnect(); throw new IOException(status==403||status==429?"O GitHub limitou as consultas. Tente novamente mais tarde.":"Servidor indisponível ("+status+")"); }
            return c;
        }
        throw new IOException("Redirecionamentos demais na atualização");
    }
    static JSONObject json(String url) throws Exception {
        HttpURLConnection c=connect(url);
        try(InputStream in=c.getInputStream()) { return new JSONObject(new String(Render.read(in,256*1024),java.nio.charset.StandardCharsets.UTF_8)); } finally { c.disconnect(); }
    }
    void check(boolean manual,Checked callback) {
        if(checking||closed) return;
        android.content.SharedPreferences prefs=activity.getSharedPreferences("updates",Context.MODE_PRIVATE);
        if(!manual&&System.currentTimeMillis()-prefs.getLong("last_check",0)<12*60*60*1000L) return;
        checking=true; ProgressDialog dialog=null;
        if(manual) { dialog=new ProgressDialog(activity); dialog.setMessage("Consultando atualizações no GitHub…"); dialog.setCancelable(false); dialog.show(); }
        final ProgressDialog wait=dialog;
        worker.execute(()->{
            try {
                JSONObject release=json("https://api.github.com/repos/"+BuildConfig.UPDATE_REPOSITORY+"/releases/latest");
                JSONArray assets=release.getJSONArray("assets"); String manifest=null;
                for(int i=0;i<assets.length();i++) if(assets.getJSONObject(i).getString("name").equals("update.json")) manifest=assets.getJSONObject(i).getString("browser_download_url");
                if(manifest==null) throw new IOException("A versão publicada ainda não tem um pacote de atualização compatível.");
                Info info=new Info(json(manifest)); prefs.edit().putLong("last_check",System.currentTimeMillis()).apply();
                handler.post(()->{ checking=false; if(closed) return; if(wait!=null) wait.dismiss(); available=info.code>BuildConfig.VERSION_CODE?info:null; if(callback!=null) callback.done(available); if(manual) { if(available!=null) offer(available); else Ui.dialog(activity,"Tudo atualizado","Você está usando a versão "+BuildConfig.VERSION_NAME+". Nenhuma versão mais nova está disponível."); } });
            } catch(Exception e) { handler.post(()->{ checking=false; if(closed) return; if(wait!=null) wait.dismiss(); if(manual) Ui.dialog(activity,"Atualização indisponível",Ui.error(e)+"\n\nSuas apresentações continuam funcionando offline."); }); }
        });
    }
    void offer(Info info) {
        new AlertDialog.Builder(activity).setTitle("Nova versão: "+info.name).setMessage((info.notes.isEmpty()?"Uma nova versão do Slide TD98 está disponível.":info.notes)+"\n\nTamanho: "+String.format(Locale.ROOT,"%.1f",info.bytes/1048576f)+" MB. O Android pedirá confirmação para instalar. Seus arquivos e a posição das aulas serão preservados.").setPositiveButton("Baixar e instalar",(d,n)->download(info)).setNegativeButton("Agora não",null).show();
    }
    void download(Info info) {
        if(downloading||closed) return; downloading=true;
        ProgressDialog progress=new ProgressDialog(activity); progress.setTitle("Baixando "+info.name); progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); progress.setMax(100); progress.setCancelable(false); progress.show();
        worker.execute(()->{
            File target=UpdateProvider.apk(activity),part=new File(target.getParentFile(),"download.part");
            try {
                target.getParentFile().mkdirs(); if(target.getParentFile().getUsableSpace()<info.bytes+8*1024*1024) throw new IOException("Espaço insuficiente para baixar a atualização");
                HttpURLConnection c=connect(info.url); MessageDigest digest=MessageDigest.getInstance("SHA-256"); long total=0; int prior=-1;
                try(InputStream in=c.getInputStream(); OutputStream out=new FileOutputStream(part)) {
                    byte[] buf=new byte[65536]; int n;
                    while((n=in.read(buf))!=-1) { if(closed) throw new IOException("Download interrompido"); total+=n; if(total>info.bytes||total>MAX_APK) throw new IOException("Tamanho da atualização não confere"); out.write(buf,0,n); digest.update(buf,0,n); int pct=(int)(total*100/info.bytes); if(pct!=prior) { prior=pct; handler.post(()->{ if(!closed) progress.setProgress(pct); }); } }
                } finally { c.disconnect(); }
                if(total!=info.bytes||!MessageDigest.isEqual(info.hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),LibraryStore.hex(digest.digest()).getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new IOException("O download falhou na verificação de integridade. Tente novamente.");
                verify(part,info.code);
                if(target.exists()&&!target.delete()) throw new IOException("Não foi possível substituir o arquivo temporário");
                if(!part.renameTo(target)) throw new IOException("Não foi possível preparar a instalação");
                handler.post(()->{ downloading=false; if(closed) return; progress.dismiss(); install(); });
            } catch(Exception e) { part.delete(); handler.post(()->{ downloading=false; if(closed) return; progress.dismiss(); Ui.dialog(activity,"Atualização não instalada",Ui.error(e)); }); }
        });
    }
    @SuppressWarnings("deprecation")
    void verify(File apk,int expectedCode) throws Exception {
        PackageManager pm=activity.getPackageManager(); PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES),current=pm.getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);
        if(archive==null||!current.packageName.equals(archive.packageName)||archive.versionCode!=expectedCode||archive.versionCode<=current.versionCode) throw new IOException("O APK não é uma atualização válida deste aplicativo");
        if(archive.signatures==null||current.signatures==null||archive.signatures.length==0||archive.signatures.length!=current.signatures.length) throw new IOException("Assinatura do APK ausente ou incompatível");
        Set<String> expected=new HashSet<>(),found=new HashSet<>(); MessageDigest sha=MessageDigest.getInstance("SHA-256");
        for(Signature s:current.signatures) expected.add(LibraryStore.hex(sha.digest(s.toByteArray())));
        for(Signature s:archive.signatures) found.add(LibraryStore.hex(sha.digest(s.toByteArray())));
        if(!expected.equals(found)) throw new IOException("A assinatura desta atualização não corresponde à assinatura permanente instalada");
    }
    void install() {
        if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity).setTitle("Permitir atualização pelo Slide").setMessage("Na próxima tela, permita que o Slide TD98 instale aplicativos. Depois volte para continuar. Essa permissão é usada apenas para instalar a atualização verificada.").setPositiveButton("Abrir permissão",(d,n)->{
                awaitingPermission=true;
                try { activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName()))); }
                catch(ActivityNotFoundException e) { awaitingPermission=false; Ui.dialog(activity,"Permissão de instalação","Ative a instalação de fontes desconhecidas para o Slide TD98 nas configurações de segurança do projetor e tente novamente."); }
            }).setNegativeButton("Agora não",null).show(); return;
        }
        launchInstaller();
    }
    void launchInstaller() {
        try {
            File apk=UpdateProvider.apk(activity); PackageInfo info=activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),0); if(info==null) throw new IOException("Atualização baixada não encontrada"); verify(apk,info.versionCode);
            Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateProvider.uri(activity),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Slide TD98",UpdateProvider.uri(activity))); activity.startActivity(intent);
        } catch(Exception e) { Ui.dialog(activity,"Instalação indisponível",Ui.error(e)); }
    }
    void resumeInstall() { if(awaitingPermission) { awaitingPermission=false; if(Build.VERSION.SDK_INT<26||activity.getPackageManager().canRequestPackageInstalls()) launchInstaller(); else Ui.dialog(activity,"Permissão não concedida","A atualização foi baixada, mas o Android ainda não permitiu a instalação. Use Atualizações para tentar novamente."); } }
    public void close() { closed=true; handler.removeCallbacksAndMessages(null); worker.shutdownNow(); }
}
