package online.b5solucoes.slide;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class Ui {
    static final int BG=0xff0b1220, PANEL=0xff152033, ACCENT=0xff8be9be, TEXT=0xfff1f5fa, MUTED=0xffa9b6c9;
    static int dp(Context c,float value) { return Math.round(value*c.getResources().getDisplayMetrics().density); }
    static void fullscreen(Activity a) { a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
    static TextView text(Context c,String text,float sp,int color) { TextView t=new TextView(c); t.setText(text); t.setTextSize(sp); t.setTextColor(color); t.setFontFeatureSettings("kern"); return t; }
    static GradientDrawable rounded(Context c,int fill,int stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(c,12)); d.setStroke(dp(c,stroke==ACCENT?3:1),stroke); return d; }
    static StateListDrawable focus(Context c) {
        StateListDrawable d=new StateListDrawable(); d.addState(new int[]{android.R.attr.state_focused},rounded(c,0xff20483e,ACCENT)); d.addState(new int[]{android.R.attr.state_pressed},rounded(c,0xff295a4a,ACCENT)); d.addState(new int[]{},rounded(c,PANEL,0xff324055)); return d;
    }
    static Button button(Context c,String label,Runnable action) {
        Button b=new Button(c); b.setId(View.generateViewId()); b.setText(label); b.setTextSize(17); b.setTextColor(TEXT); b.setAllCaps(false); b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START); b.setPadding(dp(c,20),dp(c,10),dp(c,20),dp(c,10)); b.setBackground(focus(c)); b.setMinHeight(dp(c,54)); b.setMinimumWidth(0); b.setFocusable(true); b.setFocusableInTouchMode(true); b.setOnClickListener(v->action.run()); return b;
    }
    static LinearLayout vertical(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static void gap(LinearLayout l,int dp) { Space s=new Space(l.getContext()); l.addView(s,new LinearLayout.LayoutParams(1,Ui.dp(l.getContext(),dp))); }
    static void dialog(Activity a,String title,String message) { new AlertDialog.Builder(a).setTitle(title).setMessage(message).setPositiveButton("Entendi",null).show(); }
    static String error(Throwable t) { String s=t.getMessage(); return s==null||s.trim().isEmpty()?"Não foi possível ler esse arquivo. Tente a versão PDF.":s; }
}
