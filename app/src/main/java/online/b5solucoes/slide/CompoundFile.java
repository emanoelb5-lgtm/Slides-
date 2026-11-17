package online.b5solucoes.slide;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded reader for OLE compound streams (MS-CFB), used only for legacy .ppt. */
final class CompoundFile {
    final byte[] bytes; final int sectorSize,miniSize,cutoff; final int[] fat,miniFat; final byte[] miniStream;
    final Map<String,Entry> entries=new HashMap<>();
    static final class Entry { final int sector; final long size; Entry(int s,long n) { sector=s; size=n; } }
    CompoundFile(File file) throws IOException {
        try(InputStream in=new FileInputStream(file)) { bytes=Render.read(in,96*1024*1024); }
        byte[] magic={(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1};
        if(bytes.length<512||!Arrays.equals(Arrays.copyOf(bytes,8),magic)) throw new IOException("PowerPoint antigo inválido ou protegido por senha");
        sectorSize=1<<u16(bytes,30); miniSize=1<<u16(bytes,32); cutoff=i32(bytes,56);
        if((sectorSize!=512&&sectorSize!=4096)||miniSize!=64||cutoff!=4096) throw new IOException("Estrutura OLE não suportada");
        List<Integer> fatSectors=new ArrayList<>(); int numFat=i32(bytes,44);
        if(numFat<0||numFat>bytes.length/sectorSize) throw new IOException("Tabela OLE inválida");
        for(int i=0;i<109;i++) { int s=i32(bytes,76+i*4); if(s>=0) fatSectors.add(s); }
        int difat=i32(bytes,68),difatCount=i32(bytes,72); Set<Integer> visited=new HashSet<>();
        for(int i=0;i<difatCount&&difat>=0;i++) {
            if(!visited.add(difat)||visited.size()>bytes.length/sectorSize) throw new IOException("Ciclo na tabela OLE");
            int p=sectorOffset(difat);
            for(int j=0;j<sectorSize/4-1;j++) { int s=i32(bytes,p+j*4); if(s>=0) fatSectors.add(s); }
            difat=i32(bytes,p+sectorSize-4);
        }
        if(fatSectors.size()<numFat) throw new IOException("Tabela OLE incompleta");
        fat=new int[numFat*(sectorSize/4)];
        for(int i=0;i<numFat;i++) { int pos=sectorOffset(fatSectors.get(i)); for(int j=0;j<sectorSize/4;j++) fat[i*sectorSize/4+j]=i32(bytes,pos+j*4); }
        byte[] directory=chain(i32(bytes,48),-1,fat,sectorSize,null); Entry root=null;
        for(int p=0;p+128<=directory.length;p+=128) {
            int len=u16(directory,p+64),type=directory[p+66]&255;
            if(type!=2&&type!=5||len<2||len>64) continue;
            String name=new String(directory,p,len-2,StandardCharsets.UTF_16LE);
            long size=Integer.toUnsignedLong(i32(directory,p+120));
            if(sectorSize==4096) size|=(long)i32(directory,p+124)<<32;
            Entry e=new Entry(i32(directory,p+116),size);
            if(type==5) root=e; else entries.put(name,e);
        }
        miniStream=root==null?new byte[0]:chain(root.sector,root.size,fat,sectorSize,null);
        byte[] mf=chain(i32(bytes,60),(long)Math.max(0,i32(bytes,64))*sectorSize,fat,sectorSize,null);
        miniFat=new int[mf.length/4]; for(int i=0;i<miniFat.length;i++) miniFat[i]=i32(mf,i*4);
    }
    int sectorOffset(int sector) throws IOException { long p=((long)sector+1)*sectorSize; if(sector<0||p<0||p+sectorSize>bytes.length) throw new IOException("Setor OLE fora do arquivo"); return (int)p; }
    byte[] stream(String name) throws IOException { Entry e=entries.get(name); if(e==null) return null; return chain(e.sector,e.size,e.size<cutoff?miniFat:fat,e.size<cutoff?miniSize:sectorSize,e.size<cutoff?miniStream:null); }
    byte[] chain(int sector,long expected,int[] table,int size,byte[] mini) throws IOException {
        if(expected>96*1024*1024||expected< -1) throw new IOException("Stream OLE excessivo");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); Set<Integer> seen=new HashSet<>();
        while(sector>=0&&(expected<0||out.size()<expected)) {
            if(sector>=table.length||!seen.add(sector)) throw new IOException("Cadeia OLE inválida");
            int pos=mini==null?sectorOffset(sector):sector*size; byte[] source=mini==null?bytes:mini;
            int len=(int)Math.min(size,expected<0?size:expected-out.size());
            if(pos<0||pos+len>source.length||out.size()+len>96*1024*1024) throw new IOException("Stream OLE inválido");
            out.write(source,pos,len); sector=table[sector];
        }
        if(expected>=0&&out.size()!=expected) throw new IOException("Stream OLE truncado");
        return out.toByteArray();
    }
    static int u16(byte[] b,int p) { if(p<0||p+2>b.length) return 0; return (b[p]&255)|((b[p+1]&255)<<8); }
    static int i32(byte[] b,int p) { if(p<0||p+4>b.length) return 0; return (b[p]&255)|((b[p+1]&255)<<8)|((b[p+2]&255)<<16)|(b[p+3]<<24); }
}
