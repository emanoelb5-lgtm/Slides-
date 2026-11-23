package online.b5solucoes.slide;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class PlayerActivity extends Activity {
    final ExecutorService worker=Executors.newSingleThreadExecutor(); final Handler handler=new Handler(Looper.getMainLooper());
    final LruCache<Integer,Bitmap> cache=new LruCache<Integer,Bitmap>(16*1024) { protected int sizeOf(Integer key,Bitmap value) { return value.getByteCount()/1024; } };
    LibraryStore store; LibraryStore.Item item; Deck deck; ImageView image; TextView hud; FrameLayout root; View black;
    volatile boolean closed; volatile int generation,previewGeneration; int page,total; boolean auto,paused,menuOpen,blackout; int seconds=15; AlertDialog menu;
    String digits="";
    final Runnable advance=()->{ if(auto&&!paused&&!menuOpen&&!blackout&&deck!=null) { if(page+1<total) show(page+1); else if(store.prefs.getBoolean("loop",false)) show(0); else { auto=false; message("Apresentação concluída"); } } };
    final Runnable hideHud=()->{ if(hud!=null) hud.setVisibility(View.GONE); };
    final Runnable jumpDigits=()->{ try { int n=Integer.parseInt(digits); if(n>=1&&n<=total) show(n-1); else message("Escolha um slide entre 1 e "+total); } catch(Exception ignored) {} digits=""; };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); Ui.fullscreen(this); store=new LibraryStore(this); seconds=store.prefs.getInt("seconds",15);
        root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        image=new ImageView(this); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setContentDescription("Slide apresentado"); image.setFocusable(false);
        int margin=store.prefs.getInt("margin",3); android.util.DisplayMetrics dm=getResources().getDisplayMetrics(); int inset=Math.round(Math.min(dm.widthPixels,dm.heightPixels)*margin/100f);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(-1,-1); ip.setMargins(inset,inset,inset,inset); root.addView(image,ip);
        hud=Ui.text(this,"Preparando apresentação…",16,Ui.TEXT); hud.setBackgroundColor(0xe6142030); hud.setPadding(Ui.dp(this,24),Ui.dp(this,10),Ui.dp(this,24),Ui.dp(this,10));
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL); hp.bottomMargin=Ui.dp(this,18)+inset; root.addView(hud,hp);
        black=new View(this); black.setBackgroundColor(Color.BLACK); black.setVisibility(View.GONE); root.addView(black,new FrameLayout.LayoutParams(-1,-1));
        root.setOnClickListener(v->{ if(blackout) toggleBlack(); else controls(); });
        root.setFocusable(true); root.setFocusableInTouchMode(true); root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setContentView(root); root.requestFocus();
        if(getIntent().getBooleanExtra("demo",false)) { deck=new DemoDeck(); total=deck.size(); show(state==null?0:state.getInt("page",0)); return; }
        String file=getIntent().getStringExtra("file"); item=store.find(file==null?"":file);
        if(item==null) { fail(new IOException("Apresentação não encontrada. Abra o arquivo novamente.")); return; }
        final File source=new File(store.dir,item.file); int restored=state==null?item.page:state.getInt("page",item.page);
        worker.execute(()->{ try { Deck loaded=Deck.open(source); if(closed) { loaded.close(); return; } deck=loaded; runOnUiThread(()->{ if(closed) return; total=loaded.size(); item.count=total; show(Math.max(0,Math.min(total-1,restored))); }); } catch(Exception|OutOfMemoryError e) { runOnUiThread(()->fail(e)); } });
    }
    void fail(Throwable error) { if(closed) return; new AlertDialog.Builder(this).setTitle("Não foi possível apresentar").setMessage(Ui.error(error)).setPositiveButton("Voltar",(d,n)->finish()).setOnCancelListener(d->finish()).show(); }
    void show(int index) {
        if(deck==null||closed) return;
        page=Math.max(0,Math.min(total-1,index)); int token=++generation,requested=page; handler.removeCallbacks(advance);
        if(item!=null) { item.page=page; store.touch(item); }
        message("Slide "+(page+1)+" / "+total+"   ·   OK: opções"+(auto?"   ·   Automático":""));
        Bitmap cached=cache.get(requested);
        if(cached!=null) { image.setImageBitmap(cached); schedule(); return; }
        worker.execute(()->{
            if(closed||token!=generation) return;
            try {
                int max=store.prefs.getBoolean("light",false)||Runtime.getRuntime().maxMemory()<96L*1024*1024?960:1600;
                Bitmap bitmap=deck.render(requested,max,Math.round(max*.8f));
                if(closed||token!=generation) return; cache.put(requested,bitmap);
                runOnUiThread(()->{ if(!closed&&token==generation) { image.setImageBitmap(bitmap); schedule(); } });
                // At most one next slide is rendered ahead; long lessons never load all pages.
                try { if(requested+1<total&&cache.get(requested+1)==null&&!closed&&token==generation) {
                    Bitmap next=deck.render(requested+1,max,Math.round(max*.8f));
                    if(!closed&&token==generation) cache.put(requested+1,next);
                } } catch(Exception|OutOfMemoryError ignored) { /* A prefetch failure never hides a valid current slide. */ }
            } catch(Exception|OutOfMemoryError e) { runOnUiThread(()->{ if(!closed&&token==generation) { auto=false; new AlertDialog.Builder(this).setTitle("Falha no slide "+(requested+1)).setMessage(Ui.error(e)+"\n\nTente o modo leve nas opções ou abra a versão PDF.").setPositiveButton("Próximo slide",(d,n)->show(requested+1)).setNegativeButton("Voltar",(d,n)->finish()).show(); } }); }
        });
    }
    void schedule() { handler.removeCallbacks(advance); if(auto&&!paused&&!menuOpen&&!blackout) handler.postDelayed(advance,seconds*1000L); }
    void message(String s) { hud.setText(s); hud.setVisibility(View.VISIBLE); handler.removeCallbacks(hideHud); handler.postDelayed(hideHud,3500); }
    void controls() {
        if(deck==null||menuOpen) return; menuOpen=true; handler.removeCallbacks(advance);
        String[] actions={"Continuar apresentação","Slide anterior","Próximo slide","Visão geral dos slides","Ir para um slide",auto?"Parar apresentação automática":"Apresentação automática","Tela preta / pausa","Sobre este formato","Encerrar apresentação"};
        menu=new AlertDialog.Builder(this).setTitle("Slide "+(page+1)+" de "+total).setItems(actions,(d,n)->{
            switch(n) { case 1: show(page-1); break; case 2: show(page+1); break; case 3: handler.post(this::overview); break; case 4: handler.post(this::jumpDialog); break; case 5: if(auto) { auto=false; message("Modo automático desligado"); } else handler.post(this::automatic); break; case 6: toggleBlack(); break; case 7: handler.post(()->Ui.dialog(this,"Compatibilidade do arquivo",deck.notice().isEmpty()?"PDF com renderização nativa do Android. Setas avançam ou retornam; OK abre o menu. Arquivos protegidos por senha não são aceitos.":deck.notice())); break; case 8: finish(); break; default: break; }
        }).setNegativeButton("Voltar",null).create();
        menu.setOnDismissListener(d->{ menuOpen=false; schedule(); Ui.fullscreen(this); }); menu.show();
    }
    void automatic() {
        int[] times={5,10,15,30,60}; String[] labels={"5 segundos por slide","10 segundos por slide","15 segundos por slide","30 segundos por slide","60 segundos por slide"}; menuOpen=true; handler.removeCallbacks(advance);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Tempo entre os slides").setItems(labels,(dialog,n)->{ seconds=times[n]; store.prefs.edit().putInt("seconds",seconds).apply(); auto=true; message("Automático: "+seconds+" segundos por slide"); }).setNeutralButton(store.prefs.getBoolean("loop",false)?"Repetir: ligado":"Repetir: desligado",null).setNegativeButton("Cancelar",null).create();
        d.setOnDismissListener(x->{ menuOpen=false; schedule(); }); d.show();
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{ boolean repeat=!store.prefs.getBoolean("loop",false); store.prefs.edit().putBoolean("loop",repeat).apply(); d.getButton(AlertDialog.BUTTON_NEUTRAL).setText(repeat?"Repetir: ligado":"Repetir: desligado"); });
    }
    void jumpDialog() {
        menuOpen=true; handler.removeCallbacks(advance);
        NumberPicker picker=new NumberPicker(this); picker.setMinValue(1); picker.setMaxValue(Math.max(1,total)); picker.setValue(page+1); picker.setWrapSelectorWheel(false); picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS); picker.setFocusable(true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Escolha com ↑ e ↓").setView(picker).setPositiveButton("Abrir slide",(x,n)->show(picker.getValue()-1)).setNegativeButton("Cancelar",null).create();
        d.setOnDismissListener(x->{ menuOpen=false; schedule(); }); d.show(); picker.requestFocus();
    }
    void overview() { overview(page); }
    void overview(int selected) {
        menuOpen=true; handler.removeCallbacks(advance);
        final int previewToken=++previewGeneration;
        LinearLayout layout=Ui.vertical(this); layout.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));
        TextView caption=Ui.text(this,"Selecione com as setas e pressione OK",14,Ui.MUTED); layout.addView(caption);
        GridView grid=new GridView(this); grid.setNumColumns(4); grid.setVerticalSpacing(Ui.dp(this,8)); grid.setHorizontalSpacing(Ui.dp(this,8)); grid.setSelector(Ui.focus(this));
        final int batchStart=(selected/12)*12; final int batchEnd=Math.min(total,batchStart+12);
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter() {
            public int getCount() { return batchEnd-batchStart; } public Object getItem(int p) { return p+batchStart; } public long getItemId(int p) { return p+batchStart; }
            public View getView(int at,View reuse,ViewGroup parent) {
                int number=at+batchStart; LinearLayout tile=Ui.vertical(PlayerActivity.this); tile.setPadding(8,5,8,5); tile.setBackground(Ui.rounded(PlayerActivity.this,Ui.PANEL,number==page?Ui.ACCENT:0xff324055));
                ImageView thumb=new ImageView(PlayerActivity.this); thumb.setScaleType(ImageView.ScaleType.FIT_CENTER); tile.addView(thumb,new LinearLayout.LayoutParams(-1,Ui.dp(PlayerActivity.this,62)));
                TextView label=Ui.text(PlayerActivity.this,"Slide "+(number+1),13,Ui.TEXT); label.setGravity(Gravity.CENTER); tile.addView(label);
                worker.execute(()->{ if(closed||previewToken!=previewGeneration) return; try { Bitmap t=deck.render(number,240,140); runOnUiThread(()->{ if(!closed&&previewToken==previewGeneration) thumb.setImageBitmap(t); }); } catch(Exception|OutOfMemoryError ignored) {} });
                return tile;
            }
        }; grid.setAdapter(adapter);
        int screenDp=Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);
        layout.addView(grid,new LinearLayout.LayoutParams(-1,Ui.dp(this,Math.max(90,Math.min(280,screenDp-160)))));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Visão geral · "+(batchStart+1)+"–"+batchEnd+" / "+total).setView(layout).setNegativeButton("Voltar",null).setNeutralButton("12 anteriores",null).setPositiveButton("12 seguintes",null).create();
        grid.setOnItemClickListener((p,v,n,id)->{ d.dismiss(); show(batchStart+n); });
        d.setOnDismissListener(x->{ previewGeneration++; menuOpen=false; schedule(); }); d.show(); grid.requestFocus(); grid.setSelection(selected-batchStart);
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(batchStart>0); d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(batchEnd<total);
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{ d.dismiss(); overview(Math.max(0,batchStart-12)); });
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ d.dismiss(); overview(batchEnd); });
    }
    void toggleBlack() { blackout=!blackout; black.setVisibility(blackout?View.VISIBLE:View.GONE); if(blackout) handler.removeCallbacks(advance); else { message("Slide "+(page+1)+" / "+total); schedule(); } }
    @Override public boolean onKeyDown(int key,KeyEvent e) {
        if(blackout) { if(e.getRepeatCount()==0) toggleBlack(); return true; }
        if(key>=KeyEvent.KEYCODE_0&&key<=KeyEvent.KEYCODE_9) { if(digits.length()<5) digits+=key-KeyEvent.KEYCODE_0; handler.removeCallbacks(jumpDigits); message("Ir para o slide "+digits); handler.postDelayed(jumpDigits,1600); return true; }
        switch(key) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_PAGE_DOWN: case KeyEvent.KEYCODE_CHANNEL_UP: case KeyEvent.KEYCODE_MEDIA_NEXT: case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_ENTER:
                if(key==KeyEvent.KEYCODE_ENTER) { if(e.getRepeatCount()==0) controls(); } else show(page+1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_PAGE_UP: case KeyEvent.KEYCODE_CHANNEL_DOWN: case KeyEvent.KEYCODE_MEDIA_PREVIOUS: show(page-1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_MENU: if(e.getRepeatCount()==0) controls(); return true;
            case KeyEvent.KEYCODE_B: if(e.getRepeatCount()==0) toggleBlack(); return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: case KeyEvent.KEYCODE_MEDIA_PLAY: case KeyEvent.KEYCODE_MEDIA_PAUSE: if(e.getRepeatCount()==0) { auto=!auto; message(auto?"Automático ligado":"Automático pausado"); schedule(); } return true;
            case KeyEvent.KEYCODE_MOVE_HOME: show(0); return true;
            case KeyEvent.KEYCODE_MOVE_END: show(total-1); return true;
            default: return super.onKeyDown(key,e);
        }
    }
    @Override public void onBackPressed() { if(blackout) toggleBlack(); else { menuOpen=true; handler.removeCallbacks(advance); AlertDialog d=new AlertDialog.Builder(this).setTitle("Encerrar apresentação?").setMessage("Você poderá continuar deste slide depois.").setNegativeButton("Continuar",null).setPositiveButton("Encerrar",(x,n)->finish()).create(); d.setOnDismissListener(x->{ menuOpen=false; schedule(); }); d.show(); } }
    @Override protected void onPause() { paused=true; handler.removeCallbacks(advance); super.onPause(); }
    @Override protected void onResume() { super.onResume(); paused=false; Ui.fullscreen(this); schedule(); }
    @Override protected void onSaveInstanceState(Bundle b) { b.putInt("page",page); super.onSaveInstanceState(b); }
    @Override public void onTrimMemory(int level) { super.onTrimMemory(level); if(level>=TRIM_MEMORY_RUNNING_LOW) cache.evictAll(); }
    @Override protected void onDestroy() { closed=true; generation++; handler.removeCallbacksAndMessages(null); worker.execute(()->{ try { if(deck!=null) deck.close(); } catch(Exception ignored) {} cache.evictAll(); }); worker.shutdown(); super.onDestroy(); }
}

final class DemoDeck implements Deck {
    final String[][] content={{"Pronto para apresentar.","Use → ou ↓ para avançar.\nUse ← ou ↑ para voltar.\nOK abre as opções da apresentação."},{"Controle na mão.","Menu ou OK: opções\nVoltar: encerrar com confirmação\nCH+ / CH−: próximo e anterior\nTeclas numéricas: ir direto a um slide"},{"A tela é toda sua.","Ative Tela preta pelo menu.\nPressione uma tecla para retornar.\nA margem de segurança evita bordas cortadas."},{"No seu ritmo.","Apresente manualmente ou use o modo automático.\nA posição é salva ao trocar de slide.\nAbra seus arquivos para começar."}};
    public int size() { return content.length; }
    public Bitmap render(int p,int mw,int mh) {
        Bitmap b=Render.blank(16f/9,mw,mh,Ui.BG); Canvas c=new Canvas(b); c.scale(b.getWidth()/960f,b.getHeight()/540f);
        Paint accent=new Paint(Paint.ANTI_ALIAS_FLAG); accent.setColor(Ui.ACCENT); c.drawRoundRect(62,68,126,76,4,4,accent);
        TextBlock.drawPlain(c,"SLIDE TD98   /   TESTE DO CONTROLE",new RectF(62,104,898,145),15,Ui.ACCENT,"l","ctr");
        TextBlock.drawPlain(c,content[p][0],new RectF(62,168,898,251),42,Ui.TEXT,"l","ctr");
        TextBlock.drawPlain(c,content[p][1],new RectF(62,274,898,448),25,Ui.MUTED,"l","ctr");
        TextBlock.drawPlain(c,"0"+(p+1)+"  /  04",new RectF(62,475,898,510),14,Ui.ACCENT,"r","ctr"); return b;
    }
    public String notice() { return "Apresentação de teste incluída no aplicativo. Funciona sem internet e sem arquivo externo."; }
    public void close() {}
}
