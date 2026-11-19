package vn.streampocket.mobile;
import org.junit.Test;
import org.json.*;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
public class SeriesMetadataTest {
 @Test public void perEpisodeDurationsDoNotCopyFirstEpisode()throws Exception{
  JSONObject c=new JSONObject("{\"ep\":1,\"trackingEpisodes\":{\"1\":{\"episode\":1,\"drama_duration\":125},\"2\":{\"episode\":2,\"drama_duration\":143},\"3\":{\"episode\":3,\"drama_duration\":3723}}}");
  JSONObject schema=new JSONObject("{\"duration\":\"PT2M5S\"}");
  assertEquals("02:05",SeriesMetadata.duration(c,schema,1));
  assertEquals("02:23",SeriesMetadata.duration(c,schema,2));
  assertEquals("1:02:03",SeriesMetadata.duration(c,schema,3));
  assertEquals("",SeriesMetadata.duration(c,schema,4));
 }
 @Test public void schemaFallbackOnlyAppliesToCurrentEpisode()throws Exception{
  JSONObject c=new JSONObject("{\"ep\":2,\"trackingEpisodes\":{\"2\":{\"episode\":9,\"drama_duration\":999}}}");
  assertEquals("02:23",SeriesMetadata.duration(c,new JSONObject("{\"duration\":\"PT2M23S\"}"),2));
  assertEquals("",SeriesMetadata.duration(c,new JSONObject("{\"duration\":\"PT2M23S\"}"),1));
  assertEquals("",SeriesMetadata.duration(c,new JSONObject("{\"duration\":\"bad\"}"),2));
  assertEquals("",SeriesMetadata.duration(c,new JSONObject("{\"duration\":\"-PT2M\"}"),2));
 }
 @Test public void prefersCurrentPlayerCoverAndRetainsSignedQuery()throws Exception{
  JSONObject c=new JSONObject("{\"coverSrc\":\"https://pic.drleao.cn/cover.jpg?auth_key=test\",\"posterSrc\":\"/poster.jpg\"}");
  JSONObject schema=new JSONObject("{\"thumbnailUrl\":[\"https://unsupported.invalid/old.jpg\"]}");
  assertEquals("https://pic.drleao.cn/cover.jpg?auth_key=test",SeriesMetadata.cover(Jsoup.parse(""),c,schema,Sources.MAIN));
  c.put("coverSrc","javascript:bad");
  assertEquals(Sources.FAST+"/poster.jpg",SeriesMetadata.cover(Jsoup.parse(""),c,schema,Sources.FAST));
 }
 @Test public void acceptsSchemaStringArrayAndMetaWithoutBrokenPlaceholder()throws Exception{
  assertEquals(Sources.MAIN+"/cover.jpg",SeriesMetadata.cover(Jsoup.parse(""),new JSONObject(),new JSONObject("{\"thumbnailUrl\":\"/cover.jpg\"}"),Sources.MAIN));
  assertEquals("https://pic.drleao.cn/valid.jpg",SeriesMetadata.cover(Jsoup.parse(""),new JSONObject(),new JSONObject("{\"thumbnailUrl\":[\"http://unsupported.invalid/no.jpg\",\"https://pic.drleao.cn/valid.jpg\"]}"),Sources.MAIN));
  assertEquals(Sources.MAIN+"/meta.jpg",SeriesMetadata.cover(Jsoup.parse("<meta property='og:image' content='/meta.jpg'>"),new JSONObject(),new JSONObject(),Sources.MAIN));
  assertEquals("",SeriesMetadata.cover(Jsoup.parse(""),new JSONObject(),new JSONObject(),Sources.MAIN));
 }
}
