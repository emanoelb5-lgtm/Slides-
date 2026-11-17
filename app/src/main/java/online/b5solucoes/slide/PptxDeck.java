package online.b5solucoes.slide;

import android.graphics.*;
import org.w3c.dom.*;
import java.io.*;
import java.util.*;

/** Canvas-based static Open XML reader; no desktop AWT, Google services or network conversion. */
final class PptxDeck implements Deck {
    final PackageReader pack;
    final List<String> slides=new ArrayList<>();
    final Map<String,Integer> colors=new HashMap<>();
    float width=960,height=540;
    PptxDeck(File file) throws Exception {
        pack=new PackageReader(file);
        try {
            Document presentation=pack.xml("ppt/presentation.xml");
            if(presentation==null) throw new IOException("PowerPoint inválido ou protegido por senha");
            Element sz=Xml.first(presentation,"sldSz"); width=Xml.num(sz,"cx",12192000)/12700f; height=Xml.num(sz,"cy",6858000)/12700f;
            Map<String,String> rel=relations("ppt/presentation.xml");
            for(Element id:Xml.all(presentation,"sldId")) {
                // 'id' and 'r:id' coexist; only the relationship id begins with rId.
                String rid=id.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");
                if(rid.isEmpty()) rid=id.getAttribute("r:id");
                if(rel.containsKey(rid)) slides.add(rel.get(rid));
            }
            if(slides.isEmpty()||slides.size()>5000) throw new IOException("Quantidade de slides inválida");
            colors.put("dk1",Color.BLACK); colors.put("lt1",Color.WHITE); colors.put("dk2",0xff1f497d); colors.put("lt2",0xffeeece1);
            String[] keys={"accent1","accent2","accent3","accent4","accent5","accent6"}; int[] values={0xff4472c4,0xffed7d31,0xffa5a5a5,0xffffc000,0xff5b9bd5,0xff70ad47};
            for(int i=0;i<keys.length;i++) colors.put(keys[i],values[i]);
            Document theme=pack.xml("ppt/theme/theme1.xml");
            Element scheme=Xml.first(theme,"clrScheme");
            for(Element e:Xml.children(scheme)) colors.put(Xml.name(e),color(e,Color.BLACK));
        } catch(Exception e) { pack.close(); throw e; }
    }
    public int size() { return slides.size(); }
    public String notice() { return "PowerPoint estático: animações, vídeos, SmartArt, gráficos e fontes especiais podem diferir. PDF preserva melhor a aparência original. Macros não são executadas."; }
    Map<String,String> relations(String path) throws Exception {
        Map<String,String> out=new HashMap<>(); int slash=path.lastIndexOf('/');
        String relPath=path.substring(0,slash+1)+"_rels/"+path.substring(slash+1)+".rels";
        Document doc=pack.xml(relPath);
        for(Element e:Xml.all(doc,"Relationship")) {
            if(Xml.attr(e,"TargetMode","").equals("External")) continue;
            try { out.put(Xml.attr(e,"Id",""),Formats.resolve(path,Xml.attr(e,"Target",""))); } catch(IllegalArgumentException ignored) {}
        }
        return out;
    }
    String linked(Map<String,String> rel,String folder) { for(String v:rel.values()) if(v.contains("/"+folder+"/")) return v; return null; }
    int color(Element container,int fallback) {
        if(container==null) return fallback;
        Element srgb=Xml.first(container,"srgbClr"),system=Xml.first(container,"sysClr"),scheme=Xml.first(container,"schemeClr");
        int result=fallback;
        try {
            if(srgb!=null) result=Color.parseColor("#"+Xml.attr(srgb,"val","000000"));
            else if(system!=null) result=Color.parseColor("#"+Xml.attr(system,"lastClr","000000"));
            else if(scheme!=null) {
                String key=Xml.attr(scheme,"val","dk1");
                if(key.equals("tx1")) key="dk1"; if(key.equals("bg1")) key="lt1"; if(key.equals("tx2")) key="dk2"; if(key.equals("bg2")) key="lt2";
                result=colors.getOrDefault(key,fallback);
            }
            Element actual=srgb!=null?srgb:system!=null?system:scheme;
            for(Element mod:Xml.children(actual)) {
                float f=Xml.num(mod,"val",100000)/100000f; int r=Color.red(result),g=Color.green(result),b=Color.blue(result);
                switch(Xml.name(mod)) {
                    case "alpha": result=(Math.round(255*f)<<24)|(result&0xffffff); break;
                    case "lumMod": result=Color.argb(Color.alpha(result),clamp(r*f),clamp(g*f),clamp(b*f)); break;
                    case "lumOff": result=Color.argb(Color.alpha(result),clamp(r+255*f),clamp(g+255*f),clamp(b+255*f)); break;
                    case "tint": result=Color.argb(Color.alpha(result),clamp(r+(255-r)*f),clamp(g+(255-g)*f),clamp(b+(255-b)*f)); break;
                    case "shade": result=Color.argb(Color.alpha(result),clamp(r*f),clamp(g*f),clamp(b*f)); break;
                }
            }
        } catch(Exception ignored) {}
        return result;
    }
    static int clamp(float x) { return Math.max(0,Math.min(255,Math.round(x))); }
    @Override public synchronized Bitmap render(int page,int mw,int mh) throws Exception {
        String path=slides.get(page); Document slide=pack.xml(path); Map<String,String> sr=relations(path);
        String lp=linked(sr,"slideLayouts"); Document layout=lp==null?null:pack.xml(lp); Map<String,String> lr=lp==null?Collections.emptyMap():relations(lp);
        String mp=linked(lr,"slideMasters"); Document master=mp==null?null:pack.xml(mp); Map<String,String> mr=mp==null?Collections.emptyMap():relations(mp);
        Bitmap bitmap=Render.blank(width/height,mw,mh,Color.WHITE); Canvas c=new Canvas(bitmap); c.scale(bitmap.getWidth()/width,bitmap.getHeight()/height);
        try {
            Element bg=Xml.first(slide,"bg"); if(bg==null) bg=Xml.first(layout,"bg"); if(bg==null) bg=Xml.first(master,"bg");
            if(bg!=null) {
                c.drawColor(color(Xml.first(bg,"solidFill"),Color.WHITE));
                Element blip=Xml.first(bg,"blip");
                Map<String,String> br=Xml.first(slide,"bg")!=null?sr:Xml.first(layout,"bg")!=null?lr:mr;
                if(blip!=null) image(c,blip,new RectF(0,0,width,height),br,null);
            }
            if(!Xml.attr(slide.getDocumentElement(),"showMasterSp","1").equals("0")) renderTree(c,Xml.first(master,"spTree"),mr,null,null,true);
            renderTree(c,Xml.first(layout,"spTree"),lr,null,null,true);
            renderTree(c,Xml.first(slide,"spTree"),sr,layout,master,false);
        } catch(Exception e) { bitmap.recycle(); throw e; }
        return bitmap;
    }
    Element inherited(Element shape,Node parent) {
        Element ph=Xml.first(shape,"ph"); if(ph==null||parent==null) return null;
        String idx=Xml.attr(ph,"idx","0"),type=Xml.attr(ph,"type","obj");
        Element typeMatch=null;
        for(Element s:Xml.all(parent,"sp")) { Element p=Xml.first(s,"ph"); if(p==null) continue; if(Xml.attr(p,"idx","0").equals(idx)) return s; if(Xml.attr(p,"type","obj").equals(type)) typeMatch=s; }
        return typeMatch;
    }
    void renderTree(Canvas c,Element tree,Map<String,String> rel,Node layout,Node master,boolean template) throws Exception {
        if(tree==null) return;
        for(Element node:Xml.children(tree)) {
            String kind=Xml.name(node);
            if(kind.equals("grpSp")) {
                Element tr=Xml.first(Xml.child(node,"grpSpPr"),"xfrm");
                Element off=Xml.child(tr,"off"),ext=Xml.child(tr,"ext"),co=Xml.child(tr,"chOff"),ce=Xml.child(tr,"chExt");
                float cw=Xml.num(ce,"cx",1),ch=Xml.num(ce,"cy",1);
                c.save();
                c.translate(Xml.num(off,"x",0)/12700,Xml.num(off,"y",0)/12700);
                if(cw!=0&&ch!=0) c.scale(Xml.num(ext,"cx",cw)/cw,Xml.num(ext,"cy",ch)/ch);
                c.translate(-Xml.num(co,"x",0)/12700,-Xml.num(co,"y",0)/12700);
                renderTree(c,node,rel,layout,master,template); c.restore(); continue;
            }
            if(!Arrays.asList("sp","pic","graphicFrame","cxnSp").contains(kind)) continue;
            if(template&&Xml.first(node,"ph")!=null) continue;
            Element inheritance=inherited(node,layout); if(inheritance==null) inheritance=inherited(node,master);
            Element pr=Xml.child(node,"spPr");
            Element xfrm=Xml.child(pr,"xfrm"); if(xfrm==null) xfrm=Xml.child(node,"xfrm"); if(xfrm==null) xfrm=Xml.first(inheritance,"xfrm");
            if(xfrm==null) continue;
            Element off=Xml.child(xfrm,"off"),ext=Xml.child(xfrm,"ext");
            float x=Xml.num(off,"x",0)/12700,y=Xml.num(off,"y",0)/12700,w=Xml.num(ext,"cx",0)/12700,h=Xml.num(ext,"cy",0)/12700;
            if(w==0&&h==0) continue;
            RectF r=new RectF(x,y,x+w,y+h); c.save();
            c.rotate(Xml.num(xfrm,"rot",0)/60000,r.centerX(),r.centerY());
            if(Xml.attr(xfrm,"flipH","0").equals("1")) c.scale(-1,1,r.centerX(),r.centerY());
            if(Xml.attr(xfrm,"flipV","0").equals("1")) c.scale(1,-1,r.centerX(),r.centerY());
            try {
                if(kind.equals("pic")) {
                    Element crop=Xml.first(node,"srcRect"); RectF cr=crop==null?null:new RectF(Xml.num(crop,"l",0)/100000,Xml.num(crop,"t",0)/100000,Xml.num(crop,"r",0)/100000,Xml.num(crop,"b",0)/100000);
                    image(c,Xml.first(node,"blip"),r,rel,cr);
                } else if(kind.equals("graphicFrame")) renderTable(c,Xml.first(node,"tbl"),r);
                else {
                    drawShape(c,pr,r,kind.equals("cxnSp"));
                    Element fill=Xml.child(pr,"blipFill"); if(fill!=null) image(c,Xml.first(fill,"blip"),r,rel,null);
                    Element body=Xml.child(node,"txBody"),bp=Xml.child(body,"bodyPr");
                    RectF tr=new RectF(r.left+Xml.num(bp,"lIns",91440)/12700,r.top+Xml.num(bp,"tIns",45720)/12700,r.right-Xml.num(bp,"rIns",91440)/12700,r.bottom-Xml.num(bp,"bIns",45720)/12700);
                    TextBlock.draw(c,TextBlock.powerpoint(body,this,inheritance),tr,Xml.attr(bp,"anchor","t"));
                }
            } finally { c.restore(); }
        }
    }
    void image(Canvas c,Element blip,RectF r,Map<String,String> rel,RectF crop) throws Exception {
        if(blip==null) return;
        String rid=Xml.attr(blip,"embed",""); Element svg=Xml.first(blip,"svgBlip");
        if(svg!=null) rid=Xml.attr(svg,"embed",rid);
        String file=rel.get(rid); if(file==null) return;
        Render.picture(c,pack.bytes(file,32*1024*1024),r,crop);
    }
    void drawShape(Canvas c,Element pr,RectF rect,boolean connector) {
        String preset=Xml.attr(Xml.child(pr,"prstGeom"),"prst",connector?"line":"rect");
        Path path=new Path();
        switch(preset) {
            case "ellipse": path.addOval(rect,Path.Direction.CW); break;
            case "roundRect": path.addRoundRect(rect,Math.min(rect.width(),rect.height())*.12f,Math.min(rect.width(),rect.height())*.12f,Path.Direction.CW); break;
            case "triangle": path.moveTo(rect.centerX(),rect.top); path.lineTo(rect.right,rect.bottom); path.lineTo(rect.left,rect.bottom); path.close(); break;
            case "line": path.moveTo(rect.left,rect.top); path.lineTo(rect.right,rect.bottom); break;
            default: path.addRect(rect,Path.Direction.CW);
        }
        Element fill=Xml.child(pr,"solidFill"),grad=Xml.child(pr,"gradFill");
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        if(fill!=null&&!preset.equals("line")) { paint.setColor(color(fill,Color.TRANSPARENT)); c.drawPath(path,paint); }
        else if(grad!=null) {
            List<Element> stops=Xml.all(grad,"gs"); if(!stops.isEmpty()) { paint.setShader(new LinearGradient(rect.left,rect.top,rect.right,rect.bottom,color(stops.get(0),Color.WHITE),color(stops.get(stops.size()-1),Color.BLACK),Shader.TileMode.CLAMP)); c.drawPath(path,paint); paint.setShader(null); }
        }
        Element line=Xml.child(pr,"ln");
        if((line!=null&&Xml.child(line,"noFill")==null)||preset.equals("line")) { paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Xml.num(line,"w",12700)/12700); paint.setColor(color(Xml.child(line,"solidFill"),Color.BLACK)); c.drawPath(path,paint); }
    }
    void renderTable(Canvas c,Element table,RectF r) {
        if(table==null) return;
        List<Element> rows=Xml.children(table,"tr"),cols=Xml.children(Xml.child(table,"tblGrid"),"gridCol");
        float totalWidth=0,totalHeight=0; for(Element col:cols) totalWidth+=Xml.num(col,"w",1); for(Element row:rows) totalHeight+=Xml.num(row,"h",1);
        if(totalWidth<=0||totalHeight<=0) return;
        float y=r.top; Paint border=new Paint(Paint.ANTI_ALIAS_FLAG); border.setColor(0xff9ca3af); border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(.6f);
        for(Element row:rows) {
            float h=r.height()*Xml.num(row,"h",1)/totalHeight,x=r.left; List<Element> cells=Xml.children(row,"tc");
            for(int i=0;i<cells.size();i++) {
                float w=r.width()*(i<cols.size()?Xml.num(cols.get(i),"w",1)/totalWidth:1f/Math.max(1,cells.size()));
                RectF cell=new RectF(x,y,x+w,y+h); Element tc=cells.get(i); Paint fill=new Paint(); fill.setColor(color(Xml.child(Xml.child(tc,"tcPr"),"solidFill"),Color.WHITE)); c.drawRect(cell,fill); c.drawRect(cell,border);
                cell.inset(3,2); TextBlock.draw(c,TextBlock.powerpoint(Xml.child(tc,"txBody"),this,null),cell,"ctr"); x+=w;
            }
            y+=h;
        }
    }
    public void close() throws IOException { pack.close(); }
}
