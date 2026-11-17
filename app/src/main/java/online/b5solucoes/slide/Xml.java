package online.b5solucoes.slide;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

final class Xml {
    static Document parse(byte[] data) throws Exception {
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true); f.setExpandEntityReferences(false);
        // Android parsers differ; the lexical rejection also applies on firmware without these flags.
        String prefix=new String(data,java.nio.charset.StandardCharsets.UTF_8);
        if(prefix.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || prefix.toUpperCase(Locale.ROOT).contains("<!ENTITY"))
            throw new IOException("XML com entidades externas não é aceito");
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); } catch(ParserConfigurationException ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities",false); } catch(ParserConfigurationException ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities",false); } catch(ParserConfigurationException ignored) {}
        DocumentBuilder b=f.newDocumentBuilder();
        b.setEntityResolver((publicId,systemId)->new org.xml.sax.InputSource(new StringReader("")));
        return b.parse(new ByteArrayInputStream(data));
    }
    static String name(Node n) { return n.getLocalName()!=null ? n.getLocalName() : n.getNodeName().replaceFirst("^.*:",""); }
    static String attr(Element e,String name,String fallback) {
        if(e==null) return fallback;
        if(e.hasAttribute(name)) return e.getAttribute(name);
        NamedNodeMap attrs=e.getAttributes();
        for(int i=0;i<attrs.getLength();i++) if(name(attrs.item(i)).equals(name)) return attrs.item(i).getNodeValue();
        return fallback;
    }
    static float num(Element e,String name,float fallback) { try { return Float.parseFloat(attr(e,name,"")); } catch(Exception ignored) { return fallback; } }
    static List<Element> children(Node n) {
        List<Element> out=new ArrayList<>();
        if(n!=null) for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling()) if(c instanceof Element) out.add((Element)c);
        return out;
    }
    static List<Element> children(Node n,String name) { List<Element> out=new ArrayList<>(); for(Element e:children(n)) if(name(e).equals(name)) out.add(e); return out; }
    static Element child(Node n,String name) { for(Element e:children(n)) if(name(e).equals(name)) return e; return null; }
    static Element first(Node n,String name) {
        if(n==null) return null;
        if(n instanceof Element && name(n).equals(name)) return (Element)n;
        for(Element c:children(n)) { Element result=first(c,name); if(result!=null) return result; }
        return null;
    }
    static List<Element> all(Node n,String name) { List<Element> out=new ArrayList<>(); collect(n,name,out); return out; }
    private static void collect(Node n,String name,List<Element> out) { if(n==null) return; if(n instanceof Element && name(n).equals(name)) out.add((Element)n); for(Element c:children(n)) collect(c,name,out); }
    static String text(Node n) { return n==null?"":n.getTextContent(); }
}
