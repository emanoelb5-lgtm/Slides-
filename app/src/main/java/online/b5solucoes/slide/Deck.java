package online.b5solucoes.slide;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.caverock.androidsvg.SVG;
import java.io.*;
import java.util.*;
import java.util.zip.*;

interface Deck extends AutoCloseable {
    int size();
    Bitmap render(int page,int maxWidth,int maxHeight) throws Exception;
    default String notice() { return ""; }
    @Override void close() throws IOException;
    static Deck open(File file) throws Exception {
        switch(Formats.kind(file.getName())) {
            case PDF: return new PdfDeck(file);
            case POWERPOINT: return new PptxDeck(file);
            case OPENDOCUMENT: return new OdpDeck(file);
            case LEGACY_POWERPOINT: return new LegacyPptDeck(file);
            case IMAGES: return new ImageDeck(file,true);
            case IMAGE: return new ImageDeck(file,false);
            case TEXT: return new TextDeck(file);
            default: throw new IOException("Formato não reconhecido. Use PDF, PPTX, PPT, ODP, imagens ou ZIP de imagens.");
        }
    }
}

final class Render {
    static Bitmap blank(float ratio,int mw,int mh,int color) {
        ratio=Float.isFinite(ratio)&&ratio>0?ratio:16f/9;
        int w=Math.max(1,Math.min(mw,Math.round(mh*ratio))),h=Math.max(1,Math.round(w/ratio));
        if((long)w*h>5_000_000) throw new IllegalArgumentException("Resolução excessiva");
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); b.eraseColor(color); return b;
    }
    static Bitmap decode(byte[] bytes,int w,int h) throws Exception {
        if(bytes==null || bytes.length==0) return null;
        if(isSvg(bytes)) {
            SVG svg=SVG.getFromInputStream(new ByteArrayInputStream(bytes));
            float sw=svg.getDocumentWidth(),sh=svg.getDocumentHeight();
            RectF vb=svg.getDocumentViewBox();
            if(sw<=0||sh<=0) { sw=vb==null?Math.max(w,1):vb.width(); sh=vb==null?Math.max(h,1):vb.height(); }
            Bitmap b=blank(sw/sh,Math.min(2048,Math.max(1,w)),Math.min(2048,Math.max(1,h)),Color.TRANSPARENT);
            svg.setDocumentWidth(b.getWidth()); svg.setDocumentHeight(b.getHeight()); svg.renderToCanvas(new Canvas(b)); return b;
        }
        BitmapFactory.Options options=new BitmapFactory.Options(); options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0||options.outHeight<=0) return null;
        int sample=1;
        while(options.outWidth/sample>Math.max(1,w)*2 || options.outHeight/sample>Math.max(1,h)*2 || (long)(options.outWidth/sample)*(options.outHeight/sample)>8_000_000) sample*=2;
        options.inSampleSize=sample; options.inJustDecodeBounds=false; options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }
    static boolean isSvg(byte[] bytes) { String s=new String(bytes,0,Math.min(bytes.length,1024),java.nio.charset.StandardCharsets.UTF_8); return s.contains("<svg") || s.contains(":svg"); }
    static byte[] read(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[32768]; int n,total=0;
        while((n=in.read(buf))!=-1) { total+=n; if(total>max) throw new IOException("Conteúdo interno maior que o limite seguro"); out.write(buf,0,n); }
        return out.toByteArray();
    }
    static void picture(Canvas c,byte[] bytes,RectF dest,RectF crop) throws Exception {
        Bitmap image=decode(bytes,Math.max(1,(int)(dest.width()*2)),Math.max(1,(int)(dest.height()*2)));
        if(image==null) return;
        Rect src=new Rect(0,0,image.getWidth(),image.getHeight());
        if(crop!=null) { src.set(Math.round(crop.left*image.getWidth()),Math.round(crop.top*image.getHeight()),Math.round((1-crop.right)*image.getWidth()),Math.round((1-crop.bottom)*image.getHeight())); }
        if(src.width()>0&&src.height()>0) c.drawBitmap(image,src,dest,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
        image.recycle();
    }
}

final class PackageReader implements AutoCloseable {
    final ZipFile zip;
    PackageReader(File f) throws IOException { zip=new ZipFile(f); if(zip.size()>25000) { zip.close(); throw new IOException("Pacote com arquivos internos demais"); } }
    byte[] bytes(String name,int limit) throws IOException {
        ZipEntry e=zip.getEntry(name); if(e==null) return null;
        if(e.getSize()>limit) throw new IOException("Arquivo interno muito grande: "+name);
        try(InputStream in=zip.getInputStream(e)) { return Render.read(in,limit); }
    }
    org.w3c.dom.Document xml(String name) throws Exception { byte[] b=bytes(name,8*1024*1024); return b==null?null:Xml.parse(b); }
    @Override public void close() throws IOException { zip.close(); }
}

final class PdfDeck implements Deck {
    final ParcelFileDescriptor fd; final PdfRenderer renderer;
    PdfDeck(File file) throws IOException {
        fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
        try { renderer=new PdfRenderer(fd); } catch(Exception e) { fd.close(); throw e; }
        if(renderer.getPageCount()==0) { close(); throw new IOException("PDF sem páginas"); }
    }
    public int size() { return renderer.getPageCount(); }
    public synchronized Bitmap render(int page,int w,int h) {
        try(PdfRenderer.Page p=renderer.openPage(page)) { Bitmap b=Render.blank((float)p.getWidth()/p.getHeight(),w,h,Color.WHITE); p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); return b; }
    }
    public synchronized void close() throws IOException { renderer.close(); fd.close(); }
}

final class ImageDeck implements Deck {
    final File file; final PackageReader pack; final List<String> entries=new ArrayList<>();
    ImageDeck(File f,boolean zipped) throws IOException {
        file=f; pack=zipped?new PackageReader(f):null;
        if(zipped) {
            Enumeration<? extends ZipEntry> it=pack.zip.entries();
            while(it.hasMoreElements()) { ZipEntry e=it.nextElement(); if(!e.isDirectory() && !e.getName().startsWith("__MACOSX/") && Formats.kind(e.getName())==Formats.Kind.IMAGE) entries.add(e.getName()); }
            entries.sort(Formats::naturalCompare);
            if(entries.isEmpty()) { pack.close(); throw new IOException("Esse ZIP não contém imagens. Ele deve conter slides em JPG, PNG, WEBP, BMP, GIF ou SVG."); }
        } else entries.add(f.getName());
    }
    public int size() { return entries.size(); }
    public synchronized Bitmap render(int page,int w,int h) throws Exception {
        byte[] data;
        if(pack!=null) data=pack.bytes(entries.get(page),32*1024*1024);
        else try(InputStream in=new FileInputStream(file)) { data=Render.read(in,32*1024*1024); }
        Bitmap image=Render.decode(data,w,h);
        if(image==null) throw new IOException("Imagem não reconhecida: "+entries.get(page));
        return image;
    }
    public String notice() { return "Imagens GIF são mostradas como quadros estáticos. ZIP/CBZ segue a ordem numérica dos nomes."; }
    public void close() throws IOException { if(pack!=null) pack.close(); }
}

final class TextDeck implements Deck {
    final List<String> pages=new ArrayList<>();
    TextDeck(File file) throws IOException {
        String content;
        try(InputStream in=new FileInputStream(file)) { content=new String(Render.read(in,2*1024*1024),java.nio.charset.StandardCharsets.UTF_8); }
        for(String section:content.split("(?m)^\\s*---\\s*$|\\f")) {
            // Paginate long sections instead of silently clipping text.
            String remaining=section.trim();
            while(!remaining.isEmpty()) { int end=Math.min(1000,remaining.length()); if(end<remaining.length()) { int split=remaining.lastIndexOf('\n',end); if(split<end/2) split=remaining.lastIndexOf(' ',end); if(split>0) end=split; } pages.add(remaining.substring(0,end).trim()); remaining=remaining.substring(end).trim(); }
        }
        if(pages.isEmpty()) pages.add("Arquivo de texto vazio");
    }
    public int size() { return pages.size(); }
    public Bitmap render(int page,int w,int h) {
        Bitmap b=Render.blank(16f/9,w,h,Color.rgb(11,18,32)); Canvas c=new Canvas(b); c.scale(b.getWidth()/960f,b.getHeight()/540f);
        TextBlock.drawPlain(c,pages.get(page),new RectF(64,48,896,492),30,Color.WHITE,"l","ctr"); return b;
    }
    public void close() {}
}
