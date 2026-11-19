package vn.streampocket.mobile;
import org.junit.*;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
/** Opt-in, metadata/playlist only. Does not download video segments. */
public class LiveSourceTest {
 static byte[] curl(String url,String body,Map<String,String> headers)throws Exception{
  List<String> args=new ArrayList<>(List.of("curl.exe","--fail","--silent","--show-error","--max-time","25","-A","Mozilla/5.0 StreamPocket/1.0","--retry","2","--retry-all-errors","--retry-delay","1",url));
  headers.forEach((k,v)->{args.add("-H");args.add(k+": "+v);});
  if(body!=null)args.addAll(List.of("-H","Content-Type: application/json","--data-binary","@-"));
  Process process=new ProcessBuilder(args).start();
  try(OutputStream in=process.getOutputStream()){if(body!=null)in.write(body.getBytes(StandardCharsets.UTF_8));}
  byte[] bytes=process.getInputStream().readAllBytes(),errors=process.getErrorStream().readAllBytes();
  if(process.waitFor()!=0)throw new IOException("Live source transport failed: "+new String(errors,StandardCharsets.UTF_8));
  return bytes;
 }
 @Test public void officialGuestPlaybackHandshakeReturnsRealPlaylist()throws Exception{
  Assume.assumeTrue("1".equals(System.getenv("SP_LIVE_CHECK")));
  String root=Sources.MAIN,page=root+"/video/11547/";
  JSONObject data=EdgePlayback.exchange("/api/videos/11547/episodes/1/playback",b->new JSONObject(new String(curl(root+"/api/playback/request",b.toString(),Map.of("Referer",page,"Origin",root)),StandardCharsets.UTF_8))).getJSONObject("data");
  assertEquals("GET",data.getString("manifest_method"));assertFalse(data.getJSONObject("streams").getString("main").isBlank());
  Path fixtures=Path.of(System.getenv("SP_LIVE_FIXTURES"));
  org.jsoup.nodes.Document document=org.jsoup.Jsoup.parse(new String(java.nio.file.Files.readAllBytes(fixtures.resolve("sample-film.html")),StandardCharsets.UTF_8));
  JSONObject cdn=data.getJSONObject("cdn");JSONObject config=Json.obj("cdnM3u8Enabled",cdn.optBoolean("m3u8Enabled"),"cdnOtherEnabled",cdn.optBoolean("otherEnabled"),"cdnAuthKey",cdn.optString("authKey"),"cdnAuthValue",cdn.optString("authValue"),"cdnApp",cdn.optString("app"));
  String url=Net.resolve(page,data.getJSONObject("streams").getString("main"));System.out.println("Playback CDN: "+java.net.URI.create(url).getHost());JSONObject media=RemoteAccess.media(Json.obj("page",page,"url",url,"config",config));Net.allowed(url,media);
  String playlist=new String(curl(url,null,Net.headers(url,media)),StandardCharsets.UTF_8);
  assertTrue(playlist.startsWith("#EXTM3U"));assertTrue(playlist.contains("#EXTINF")||playlist.contains("#EXT-X-STREAM-INF"));
  System.out.println("PASS live guest session and HLS playlist; no segments fetched.");
 }
 @Test public void downloadedCoverDecodesWithPublishedProtocol()throws Exception{
  Assume.assumeTrue("1".equals(System.getenv("SP_LIVE_CHECK")));
  Path fixtures=Path.of(System.getenv("SP_LIVE_FIXTURES"));
  byte[] plain=CoverCodec.decode(java.nio.file.Files.readAllBytes(fixtures.resolve("cover-encrypted.bin")),CoverCodec.protocol(new String(java.nio.file.Files.readAllBytes(fixtures.resolve("hg-image-protocol.js")),StandardCharsets.UTF_8)));
  assertTrue(CoverCodec.isImage(plain));
  java.nio.file.Files.write(fixtures.resolve("cover-decoded-check.bin"),plain);
  System.out.println("PASS real thumbnail decode: "+plain.length+" bytes");
 }
}
