package vn.streampocket.mobile;
import org.junit.*;
import org.json.*;
import static org.junit.Assert.*;
public class ImageTransportTest {
 @Test public void actualImageTransport()throws Exception{
  Assume.assumeTrue("1".equals(System.getenv("SP_LIVE_CHECK")));
  JSONObject result=Sources.catalogue(Json.obj("site","main"));
  JSONArray items=result.getJSONArray("items");assertTrue(items.length()>0);
  String url=items.getJSONObject(0).getString("cover");
  byte[] raw=Net.bytes(url,Json.obj("page",Sources.MAIN+"/"),0,0,null);
  byte[] plain=CoverCodec.decode(raw,Sources.imageProtocol(Sources.MAIN));
  assertTrue(CoverCodec.isImage(plain));
  System.out.println("PASS native HTTP client thumbnail path: "+plain.length+" bytes");
 }
}
