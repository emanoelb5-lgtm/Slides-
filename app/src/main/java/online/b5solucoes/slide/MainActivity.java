package online.b5solucoes.slide;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int PICK=10,STORAGE=11;
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    LibraryStore store; LinearLayout root; TextView status; Button updateButton; UpdateManager updater;
    private boolean destroyed,busy; private File browsing;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new LibraryStore(this); updater=new UpdateManager(this); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); Ui.fullscreen(this); home();
        if(Intent.ACTION_VIEW.equals(getIntent().getAction())&&getIntent().getData()!=null) importUri(getIntent().getData());
        else updater.check(false,info->{ if(info!=null&&!isFinishing()&&updateButton!=null) updateButton.setText("Atualizar para "+info.name); });
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); if(Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null) importUri(intent.getData()); }
    @Override protected void onResume() { super.onResume(); Ui.fullscreen(this); if(store!=null&&!busy&&browsing==null) home(); if(updater!=null) updater.resumeInstall(); }
    void home() {
        browsing=null; root=Ui.vertical(this); root.setBackgroundColor(Ui.BG);
        int safe=Ui.dp(this,20+store.prefs.getInt("margin",3)*2); root.setPadding(safe,Ui.dp(this,16),safe,Ui.dp(this,14));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=Ui.text(this,"▶  SLIDE",24,Ui.ACCENT); brand.setTypeface(null,android.graphics.Typeface.BOLD); header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=Ui.text(this,"TD98 PRO  ·  v"+BuildConfig.VERSION_NAME,13,Ui.MUTED); header.addView(version); root.addView(header); Ui.gap(root,12);
        LinearLayout body=new LinearLayout(this); body.setGravity(Gravity.CENTER_VERTICAL); root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout intro=Ui.vertical(this); intro.setPadding(0,0,Ui.dp(this,26),0); body.addView(intro,new LinearLayout.LayoutParams(0,-2,.9f));
        TextView headline=Ui.text(this,"Sua aula,\nna tela grande.",32,Ui.TEXT); headline.setTypeface(null,android.graphics.Typeface.BOLD); intro.addView(headline); Ui.gap(intro,12);
        intro.addView(Ui.text(this,"PDF, PowerPoint, OpenDocument\ne imagens direto do pendrive.",16,Ui.MUTED)); Ui.gap(intro,18);
        intro.addView(Ui.text(this,"OFFLINE  •  CONTROLE REMOTO",12,Ui.ACCENT));
        LinearLayout actions=Ui.vertical(this); body.addView(actions,new LinearLayout.LayoutParams(0,-2,1.1f));
        Button open=Ui.button(this,"Abrir apresentação",this::picker); addAction(actions,open);
        addAction(actions,Ui.button(this,"Pendrive e pastas",this::browse));
        LinearLayout row=new LinearLayout(this); Button recent=Ui.button(this,"Recentes",this::recents),demo=Ui.button(this,"Testar controle",this::demo);
        row.addView(recent,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1)); LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,Ui.dp(this,56),1); right.leftMargin=Ui.dp(this,8); row.addView(demo,right); actions.addView(row); Ui.gap(actions,8);
        LinearLayout bottom=new LinearLayout(this); updateButton=Ui.button(this,"Atualizações",()->updater.check(true,null)); Button settings=Ui.button(this,"Opções",this::settings);
        if(updater!=null&&updater.available!=null) updateButton.setText("Atualizar: "+updater.available.name);
        bottom.addView(updateButton,new LinearLayout.LayoutParams(0,Ui.dp(this,56),1)); LinearLayout.LayoutParams opts=new LinearLayout.LayoutParams(0,Ui.dp(this,56),.7f); opts.leftMargin=Ui.dp(this,8); bottom.addView(settings,opts); actions.addView(bottom);
        List<LibraryStore.Item> recentItems=store.items();
        if(!recentItems.isEmpty()) { LibraryStore.Item first=recentItems.get(0); Ui.gap(root,8); Button resume=Ui.button(this,"Continuar: "+first.name+"  ·  slide "+(first.page+1),()->play(first)); resume.setTextSize(14); resume.setSingleLine(); resume.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); root.addView(resume,new LinearLayout.LayoutParams(-1,Ui.dp(this,48))); }
        status=Ui.text(this,"Setas: navegar    OK: selecionar    Voltar: retornar",12,Ui.MUTED); status.setPadding(0,Ui.dp(this,10),0,0); root.addView(status); setContentView(root); open.requestFocus();
    }
    void addAction(LinearLayout parent,Button button) { parent.addView(button,new LinearLayout.LayoutParams(-1,Ui.dp(this,56))); Ui.gap(parent,8); }
    void picker() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(intent,PICK); } catch(ActivityNotFoundException e) {
            try { startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),PICK); } catch(ActivityNotFoundException ignored) { browse(); }
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); if(request==PICK&&result==RESULT_OK&&data!=null&&data.getData()!=null) importUri(data.getData()); }
    interface ImportWork { LibraryStore.Item run() throws Exception; }
    void importing(ImportWork work) {
        if(busy) return; busy=true;
        ProgressDialog wait=new ProgressDialog(this); wait.setMessage("Preparando uma cópia offline da apresentação…"); wait.setCancelable(false); wait.show();
        worker.execute(()->{ try { LibraryStore.Item item=work.run(); runOnUiThread(()->{ busy=false; if(destroyed) return; wait.dismiss(); play(item); }); } catch(Exception|OutOfMemoryError e) { runOnUiThread(()->{ busy=false; if(destroyed) return; wait.dismiss(); Ui.dialog(this,"Não foi possível abrir",Ui.error(e)); }); } });
    }
    void importUri(Uri uri) { importing(()->store.importUri(uri)); }
    void play(LibraryStore.Item item) { store.touch(item); browsing=null; Intent player=new Intent(this,PlayerActivity.class); player.putExtra("file",item.file); startActivity(player); }
    void recents() { List<LibraryStore.Item> all=store.items(); if(all.isEmpty()) { Ui.dialog(this,"Recentes","Abra uma apresentação para ela aparecer aqui. Os arquivos importados ficam disponíveis mesmo sem o pendrive."); return; } String[] names=new String[all.size()]; for(int i=0;i<names.length;i++) names[i]=all.get(i).name+"  ·  slide "+(all.get(i).page+1); new AlertDialog.Builder(this).setTitle("Suas apresentações").setItems(names,(d,n)->play(all.get(n))).setNegativeButton("Voltar",null).show(); }
    void browse() {
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},STORAGE); return; }
        roots();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) { super.onRequestPermissionsResult(request,permissions,grants); if(request==STORAGE) { if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) roots(); else { Ui.dialog(this,"Escolher um arquivo","Sem acesso direto às pastas, use o seletor de arquivos do Android."); picker(); } } }
    void roots() {
        LinkedHashMap<String,File> locations=new LinkedHashMap<>(); locations.put("Downloads",Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)); locations.put("Armazenamento interno",Environment.getExternalStorageDirectory());
        for(String parent:new String[]{"/storage","/mnt/media_rw","/mnt/usb_storage","/mnt/usb_storage1","/mnt/usbhost","/mnt/usbhost1","/mnt/usb","/mnt/usb1","/mnt/usb2","/mnt/sda","/mnt/sdb","/udisk"}) { File p=new File(parent); if(p.isDirectory()&&p.canRead()) { if(!parent.equals("/storage")&&!parent.equals("/mnt/media_rw")) locations.put("Pendrive: "+p.getName(),p); File[] dirs=p.listFiles(); if(dirs!=null) for(File d:dirs) if(d.isDirectory()&&d.canRead()&&!d.getName().equals("emulated")&&!d.getName().equals("self")) locations.put("Unidade: "+d.getName(),d); } }
        for(File d:getExternalFilesDirs(null)) if(d!=null) { String s=d.getAbsolutePath(); int at=s.indexOf("/Android/"); if(at>0) { File volume=new File(s.substring(0,at)); if(volume.canRead()) locations.put("Volume: "+volume.getName(),volume); } }
        List<String> labels=new ArrayList<>(); List<File> paths=new ArrayList<>();
        for(Map.Entry<String,File> entry:locations.entrySet()) if(entry.getValue().isDirectory()&&entry.getValue().canRead()) { labels.add(entry.getKey()); paths.add(entry.getValue()); }
        labels.add("Escolher pelo Android");
        new AlertDialog.Builder(this).setTitle("Pendrive e armazenamento").setItems(labels.toArray(new String[0]),(d,n)->{ if(n==paths.size()) picker(); else folder(paths.get(n)); }).setNegativeButton("Voltar",null).show();
    }
    void folder(File directory) {
        File[] children=directory.listFiles(f->!f.getName().startsWith(".")&&(f.isDirectory()||Formats.supported(f.getName())));
        if(children==null) { Ui.dialog(this,"Pasta sem acesso","O Android desse projetor não liberou esta pasta. Use “Abrir apresentação” para escolher o arquivo."); return; }
        Arrays.sort(children,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):Formats.naturalCompare(a.getName(),b.getName()));
        browsing=directory; LinearLayout panel=Ui.vertical(this); panel.setBackgroundColor(Ui.BG); int pad=Ui.dp(this,28); panel.setPadding(pad,pad,pad,pad);
        TextView title=Ui.text(this,directory.getAbsolutePath(),19,Ui.TEXT); title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.START); panel.addView(title); Ui.gap(panel,12);
        List<String> labels=new ArrayList<>(); labels.add("↑  Pasta anterior / voltar"); for(File f:children) labels.add((f.isDirectory()?"▸  ":"    ")+f.getName()); if(children.length==0) labels.add("Nenhuma apresentação nesta pasta");
        ListView list=new ListView(this); list.setSelector(Ui.focus(this)); list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels) { @Override public View getView(int p,View v,android.view.ViewGroup parent) { TextView t=(TextView)super.getView(p,v,parent); t.setTextColor(Ui.TEXT); t.setTextSize(18); t.setMinHeight(Ui.dp(MainActivity.this,54)); return t; } });
        list.setOnItemClickListener((p,v,n,id)->{ if(n==0) parentFolder(); else if(n-1<children.length) { File file=children[n-1]; if(file.isDirectory()) folder(file); else importing(()->store.importFile(file)); } });
        panel.addView(list,new LinearLayout.LayoutParams(-1,0,1)); panel.addView(Ui.text(this,"Pastas e arquivos podem ser selecionados com as setas e OK.",13,Ui.MUTED)); setContentView(panel); list.requestFocus();
    }
    void parentFolder() { if(browsing!=null&&browsing.getParentFile()!=null&&!browsing.getAbsolutePath().equals("/storage")&&!browsing.getAbsolutePath().equals("/")) folder(browsing.getParentFile()); else home(); }
    void demo() { Intent i=new Intent(this,PlayerActivity.class); i.putExtra("demo",true); startActivity(i); }
    void settings() {
        String[] options={"Margem de segurança: "+store.prefs.getInt("margin",3)+"%","Qualidade: "+(store.prefs.getBoolean("light",false)?"leve (960 px)":"normal (1600 px)"),"Sobre formatos e compatibilidade","Versão, privacidade e licença"};
        new AlertDialog.Builder(this).setTitle("Opções do projetor").setItems(options,(d,n)->{
            if(n==0) new AlertDialog.Builder(this).setTitle("Margem em todos os lados").setItems(new String[]{"0% — imagem inteira","3% — padrão","5% — bordas cortadas","8% — bordas muito cortadas"},(a,b)->{ store.prefs.edit().putInt("margin",new int[]{0,3,5,8}[b]).apply(); home(); }).show();
            if(n==1) { boolean light=!store.prefs.getBoolean("light",false); store.prefs.edit().putBoolean("light",light).apply(); Ui.dialog(this,"Qualidade de exibição",light?"Modo leve ativado. Menor uso de memória e processamento.":"Qualidade normal ativada. Renderização com até 1600 pixels de largura."); }
            if(n==2) Ui.dialog(this,"Formatos offline","PDF • PPTX/PPSX/POTX • PPTM/PPSM/POTM • PPT/PPS/POT • ODP/OTP/FODP • JPG/JPEG/PNG/WEBP/BMP/GIF/SVG • ZIP/CBZ de imagens • TXT/MD.\n\nPowerPoint e OpenDocument usam leitura estática. Fontes, efeitos, SmartArt, gráficos, vídeos e objetos complexos podem mudar ou não aparecer. PPT antigo tem leitura básica. GIF mostra imagem estática. Macros não são executadas. Arquivos com senha e Apple Keynote não são compatíveis.\n\nPara manter exatamente o visual de uma aula, prefira PDF ou ZIP de imagens.");
            if(n==3) Ui.dialog(this,"Slide TD98 · "+BuildConfig.VERSION_NAME,"B5 Soluções · Emanuel Bonfim\nAndroid 5.0 ou superior · sem Google Play\n\nUso permanente, sem assinatura, ativação ou prazo de expiração. Código sob licença MIT. AndroidSVG sob Apache 2.0.\n\nOs arquivos são lidos no projetor e não são enviados a servidores. A internet é usada apenas para buscar e baixar atualizações públicas do GitHub.\n\nAtualizações: "+BuildConfig.UPDATE_REPOSITORY+"\nO Android pede confirmação para instalar uma atualização.");
        }).setNegativeButton("Voltar",null).show();
    }
    @Override public void onBackPressed() { if(browsing!=null) parentFolder(); else new AlertDialog.Builder(this).setTitle("Fechar o Slide?").setNegativeButton("Continuar",null).setPositiveButton("Fechar",(d,n)->finish()).show(); }
    @Override protected void onDestroy() { destroyed=true; worker.shutdown(); if(updater!=null) updater.close(); super.onDestroy(); }
}
