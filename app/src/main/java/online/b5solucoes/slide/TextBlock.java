package online.b5solucoes.slide;

import android.graphics.*;
import android.text.*;
import android.text.style.*;
import org.w3c.dom.*;
import java.util.*;

final class TextBlock {
    static class Run {
        String text,family="sans-serif"; float size=20; int color=Color.BLACK; boolean bold,italic,underline;
        Run(String s) { text=s; }
    }
    static class Paragraph {
        final List<Run> runs=new ArrayList<>(); String align="l"; boolean bullet; float before=0,after=0;
    }
    private TextBlock() {}
    static void drawPlain(Canvas c,String text,RectF rect,float size,int color,String align,String anchor) {
        Paragraph p=new Paragraph(); p.align=align; Run r=new Run(text); r.size=size; r.color=color; p.runs.add(r);
        draw(c,Collections.singletonList(p),rect,anchor);
    }
    static void draw(Canvas c,List<Paragraph> paragraphs,RectF rect,String anchor) {
        if(rect.width()<=0||rect.height()<=0 || paragraphs.isEmpty()) return;
        // Work at 4x logical points so small text and line wrapping retain sub-pixel precision.
        final float factor=4;
        SpannableStringBuilder text=new SpannableStringBuilder();
        for(Paragraph p:paragraphs) {
            int paragraphStart=text.length();
            if(p.bullet) text.append("•  ");
            for(Run r:p.runs) {
                int start=text.length(); text.append(r.text);
                if(start==text.length()) continue;
                int flags=Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
                text.setSpan(new AbsoluteSizeSpan(Math.max(1,Math.round(r.size*factor))),start,text.length(),flags);
                text.setSpan(new ForegroundColorSpan(r.color),start,text.length(),flags);
                text.setSpan(new TypefaceSpan(normalizeFamily(r.family)),start,text.length(),flags);
                if(r.bold||r.italic) text.setSpan(new StyleSpan((r.bold?Typeface.BOLD:0)|(r.italic?Typeface.ITALIC:0)),start,text.length(),flags);
                if(r.underline) text.setSpan(new UnderlineSpan(),start,text.length(),flags);
            }
            text.append('\n');
            Layout.Alignment a=p.align.equals("ctr")||p.align.equals("center")?Layout.Alignment.ALIGN_CENTER:p.align.equals("r")||p.align.equals("end")||p.align.equals("right")?Layout.Alignment.ALIGN_OPPOSITE:Layout.Alignment.ALIGN_NORMAL;
            text.setSpan(new AlignmentSpan.Standard(a),paragraphStart,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if(text.length()>0) text.delete(text.length()-1,text.length());
        TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG); paint.setTextSize(20*factor); paint.setColor(Color.BLACK);
        int width=Math.max(1,(int)(rect.width()*factor));
        StaticLayout layout=new StaticLayout(text,paint,width,Layout.Alignment.ALIGN_NORMAL,1.05f,0,false);
        float shrink=layout.getHeight()>rect.height()*factor?Math.max(.35f,rect.height()*factor/layout.getHeight()):1;
        if(shrink<1) {
            // Re-layout at a wider logical width then shrink both axes to keep the whole text visible.
            width=Math.max(1,(int)(rect.width()*factor/shrink));
            layout=new StaticLayout(text,paint,width,Layout.Alignment.ALIGN_NORMAL,1.05f,0,false);
        }
        float textHeight=layout.getHeight()/factor*shrink;
        float y=rect.top;
        if(anchor.equals("ctr")||anchor.equals("middle")) y+=Math.max(0,(rect.height()-textHeight)/2);
        else if(anchor.equals("b")||anchor.equals("bottom")) y+=Math.max(0,rect.height()-textHeight);
        c.save(); c.clipRect(rect); c.translate(rect.left,y); c.scale(shrink/factor,shrink/factor); layout.draw(c); c.restore();
    }
    static String normalizeFamily(String s) {
        String lower=s.toLowerCase(Locale.ROOT);
        if(lower.contains("courier")||lower.contains("mono")) return "monospace";
        if(lower.contains("times")||lower.contains("cambria")||lower.contains("serif")&&!lower.contains("sans")) return "serif";
        return "sans-serif";
    }
    static List<Paragraph> powerpoint(Element body,PptxDeck owner,Element inherited) {
        List<Paragraph> out=new ArrayList<>();
        if(body==null) return out;
        for(Element p:Xml.children(body,"p")) {
            Paragraph para=new Paragraph(); Element pp=Xml.child(p,"pPr");
            para.align=Xml.attr(pp,"algn","l");
            para.bullet=Xml.child(pp,"buChar")!=null || Xml.child(pp,"buAutoNum")!=null;
            Element defaults=Xml.child(pp,"defRPr");
            if(defaults==null) defaults=Xml.first(body,"defRPr");
            Element inheritedDefault=Xml.first(inherited,"defRPr");
            for(Element part:Xml.children(p)) {
                String type=Xml.name(part);
                if(type.equals("br")) { para.runs.add(new Run("\n")); continue; }
                if(!type.equals("r")&&!type.equals("fld")) continue;
                Run r=new Run(Xml.text(Xml.child(part,"t")));
                Element rp=Xml.child(part,"rPr");
                r.size=Xml.num(rp,"sz",Xml.num(defaults,"sz",Xml.num(inheritedDefault,"sz",2000)))/100;
                r.bold=Xml.attr(rp,"b",Xml.attr(defaults,"b","0")).equals("1");
                r.italic=Xml.attr(rp,"i",Xml.attr(defaults,"i","0")).equals("1");
                r.underline=!Xml.attr(rp,"u","none").equals("none");
                Element fill=Xml.child(rp,"solidFill"); if(fill==null) fill=Xml.child(defaults,"solidFill"); if(fill==null) fill=Xml.child(inheritedDefault,"solidFill");
                r.color=owner.color(fill,Color.BLACK);
                r.family=Xml.attr(Xml.child(rp,"latin"),"typeface",Xml.attr(Xml.child(defaults,"latin"),"typeface","sans-serif"));
                para.runs.add(r);
            }
            out.add(para);
        }
        return out;
    }
}
