package vn.streampocket.mobile;
import org.junit.Test;
import org.json.JSONObject;
import java.io.IOException;
import static org.junit.Assert.*;

public class ProtocolTest {
 @Test public void paginationHasOneSeparatorAndKeepsRoute(){
  assertEquals("/2/",Sources.pagePath("/",2));
  assertEquals("/ai-duanju/2/",Sources.pagePath("/ai-duanju/",2));
  assertEquals("/newest/2/",Sources.pagePath("/newest",2));
  assertEquals("/author/156291/video/3/",Sources.pagePath("/author/156291/",3));
  assertEquals("/tag/dushi/page/2/",Sources.pagePath("/tag/dushi/",2));
  assertEquals("/search/video/AI/2/",Sources.pagePath("/search/video/AI/",2));
 }
 @Test public void seriesUrlsAreRestricted()throws Exception{
  assertEquals("11547",Sources.id("https://huangguoai.com/video/11547/ep-2/"));
  assertEquals("11547",Sources.id("https://duhgxf.lbvrhyir.cc/video/11547/"));
  assertEquals("11547",Sources.id("https://1bb4a2.ipycpknh.cc/video/11547/"));
  for(String s:new String[]{"https://huangguoai.com.evil.test/video/1/","https://user@huangguoai.com/video/1/","https://huangguoai.com/video/../","http://huangguoai.com/video/1/"})
   try{Sources.id(s);fail(s);}catch(Exception expected){}
 }
 @Test public void sourceHeadersDoNotLeakToAnotherCdn(){
  JSONObject source=Json.obj("url","https://video.hzjvlo.cn/a.m3u8","page","https://huangguoai.com/video/1/","config",Json.obj("cdnM3u8Enabled",true,"cdnOtherEnabled",true,"cdnAuthKey","x-cdn-auth","cdnAuthValue","fake","cdnApp","test"));
  assertEquals("fake",Net.headers("https://video.hzjvlo.cn/2.ts",source).get("x-cdn-auth"));
  assertFalse(Net.headers("https://another.hzjvlo.cn/2.ts",source).containsKey("x-cdn-auth"));
 }
 @Test public void originGuardRejectsLocalAndUntrustedOrigins()throws Exception{
  for(String s:new String[]{"http://127.0.0.1/","https://huangguoai.com.evil.test/","https://user@huangguoai.com/","https://huangguoai.com:8080/"}){
   try{Net.allowed(s);fail(s);}catch(IOException expected){}
  }
  Net.allowed("https://huangguoai.com/video/1/");
 }
 @Test public void updatedCdnHostsAreAllowedWithoutOpeningLookalikeDomains()throws Exception{
  Net.allowed("https://1bb4a2.ipycpknh.cc/video/11547/");
  JSONObject source=RemoteAccess.media(Json.obj("url","https://new-cdn.example/video.m3u8"));
  Net.allowed("https://new-cdn.example/video.m3u8",source);
  for(String url:new String[]{"https://pic.qnlycc.cn.evil.test/a","https://unknown.tjyyjk.cn/a","http://pic.qnlycc.cn/a","https://user@pic.qnlycc.cn/a","https://tp2.qnlycc.cn.evil.test/a","https://anything.qnlycc.cn/a"}){
   try{Net.allowed(url);fail(url);}catch(IOException expected){}
  }
 }
 @Test public void aesPlaylistKeepsSequenceAndClearsKeys()throws Exception{
  Hls.Playlist p=Hls.parseMedia("https://cdn.pt1269.com/f/a.m3u8","#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:42\n#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin?x=1,2\"\n#EXTINF:6,\na.ts\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:6,\nb.ts\n#EXT-X-ENDLIST");
  assertEquals(2,p.segments.size());assertEquals(42,p.segments.get(0).sequence);
  assertEquals("https://cdn.pt1269.com/f/key.bin?x=1,2",p.segments.get(0).key);
  assertEquals("",p.segments.get(1).key);assertEquals(43,p.segments.get(1).sequence);
 }
 @Test public void fmp4RangesAndInitSegment()throws Exception{
  Hls.Playlist p=Hls.parseMedia("https://cdn.pt1269.com/a.m3u8","#EXTM3U\n#EXT-X-MAP:URI=\"a.mp4\",BYTERANGE=\"100@0\"\n#EXT-X-BYTERANGE:200@100\na.mp4\n#EXT-X-BYTERANGE:200\na.mp4\n#EXT-X-ENDLIST");
  assertEquals("mp4",p.extension);assertTrue(p.segments.get(0).init);assertEquals(300,p.segments.get(2).start);
 }
 @Test public void unsupportedEncryptionFailsExplicitly()throws Exception{
  try{Hls.parseMedia("https://cdn.pt1269.com/a.m3u8","#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"\na.ts");fail();}
  catch(IOException expected){assertTrue(expected.getMessage().contains("mã hóa"));}
 }
 @Test public void rangeCannotContinueOnDifferentResource()throws Exception{
  try{Hls.parseMedia("https://cdn.pt1269.com/a.m3u8","#EXTM3U\n#EXT-X-BYTERANGE:200\na.mp4");fail();}
  catch(IOException expected){assertTrue(expected.getMessage().contains("offset"));}
 }
}
