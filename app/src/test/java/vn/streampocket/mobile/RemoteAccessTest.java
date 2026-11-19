package vn.streampocket.mobile;
import org.junit.*;
import org.json.*;
import org.jsoup.Jsoup;
import okhttp3.*;
import java.io.*;
import java.net.*;
import java.util.*;
import static org.junit.Assert.*;
public class RemoteAccessTest {
 interface Checked {void run()throws Exception;}
 void denied(Checked r)throws Exception{try{r.run();fail("Expected rejection");}catch(IOException expected){}}
 JSONObject media(String url)throws Exception{return RemoteAccess.media(Json.obj("url",url,"page",Sources.MAIN+"/video/11547/","config",Json.obj("cdnM3u8Enabled",true,"cdnOtherEnabled",true,"cdnAuthKey","x-cdn-auth","cdnAuthValue","test-secret","cdnApp","test")));}
 @Test public void apiAssignedDomainIsScopedAndCannotBeForgedByJson()throws Exception{
  String fresh="https://never-listed-cdn.example/master.m3u8";
  denied(()->Net.allowed(fresh));JSONObject source=media(fresh);Net.allowed(fresh,source);
  denied(()->Net.allowed(fresh,new JSONObject(source.toString())));
  denied(()->Net.allowed(fresh,media("https://another-stream.example/start.m3u8")));
 }
 @Test public void manifestsDiscoverVariantAudioMapKeySegmentsAndKeepRedirectBase()throws Exception{
  JSONObject source=media("https://entry-new.example/start.m3u8");RemoteAccess.Scope scope=RemoteAccess.scope(source);
  scope.playlist(source.getString("url"),source.getString("url"),"#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,URI=\"https://audio-new.example/a.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://variant-new.example/old/list.m3u8");
  Net.allowed("https://audio-new.example/a.m3u8",source);Net.allowed("https://variant-new.example/old/list.m3u8",source);
  scope.redirect("https://variant-new.example/old/list.m3u8","https://rotated.example/new/list.m3u8");
  scope.playlist("https://variant-new.example/old/list.m3u8","https://rotated.example/new/list.m3u8","#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://key-new.example/key\"\n#EXT-X-MAP:URI=\"https://init-new.example/init.mp4\"\n#EXTINF:6,\nchunk.ts\n#EXTINF:6,\nhttps://segment-new.example/1.ts");
  for(String u:new String[]{"https://rotated.example/new/chunk.ts","https://key-new.example/key","https://init-new.example/init.mp4","https://segment-new.example/1.ts"})Net.allowed(u,source);
  assertEquals("https://rotated.example/new/list.m3u8",Net.finalUrl("https://variant-new.example/old/list.m3u8",source));
  denied(()->Net.allowed("https://not-in-any-playlist.example/1.ts",source));
 }
 @Test public void realInterceptorLearnsRedirectAndStripsAuthFromNewHost()throws Exception{
  JSONObject source=media("https://entry.example/start.m3u8");List<Request> sent=new ArrayList<>();
  OkHttpClient client=Net.forSource(source).newBuilder().addInterceptor(chain->{
   Request r=chain.request();sent.add(r);
   Response.Builder out=new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).message("fixture");
   if(r.url().host().equals("entry.example"))return out.code(302).header("Location","https://next.example/final/list.m3u8").body(ResponseBody.create(new byte[0],null)).build();
   return out.code(200).body(ResponseBody.create("#EXTM3U\n#EXTINF:6,\nhttps://third.example/chunk.ts\n",MediaType.get("application/vnd.apple.mpegurl"))).build();
  }).build();
  try(Response r=client.newCall(new Request.Builder().url(source.getString("url")).header("Authorization","should-not-follow").header("Cookie","private=1").build()).execute()){
   assertEquals("next.example",r.request().url().host());assertTrue(r.body().string().startsWith("#EXTM3U"));
  }
  assertEquals("test-secret",sent.get(0).header("x-cdn-auth"));
  for(String name:new String[]{"x-cdn-auth","x-cdn-app","Authorization","Cookie"})assertNull(name,sent.get(1).header(name));
  Net.allowed("https://third.example/chunk.ts",source);
 }
 @Test public void imagesMustHaveBeenObservedInSourceMetadata()throws Exception{
  String fresh="https://brand-new-images.example/cover.jpg?auth_key=fixture";
  denied(()->SourceImages.context(fresh));SourceImages.remember(Sources.MAIN+"/video/11547/",fresh);
  JSONObject image=SourceImages.context(fresh);Net.allowed(fresh,image);
  denied(()->Net.allowed("https://another-image.example/a.jpg",image));
  RemoteAccess.scope(image).playlist(fresh,fresh,"#EXTM3U\nhttps://unrelated.example/file");
  denied(()->Net.allowed("https://unrelated.example/file",image));
 }
 @Test public void nonPlaylistBodyCannotExpandTrust()throws Exception{
  JSONObject source=media("https://entry.example/start");RemoteAccess.scope(source).playlist("https://entry.example/start","https://entry.example/start","<html>https://unrelated.example/video</html>");
  denied(()->Net.allowed("https://unrelated.example/video",source));
 }
 @Test public void privateDestinationsAndMixedDnsAreRejected()throws Exception{
  for(String url:new String[]{"http://cdn.example/a","https://127.0.0.1/a","https://[::1]/a","https://10.0.0.1/a","https://localhost/a","https://machine.local/a","https://user@cdn.example/a","https://cdn.example:8443/a"})denied(()->RemoteAccess.publicUrl(url));
  for(String ip:new String[]{"127.0.0.1","10.2.3.4","172.16.1.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fc00::1","fe80::1","224.0.0.1"})assertFalse(ip,RemoteAccess.publicAddress(InetAddress.getByName(ip)));
  assertTrue(RemoteAccess.publicAddress(InetAddress.getByName("8.8.8.8")));
  denied(()->RemoteAccess.publicDns(h->List.of(InetAddress.getByName("8.8.8.8"),InetAddress.getByName("127.0.0.1"))).lookup("rebind.example"));
 }
 @Test public void redirectsToPrivateHostsAndLoopsAreStopped()throws Exception{
  JSONObject source=media("https://entry.example/start");
  denied(()->RemoteAccess.scope(source).redirect("https://entry.example/start","https://127.0.0.1/admin"));
  int[] calls={0};
  OkHttpClient looping=Net.forSource(source).newBuilder().addInterceptor(chain->{calls[0]++;return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(302).message("loop").header("Location","/start").body(ResponseBody.create(new byte[0],null)).build();}).build();
  denied(()->{try(Response ignored=looping.newCall(new Request.Builder().url("https://entry.example/start").build()).execute()){}});
  assertEquals(5,calls[0]);
 }
 @Test public void pageRedirectIsRememberedOnlyAfterSourceShapeValidation()throws Exception{
  String next="https://new-source-origin.example";
  denied(()->SourceSites.accept(Sources.LEGACY_FAST,Jsoup.parse("<main>advert</main>",next+"/")));
  assertFalse(SourceSites.known(next));
  String html="<main></main><script src='/static/web/js/plugins/crypto-image-protocol.js?v=next'></script>";
  try{SourceSites.accept(Sources.LEGACY_FAST,Jsoup.parse(html,next+"/"));assertEquals(next,SourceSites.current("fast"));assertTrue(SourceSites.known(next));}
  finally{SourceSites.accept(next,Jsoup.parse(html,Sources.FAST+"/"));}
 }
 @Test public void manifestBytesAndHostGrowthAreBounded()throws Exception{
  denied(()->Net.bounded(new ByteArrayInputStream(new byte[9]),8));
  RemoteAccess.Scope scope=RemoteAccess.scope(media("https://entry.example/a"));
  for(int i=1;i<256;i++)scope.grant("https://cdn"+i+".example/a");
  denied(()->scope.grant("https://overflow.example/a"));
 }
}
