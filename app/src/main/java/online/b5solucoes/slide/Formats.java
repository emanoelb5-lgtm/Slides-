package online.b5solucoes.slide;

import java.util.*;

/** File types are checked from the display name as vendor pickers often use application/octet-stream. */
public final class Formats {
    public enum Kind { PDF, POWERPOINT, LEGACY_POWERPOINT, OPENDOCUMENT, IMAGES, IMAGE, TEXT, UNKNOWN }
    private Formats() {}
    public static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
    public static Kind kind(String name) {
        switch(extension(name)) {
            case "pdf": return Kind.PDF;
            case "pptx": case "ppsx": case "potx": case "pptm": case "ppsm": case "potm": return Kind.POWERPOINT;
            case "ppt": case "pps": case "pot": return Kind.LEGACY_POWERPOINT;
            case "odp": case "otp": case "fodp": return Kind.OPENDOCUMENT;
            case "zip": case "cbz": return Kind.IMAGES;
            case "jpg": case "jpeg": case "png": case "webp": case "bmp": case "gif": case "svg": return Kind.IMAGE;
            case "txt": case "md": return Kind.TEXT;
            default: return Kind.UNKNOWN;
        }
    }
    public static boolean supported(String name) { return kind(name) != Kind.UNKNOWN; }
    public static int naturalCompare(String left, String right) {
        int a=0,b=0;
        while(a<left.length() && b<right.length()) {
            char x=Character.toLowerCase(left.charAt(a)), y=Character.toLowerCase(right.charAt(b));
            if(Character.isDigit(x) && Character.isDigit(y)) {
                int ae=a,be=b;
                while(ae<left.length() && Character.isDigit(left.charAt(ae))) ae++;
                while(be<right.length() && Character.isDigit(right.charAt(be))) be++;
                String an=left.substring(a,ae).replaceFirst("^0+(?!$)",""), bn=right.substring(b,be).replaceFirst("^0+(?!$)","");
                int c=Integer.compare(an.length(),bn.length());
                if(c==0) c=an.compareTo(bn);
                if(c!=0) return c;
                a=ae; b=be;
            } else { if(x!=y) return x-y; a++; b++; }
        }
        return Integer.compare(left.length(),right.length());
    }
    /** Normalizes an internal package relationship, never an OS path. */
    public static String resolve(String source, String target) {
        if(target==null || target.isEmpty() || target.indexOf('\\')>=0 || target.contains(":") || target.contains("\u0000"))
            throw new IllegalArgumentException("Referência externa ou inválida no arquivo");
        String joined=target.startsWith("/") ? target.substring(1) : source.substring(0,Math.max(0,source.lastIndexOf('/')+1))+target;
        Deque<String> parts=new ArrayDeque<>();
        for(String part:joined.split("/")) {
            if(part.isEmpty()||part.equals(".")) continue;
            if(part.equals("..")) { if(parts.isEmpty()) throw new IllegalArgumentException("Referência fora do pacote"); parts.removeLast(); }
            else parts.addLast(part);
        }
        return String.join("/",parts);
    }
}
