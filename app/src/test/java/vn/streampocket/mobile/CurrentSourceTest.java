package vn.streampocket.mobile;
import org.junit.*;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
/** Opt-in live metadata, small cover and HLS manifest verification; no video segments. */
public class CurrentSourceTest {
 @Test public void currentSourcesReturnDecodableCoverAndUsablePlaylist()throws Exception{
  Assume.assumeTrue("1".equals(System.getenv("SP_LIVE_CHECK")));
  for(String site:new String[]{"main","fast"}){
   String root="fast".equals(site)?Sources.FAST:Sources.MAIN;
   JSONObject series=Sources.episodes(Json.obj("url",root+"/video/11547/","site",site));
   String cover=series.getString("cover");assertFalse("Missing current cover",cover.isBlank());Net.allowed(cover,SourceImages.context(cover));
   byte[] decoded=Sources.imageBytes(Json.obj("url",cover,"site",site));
   assertTrue(CoverCodec.isImage(decoded));
   // Desktop decoder via reflection: Android compile stubs do not include java.desktop.
   assertNotNull(Class.forName("javax.imageio.ImageIO").getMethod("read",InputStream.class).invoke(null,new ByteArrayInputStream(decoded)));
   JSONObject media=Sources.media(Json.obj("id","hg-11547-1","site",site));
   String url=media.getString("url");System.out.println(site+" cover CDN="+URI.create(cover).getHost()+", playback CDN="+URI.create(url).getHost());
   Net.allowed(url,media);
   byte[] manifest;
   try{manifest=Net.bytes(url,media,0,0,null);}catch(java.net.ConnectException network){
    System.out.println("Windows JVM CDN connection failed; verifying HTTPS manifest with curl and the same headers.");
    manifest=LiveSourceTest.curl(url,null,Net.headers(url,media));
   }
   String text=new String(manifest,StandardCharsets.UTF_8);
   RemoteAccess.scope(media).playlist(url,Net.finalUrl(url,media),text);
   assertTrue(text.startsWith("#EXTM3U"));assertTrue(text.contains("#EXTINF")||text.contains("#EXT-X-STREAM-INF"));
   for(String line:text.split("\\r?\\n"))if(!line.isBlank()&&!line.startsWith("#"))Net.allowed(Net.resolve(Net.finalUrl(url,media),line.trim()),media);
   java.util.regex.Matcher references=java.util.regex.Pattern.compile("URI=\\\"([^\\\"]+)\\\"").matcher(text);while(references.find())Net.allowed(Net.resolve(Net.finalUrl(url,media),references.group(1)),media);
   System.out.println("PASS "+site+": "+series.getJSONArray("items").length()+" episodes; decoded cover; HLS playlist and segment origins validated, no segments downloaded.");
  }
 }
}
