package online.b5solucoes.slide;

import android.graphics.*;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** Basic MS-PPT static reader. Legacy complex effects are intentionally not executed. */
final class LegacyPptDeck implements Deck {
    final byte[] doc,pictures; final List<Integer> slides=new ArrayList<>(); final List<List<String>> slideTexts=new ArrayList<>();
    final List<byte[]> imageTable=new ArrayList<>(); float width=720,height=540;
    static final class Record {
        final int p,type,len,options;
        Record(byte[] b,int p) { this.p=p; options=CompoundFile.u16(b,p); type=CompoundFile.u16(b,p+2); len=CompoundFile.i32(b,p+4); }
        int start() { return p+8; } int end() { return p+8+len; }
    }
    static List<Record> records(byte[] data,int start,int end) {
        List<Record> out=new ArrayList<>(); end=Math.min(end,data.length);
        for(int p=Math.max(0,start);p+8<=end;) { Record r=new Record(data,p); if(r.len<0||(long)p+8+r.len>end) break; out.add(r); p=r.end(); if(out.size()>30000) break; }
        return out;
    }
    static List<Record> find(byte[] data,int start,int end,int type,int depth) {
        List<Record> out=new ArrayList<>(); if(depth>24) return out;
        for(Record r:records(data,start,end)) { if(r.type==type) out.add(r); if((r.options&15)==15) out.addAll(find(data,r.start(),r.end(),type,depth+1)); }
        return out;
    }
    LegacyPptDeck(File file) throws Exception {
        CompoundFile ole=new CompoundFile(file); doc=ole.stream("PowerPoint Document"); pictures=ole.stream("Pictures");
        if(doc==null) throw new IOException("Arquivo PPT inválido ou protegido por senha");
        Map<Integer,Integer> persists=new HashMap<>();
        byte[] current=ole.stream("Current User"); int edit=current!=null&&current.length>=20?CompoundFile.i32(current,16):0,documentId=1;
        Set<Integer> seen=new HashSet<>();
        while(edit>0&&edit+32<=doc.length&&seen.add(edit)&&seen.size()<10000) {
            Record u=new Record(doc,edit); if(u.type!=4085) break;
            if(seen.size()==1) documentId=CompoundFile.i32(doc,u.start()+16);
            int po=CompoundFile.i32(doc,u.start()+12);
            if(po>0&&po+8<=doc.length) {
                Record ptr=new Record(doc,po);
                for(int p=ptr.start();p+4<=Math.min(ptr.end(),doc.length);) {
                    int desc=CompoundFile.i32(doc,p); p+=4; int id=desc&0xfffff,count=desc>>>20;
                    for(int i=0;i<count&&p+4<=Math.min(ptr.end(),doc.length);i++,p+=4) if(!persists.containsKey(id+i)) persists.put(id+i,CompoundFile.i32(doc,p));
                }
            }
            edit=CompoundFile.i32(doc,u.start()+8);
        }
        Record document=null; Integer dp=persists.get(documentId);
        if(dp!=null&&dp>=0&&dp+8<doc.length) document=new Record(doc,dp);
        if(document==null) { List<Record> ds=find(doc,0,doc.length,1000,0); if(!ds.isEmpty()) document=ds.get(ds.size()-1); }
        if(document!=null) {
            List<Record> atoms=find(doc,document.start(),document.end(),1001,0);
            if(!atoms.isEmpty()&&atoms.get(0).len>=8) { width=CompoundFile.i32(doc,atoms.get(0).start())/8f; height=CompoundFile.i32(doc,atoms.get(0).start()+4)/8f; }
            for(Record list:find(doc,document.start(),document.end(),4080,0)) {
                if((list.options>>>4)!=0) continue;
                List<String> texts=null;
                for(Record r:records(doc,list.start(),list.end())) {
                    if(r.type==1011) {
                        Integer at=persists.get(CompoundFile.i32(doc,r.start()));
                        if(at!=null&&at>=0&&at+8<doc.length) { slides.add(at); texts=new ArrayList<>(); slideTexts.add(texts); } else texts=null;
                    } else if(texts!=null&&(r.type==4000||r.type==4008)) texts.add(text(r));
                }
            }
            for(Record bse:find(doc,document.start(),document.end(),0xf007,0)) {
                byte[] data=null;
                if(bse.len>44) data=picture(doc,bse.start()+36,bse.end());
                if(data==null&&pictures!=null&&bse.len>=36) { int off=CompoundFile.i32(doc,bse.start()+28); if(off>=0&&off+8<=pictures.length) { Record image=new Record(pictures,off); data=picture(pictures,image.start(),Math.min(image.end(),pictures.length)); } }
                imageTable.add(data);
            }
        }
        if(slides.isEmpty()) for(Record s:find(doc,0,doc.length,1006,0)) { slides.add(s.p); slideTexts.add(new ArrayList<>()); }
        if(slides.isEmpty()||slides.size()>5000) throw new IOException("Nenhum slide encontrado nesse PPT. Tente salvar como PPTX ou PDF.");
        if(width<=0||height<=0||width>20000||height>20000) { width=720; height=540; }
    }
    String text(Record r) { return new String(doc,r.start(),r.len,r.type==4000?StandardCharsets.UTF_16LE:Charset.forName("windows-1252")).replace('\r','\n').replace('\u000b','\n').replace("\u0000",""); }
    static byte[] picture(byte[] bytes,int start,int end) {
        end=Math.min(end,bytes.length);
        for(int i=start;i<Math.min(end-8,start+96);i++) {
            if((bytes[i]&255)==0x89&&bytes[i+1]==0x50&&bytes[i+2]==0x4e&&bytes[i+3]==0x47) return Arrays.copyOfRange(bytes,i,end);
            if((bytes[i]&255)==0xff&&(bytes[i+1]&255)==0xd8&&(bytes[i+2]&255)==0xff) return Arrays.copyOfRange(bytes,i,end);
            if(bytes[i]=='B'&&bytes[i+1]=='M') return Arrays.copyOfRange(bytes,i,end);
        }
        return null;
    }
    public int size() { return slides.size(); }
    public String notice() { return "PPT/PPS/POT antigos: leitura básica de textos, imagens e formas. Formatação avançada pode mudar. Para aparência idêntica, exporte para PDF."; }
    public synchronized Bitmap render(int page,int mw,int mh) throws Exception {
        Bitmap b=Render.blank(width/height,mw,mh,Color.WHITE); Canvas c=new Canvas(b); c.scale(b.getWidth()/width,b.getHeight()/height);
        Record slide=new Record(doc,slides.get(page)); List<String> texts=slideTexts.get(page); int drawn=0;
        for(Record shape:find(doc,slide.start(),slide.end(),0xf004,0)) {
            List<Record> children=records(doc,shape.start(),shape.end()); Record anchor=null,sp=null; Map<Integer,Integer> props=new HashMap<>(); String content="";
            for(Record r:children) {
                if(r.type==0xf010||r.type==0xf00f) anchor=r;
                if(r.type==0xf00a) sp=r;
                if(r.type==0xf00b) for(int i=0;i<(r.options>>>4)&&i*6+6<=r.len;i++) { int id=CompoundFile.u16(doc,r.start()+i*6); if((id&0x8000)==0) props.put(id&0x3fff,CompoundFile.i32(doc,r.start()+i*6+2)); }
                if(r.type==0xf00d) {
                    StringBuilder sb=new StringBuilder();
                    for(Record tr:records(doc,r.start(),r.end())) {
                        if(tr.type==4000||tr.type==4008) sb.append(text(tr));
                        if(tr.type==3998&&tr.len>=4) { int index=CompoundFile.i32(doc,tr.start()); if(index>=0&&index<texts.size()) sb.append(texts.get(index)); }
                    }
                    content=sb.toString();
                }
            }
            if(anchor==null||anchor.len<8) continue;
            int a=anchor.start();
            // PPT client anchor stores top, left, right, bottom in master units (1/576 inch).
            RectF r=new RectF(CompoundFile.i32(doc,a+4)/8f,CompoundFile.i32(doc,a)/8f,CompoundFile.i32(doc,a+8)/8f,CompoundFile.i32(doc,a+12)/8f);
            if(anchor.len==8) r.set((short)CompoundFile.u16(doc,a+2)/8f,(short)CompoundFile.u16(doc,a)/8f,(short)CompoundFile.u16(doc,a+4)/8f,(short)CompoundFile.u16(doc,a+6)/8f);
            if(anchor.type==0xf00f) r.set(CompoundFile.i32(doc,a)/8f,CompoundFile.i32(doc,a+4)/8f,CompoundFile.i32(doc,a+8)/8f,CompoundFile.i32(doc,a+12)/8f);
            if(r.width()<=0||r.height()<=0) continue;
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); int shapeType=sp==null?1:sp.options>>>4;
            Integer image=props.get(260);
            if(image!=null&&image>0&&image<=imageTable.size()&&imageTable.get(image-1)!=null) { Render.picture(c,imageTable.get(image-1),r,null); drawn++; }
            else if(props.containsKey(385)) { p.setColor(officeColor(props.get(385))); if(shapeType==3) c.drawOval(r,p); else c.drawRect(r,p); drawn++; }
            if(!content.trim().isEmpty()) { r.inset(4,2); TextBlock.drawPlain(c,content,r,Math.min(24,Math.max(12,r.height()/3)),Color.BLACK,"l","ctr"); drawn++; }
        }
        if(drawn==0) {
            String joined=String.join("\n\n",texts);
            if(joined.trim().isEmpty()) for(Record t:find(doc,slide.start(),slide.end(),4000,0)) joined+=text(t)+"\n";
            if(joined.trim().isEmpty()) joined="Slide "+(page+1)+"\nEste slide contém objetos antigos não compatíveis. Abra a versão PDF ou PPTX.";
            TextBlock.drawPlain(c,joined,new RectF(40,35,width-40,height-35),24,Color.BLACK,"l","ctr");
        }
        return b;
    }
    static int officeColor(int value) { if((value&0xff000000)!=0) return Color.WHITE; return Color.rgb(value&255,(value>>>8)&255,(value>>>16)&255); }
    public void close() {}
}
