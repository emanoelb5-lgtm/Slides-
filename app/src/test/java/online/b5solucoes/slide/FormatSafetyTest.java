package online.b5solucoes.slide;

import org.junit.Test;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class FormatSafetyTest {
    @Test public void projectorFileNamesAreCaseInsensitiveAndNumericallyOrdered() {
        List<String> names=new ArrayList<>(Arrays.asList("Slide_100.JPG","Slide_2.jpg","Slide_01.png","Slide_10.webp")); names.sort(Formats::naturalCompare);
        assertEquals(Arrays.asList("Slide_01.png","Slide_2.jpg","Slide_10.webp","Slide_100.JPG"),names);
        assertEquals(Formats.Kind.POWERPOINT,Formats.kind("AULA.PPSX")); assertEquals(Formats.Kind.LEGACY_POWERPOINT,Formats.kind("AULA.PPT"));
    }
    @Test public void internalImagesResolveWithoutEscapingArchiveRoot() {
        assertEquals("ppt/media/image1.png",Formats.resolve("ppt/slides/slide1.xml","../media/image1.png"));
        assertEquals("Pictures/image.png",Formats.resolve("content.xml","./Pictures/image.png"));
        for(String value:new String[]{"../../../../outside", "https://evil.invalid/a", "file:///secret", "..\\secret", "\u0000"}) {
            try { Formats.resolve("ppt/slides/slide1.xml",value); fail("External or escaping reference accepted"); } catch(IllegalArgumentException expected) {}
        }
    }
    @Test public void updateDownloadsRejectHttpAndUnrelatedServers() throws Exception {
        assertEquals("github.com",UpdateManager.trusted("https://github.com/emanoelb5-lgtm/Slides-/releases").getHost());
        for(String value:new String[]{"http://github.com/a.apk","https://github.com.attacker.invalid/a.apk","https://user:password@github.com/a.apk","https://github.com:444/a.apk"}) {
            try { UpdateManager.trusted(value); fail("Untrusted download URL accepted"); } catch(IOException expected) {}
        }
    }
}
