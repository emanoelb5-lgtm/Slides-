package online.b5solucoes.slide;

import android.graphics.*;
import org.w3c.dom.*;
import java.io.*;
import java.util.*;

final class OdpDeck implements Deck {
    final PackageReader pack; final Document content,stylesDoc; final List<Element> pages;
    final Map<String,Element> styles=new HashMap<>(),layouts=new HashMap<>(),masters=new HashMap<>();
    OdpDeck(File file) throws Exception {
        pack=Formats.extension(file.getName()).equals("fodp")?null:new PackageReader(file);
        try {
            if(pack==null) { try(InputStream in=new FileInputStream(file)) { content=Xml.parse(Render.read(in,16*1024*1024)); } stylesDoc=null; }
            else { content=pack.xml("content.xml"); stylesDoc=pack.xml("styles.xml"); }
            if(content==null) throw new IOException("OpenDocument inválido ou protegido por senha");
            index(stylesDoc); index(content);
            pages=Xml.all(Xml.first(content,"presentation"),"page");
            if(pages.isEmpty()||pages.size()>5000) throw new IOException("OpenDocument sem slides válidos");
        } catch(Exception e) { if(pack!=null) pack.close(); throw e; }
    }
    void index(Node doc) { for(Element s:Xml.all(doc,"style")) styles.put(Xml.attr(s,"name",""),s); for(Element p:Xml.all(doc,"page-layout")) layouts.put(Xml.attr(p,"name",""),p); for(Element m:Xml.all(doc,"master-page")) masters.put(Xml.attr(m,"name",""),m); }
    public int size() { return pages.size(); }
    public String notice() { return "OpenDocument estático: textos, imagens e formas. Efeitos, vídeos e objetos complexos podem diferir; use PDF para máxima fidelidade."; }
    static float measure(String value,float fallback) {
        try { String v=value.trim().toLowerCase(Locale.ROOT); if(v.endsWith("cm")) return Float.parseFloat(v.substring(0,v.length()-2))*72/2.54f; if(v.endsWith("mm")) return Float.parseFloat(v.substring(0,v.length()-2))*72/25.4f; if(v.endsWith("in")) return Float.parseFloat(v.substring(0,v.length()-2))*72; if(v.endsWith("pt")) return Float.parseFloat(v.substring(0,v.length()-2)); if(v.endsWith("px")) return Float.parseFloat(v.substring(0,v.length()-2))*.75f; return Float.parseFloat(v); } catch(Exception ignored) { return fallback; }
    }
    Map<String,String> properties(String name,int depth) {
        Map<String,String> p=new HashMap<>(); Element s=styles.get(name); if(s==null||depth>12) return p;
        p.putAll(properties(Xml.attr(s,"parent-style-name",""),depth+1));
        for(Element child:Xml.children(s)) { NamedNodeMap at=child.getAttributes(); for(int i=0;i<at.getLength();i++) p.put(Xml.name(at.item(i)),at.item(i).getNodeValue()); }
        return p;
    }
    static int color(String value,int fallback) { try { if(value==null||value.equals("transparent")) return fallback; return Color.parseColor(value); } catch(Exception ignored) { return fallback; } }
    public synchronized Bitmap render(int page,int mw,int mh) throws Exception {
        Element slide=pages.get(page),master=masters.get(Xml.attr(slide,"master-page-name","")),layout=layouts.get(Xml.attr(master,"page-layout-name",""));
        Element props=Xml.first(layout,"page-layout-properties");
        float w=measure(Xml.attr(props,"page-width","28cm"),794),h=measure(Xml.attr(props,"page-height","15.75cm"),446);
        Map<String,String> bg=properties(Xml.attr(slide,"style-name",""),0);
        Bitmap b=Render.blank(w/h,mw,mh,color(bg.get("fill-color"),Color.WHITE)); Canvas c=new Canvas(b); c.scale(b.getWidth()/w,b.getHeight()/h);
        try { if(master!=null) drawTree(c,master,true); drawTree(c,slide,false); } catch(Exception e) { b.recycle(); throw e; }
        return b;
    }
    void drawTree(Canvas c,Element root,boolean template) throws Exception {
        for(Element node:Xml.children(root)) {
            String kind=Xml.name(node);
            if(kind.equals("g")) { drawTree(c,node,template); continue; }
            if(template && (Xml.attr(node,"placeholder","false").equals("true")||!Xml.attr(node,"class","").isEmpty())) continue;
            if(!Arrays.asList("frame","rect","ellipse","line","custom-shape","connector").contains(kind)) continue;
            float x=measure(Xml.attr(node,"x","0"),0),y=measure(Xml.attr(node,"y","0"),0),w=measure(Xml.attr(node,"width","0"),0),h=measure(Xml.attr(node,"height","0"),0);
            RectF r=new RectF(x,y,x+w,y+h); Map<String,String> pr=properties(Xml.attr(node,"style-name",""),0);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); String fill=pr.getOrDefault("fill","none");
            if(kind.equals("line")||kind.equals("connector")) {
                paint.setColor(color(pr.get("stroke-color"),Color.BLACK)); paint.setStrokeWidth(measure(pr.getOrDefault("stroke-width","1pt"),1));
                c.drawLine(measure(Xml.attr(node,"x1","0"),0),measure(Xml.attr(node,"y1","0"),0),measure(Xml.attr(node,"x2","0"),0),measure(Xml.attr(node,"y2","0"),0),paint); continue;
            }
            if(!fill.equals("none")) { paint.setColor(color(pr.get("fill-color"),Color.TRANSPARENT)); if(kind.equals("ellipse")) c.drawOval(r,paint); else c.drawRect(r,paint); }
            if(!pr.getOrDefault("stroke","none").equals("none")) { paint.setStyle(Paint.Style.STROKE); paint.setColor(color(pr.get("stroke-color"),Color.BLACK)); paint.setStrokeWidth(measure(pr.getOrDefault("stroke-width","1pt"),1)); if(kind.equals("ellipse")) c.drawOval(r,paint); else c.drawRect(r,paint); }
            Element image=Xml.child(node,"image");
            if(image!=null) {
                byte[] bytes=null; Element binary=Xml.first(image,"binary-data");
                if(binary!=null) { String data=Xml.text(binary).trim(); if(data.length()<44*1024*1024) bytes=android.util.Base64.decode(data,android.util.Base64.DEFAULT); }
                else if(pack!=null) { String ref=Xml.attr(image,"href",""); if(!ref.isEmpty()) { try { bytes=pack.bytes(Formats.resolve("content.xml",ref),32*1024*1024); } catch(IllegalArgumentException ignored) {} } }
                if(bytes!=null) Render.picture(c,bytes,r,null);
            }
            Element box=Xml.child(node,"text-box"); if(box==null&&!Xml.children(node,"p").isEmpty()) box=node;
            if(box!=null) {
                List<TextBlock.Paragraph> paragraphs=new ArrayList<>();
                for(Element p:Xml.all(box,"p")) {
                    Map<String,String> pp=properties(Xml.attr(p,"style-name",""),0); TextBlock.Paragraph paragraph=new TextBlock.Paragraph(); paragraph.align=pp.getOrDefault("text-align","left");
                    appendText(p,paragraph,pp,0); paragraphs.add(paragraph);
                }
                r.inset(measure(pr.getOrDefault("padding-left","2pt"),2),measure(pr.getOrDefault("padding-top","1pt"),1));
                TextBlock.draw(c,paragraphs,r,pr.getOrDefault("textarea-vertical-align","top"));
            }
        }
    }
    void appendText(Node n,TextBlock.Paragraph p,Map<String,String> props,int depth) {
        if(depth>40) return;
        for(Node ch=n.getFirstChild();ch!=null;ch=ch.getNextSibling()) {
            if(ch.getNodeType()==Node.TEXT_NODE) {
                TextBlock.Run r=new TextBlock.Run(ch.getNodeValue()); r.size=measure(props.getOrDefault("font-size","20pt"),20); r.family=props.getOrDefault("font-family",props.getOrDefault("font-name","sans-serif")); r.color=color(props.get("color"),Color.BLACK); r.bold=props.getOrDefault("font-weight","").equals("bold"); r.italic=props.getOrDefault("font-style","").equals("italic"); p.runs.add(r);
            } else if(ch instanceof Element) {
                Element e=(Element)ch; String type=Xml.name(e);
                if(type.equals("s")) { int count=Math.min(1000,Math.max(1,(int)Xml.num(e,"c",1))); p.runs.add(new TextBlock.Run(String.join("",Collections.nCopies(count," ")))); }
                else if(type.equals("line-break")) p.runs.add(new TextBlock.Run("\n"));
                else { Map<String,String> next=new HashMap<>(props); next.putAll(properties(Xml.attr(e,"style-name",""),0)); appendText(e,p,next,depth+1); }
            }
        }
    }
    public void close() throws IOException { if(pack!=null) pack.close(); }
}
