package online.b5solucoes.slide;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProjectorTest {
    final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();
    final Context context=instrument.getTargetContext();
    File temp(String name) { return new File(context.getCacheDir(),name); }
    byte[] image(int color) throws IOException { Bitmap b=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888); b.eraseColor(color); ByteArrayOutputStream out=new ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG,100,out); b.recycle(); return out.toByteArray(); }
    void zip(File file,Map<String,byte[]> entries) throws IOException { try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file))) { for(Map.Entry<String,byte[]> e:entries.entrySet()) { out.putNextEntry(new ZipEntry(e.getKey())); out.write(e.getValue()); out.closeEntry(); } } }
    byte[] xml(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    void assertColor(int color,int actual) { assertEquals(Color.red(color),Color.red(actual),5); assertEquals(Color.green(color),Color.green(actual),5); assertEquals(Color.blue(color),Color.blue(actual),5); }
    @Test public void pdfRendersOfflineAndKeepsPageOrder() throws Exception {
        File f=temp("native.pdf");
        PdfDocument pdf=new PdfDocument();
        try { for(int i=0;i<3;i++) { PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(200,100,i+1).create()); page.getCanvas().drawColor(new int[]{Color.RED,Color.GREEN,Color.BLUE}[i]); pdf.finishPage(page); } try(OutputStream out=new FileOutputStream(f)) { pdf.writeTo(out); } } finally { pdf.close(); }
        try(Deck d=Deck.open(f)) { assertEquals(3,d.size()); Bitmap b=d.render(1,800,600); assertEquals(2f,(float)b.getWidth()/b.getHeight(),.01f); assertColor(Color.GREEN,b.getPixel(400,200)); screenshot(b,"pdf-render.png"); }
    }
    @Test public void powerPointUsesSlideRelationshipsAndEmbeddedImages() throws Exception {
        Map<String,byte[]> entries=new LinkedHashMap<>();
        entries.put("ppt/presentation.xml",xml("<p:presentation xmlns:p='http://schemas.openxmlformats.org/presentationml/2006/main' xmlns:r='http://schemas.openxmlformats.org/officeDocument/2006/relationships'><p:sldIdLst><p:sldId id='999' r:id='rId7'/></p:sldIdLst><p:sldSz cx='9144000' cy='4572000'/></p:presentation>"));
        entries.put("ppt/_rels/presentation.xml.rels",xml("<Relationships><Relationship Id='rId7' Target='slides/slide8.xml'/></Relationships>"));
        entries.put("ppt/slides/_rels/slide8.xml.rels",xml("<Relationships><Relationship Id='rId2' Target='../media/photo.png'/></Relationships>"));
        entries.put("ppt/slides/slide8.xml",xml("<p:sld xmlns:p='p' xmlns:a='a' xmlns:r='r'><p:cSld><p:spTree><p:pic><p:blipFill><a:blip r:embed='rId2'/></p:blipFill><p:spPr><a:xfrm><a:off x='0' y='0'/><a:ext cx='9144000' cy='4572000'/></a:xfrm></p:spPr></p:pic></p:spTree></p:cSld></p:sld>")); entries.put("ppt/media/photo.png",image(Color.rgb(42,155,84)));
        File f=temp("embedded.pptx"); zip(f,entries);
        try(Deck d=Deck.open(f)) { assertEquals(1,d.size()); Bitmap b=d.render(0,1000,800); assertColor(Color.rgb(42,155,84),b.getPixel(500,250)); screenshot(b,"pptx-render.png"); }
    }
    @Test public void openDocumentReadsEmbeddedPictures() throws Exception {
        Map<String,byte[]> e=new LinkedHashMap<>(); e.put("content.xml",xml("<office:document-content xmlns:office='office' xmlns:draw='draw' xmlns:svg='svg' xmlns:xlink='xlink'><office:body><office:presentation><draw:page><draw:frame svg:x='0cm' svg:y='0cm' svg:width='28cm' svg:height='15.75cm'><draw:image xlink:href='Pictures/photo.png'/></draw:frame></draw:page></office:presentation></office:body></office:document-content>")); e.put("Pictures/photo.png",image(Color.MAGENTA)); File f=temp("photo.odp"); zip(f,e);
        try(Deck d=Deck.open(f)) { Bitmap b=d.render(0,960,540); assertColor(Color.MAGENTA,b.getPixel(400,200)); }
    }
    @Test public void imageArchiveOrdersSlidesByNumber() throws Exception {
        Map<String,byte[]> e=new LinkedHashMap<>(); e.put("slide10.png",image(Color.GREEN)); e.put("slide2.png",image(Color.RED)); e.put("slide1.png",image(Color.BLUE)); File f=temp("images.zip"); zip(f,e);
        try(Deck d=Deck.open(f)) { assertEquals(3,d.size()); assertColor(Color.RED,d.render(1,800,400).getPixel(50,50)); }
    }
    @Test public void textSectionsPaginateRatherThanClip() throws Exception {
        File f=temp("lesson.txt"); try(OutputStream out=new FileOutputStream(f)) { out.write(xml("Aula 1\n---\nAula 2\fAula 3")); }
        try(Deck d=Deck.open(f)) { assertEquals(3,d.size()); assertNotNull(d.render(2,960,540)); }
    }
    @Test public void legacyPowerPointGeneratedByApachePoiOpens() throws Exception {
        File f=temp("legacy.ppt"); try(InputStream in=instrument.getContext().getAssets().open("legacy.ppt"); OutputStream out=new FileOutputStream(f)) { byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); }
        try(Deck d=Deck.open(f)) { assertEquals(2,d.size()); Bitmap b=d.render(0,960,720); assertNotNull(b); assertColor(Color.rgb(139,233,190),b.getPixel(10,10)); screenshot(b,"legacy-ppt-render.png"); }
    }
    @Test public void remoteControlAdvancesPausesAndReturnsWithoutTouch() throws Exception {
        Intent intent=new Intent(context,PlayerActivity.class).putExtra("demo",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PlayerActivity player=(PlayerActivity)instrument.startActivitySync(intent);
        try {
            awaitUi(()->player.hasWindowFocus()&&player.image.getDrawable()!=null,"The presentation did not acquire an input window"); assertEquals(0,player.page);
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); instrument.waitForIdleSync(); assertEquals(1,player.page);
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_CHANNEL_UP); instrument.waitForIdleSync(); assertEquals(2,player.page);
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT); instrument.waitForIdleSync(); assertEquals(1,player.page);
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); instrument.waitForIdleSync(); assertTrue(player.menuOpen);
            awaitUi(()->player.menu!=null&&player.menu.getWindow().getDecorView().hasWindowFocus(),"Presentation options did not receive focus");
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); instrument.waitForIdleSync(); assertFalse(player.menuOpen);
            awaitUi(player::hasWindowFocus,"Input focus did not return to the presentation");
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_B); instrument.waitForIdleSync(); assertTrue(player.blackout);
            instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); instrument.waitForIdleSync(); assertFalse(player.blackout); assertEquals(1,player.page);
            screenshot(instrument.getUiAutomation().takeScreenshot(),"remote-player.png");
        } catch(AssertionError error) { screenshot(instrument.getUiAutomation().takeScreenshot(),"remote-failure.png"); throw error; }
        finally { instrument.runOnMainSync(player::finish); }
    }
    @Test public void importedFilesAndPositionSurviveNewStoreInstance() throws Exception {
        LibraryStore s=new LibraryStore(context); LibraryStore.Item item=s.copy(new ByteArrayInputStream(xml("Primeiro\fSegundo")),"offline.txt"); item.page=1; item.count=2; s.touch(item);
        LibraryStore other=new LibraryStore(context); assertEquals(1,other.find(item.file).page); assertEquals(2,other.find(item.file).count); assertTrue(new File(other.dir,item.file).isFile());
        assertTrue(other.prefs.edit().putString("upgrade_marker","slide-position-and-import-v1").commit());
        assertTrue(new File(context.getApplicationInfo().dataDir,"shared_prefs/slide.xml").isFile());
    }
    @Test public void homeHasRemoteFocusableActions() throws Exception {
        MainActivity main=(MainActivity)instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try { instrument.waitForIdleSync(); assertTrue(main.getCurrentFocus() instanceof Button); assertEquals("Abrir apresentação",((Button)main.getCurrentFocus()).getText().toString()); instrument.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN); instrument.waitForIdleSync(); assertEquals("Pendrive e pastas",((Button)main.getCurrentFocus()).getText().toString()); screenshot(instrument.getUiAutomation().takeScreenshot(),"home.png"); } finally { instrument.runOnMainSync(main::finish); }
    }
    @Test public void updatesRequireNewVersionAndExactlyTheInstalledSigner() throws Exception {
        MainActivity main=(MainActivity)instrument.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        UpdateManager manager=new UpdateManager(main);
        try {
            File future=copyAsset("updates/future.apk","future.part"),wrong=copyAsset("updates/wrong.apk","wrong.apk");
            manager.verify(future,10001);
            try { manager.verify(wrong,10001); fail("An APK from a different signer was accepted"); } catch(IOException expected) { assertTrue(expected.getMessage().contains("assinatura")); }
            try { manager.verify(new File(context.getApplicationInfo().sourceDir),BuildConfig.VERSION_CODE); fail("A non-increasing version was accepted"); } catch(IOException expected) { assertTrue(expected.getMessage().contains("atualização válida")); }
        } finally { manager.close(); instrument.runOnMainSync(main::finish); }
    }
    File copyAsset(String asset,String target) throws IOException { File file=temp(target); try(InputStream in=instrument.getContext().getAssets().open(asset); OutputStream out=new FileOutputStream(file)) { byte[] buf=new byte[32768]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n); } return file; }
    interface UiCondition { boolean ready(); }
    void awaitUi(UiCondition condition,String message) {
        long end=SystemClock.uptimeMillis()+5000;
        while(SystemClock.uptimeMillis()<end) { boolean[] value={false}; instrument.runOnMainSync(()->value[0]=condition.ready()); if(value[0]) return; SystemClock.sleep(25); }
        fail(message);
    }
    void screenshot(Bitmap bitmap,String name) throws IOException { if(bitmap==null) return; File dir=new File(context.getExternalFilesDir(null),"slide-tests"); dir.mkdirs(); try(OutputStream out=new FileOutputStream(new File(dir,name))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,out); } }
}
