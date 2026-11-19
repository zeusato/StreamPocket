package vn.streampocket.mobile;
import org.junit.Test;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import static org.junit.Assert.*;
public class UpdateReleaseTest {
 interface Checked{void run()throws Exception;}
 void reject(Checked c)throws Exception{try{c.run();fail("Expected rejection");}catch(IOException expected){}}
 byte[] payload="signed-apk-fixture-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
 JSONObject manifest()throws Exception{return Json.obj("packageName",UpdateRelease.PACKAGE,"versionName","1.1.5","versionCode",10,"apk","Stream-Pocket-1.1.5.apk","size",payload.length,"sha256",UpdateRelease.hex(MessageDigest.getInstance("SHA-256").digest(payload)));}
 JSONObject release()throws Exception{return Json.obj("tag_name","v1.1.5","draft",false,"prerelease",false,"body","Release notes","assets",Json.array(
  Json.obj("name","update.json","state","uploaded","browser_download_url",UpdateRelease.assetUrl("v1.1.5","update.json"),"size",350),
  Json.obj("name","Stream-Pocket-1.1.5.apk","state","uploaded","browser_download_url",UpdateRelease.assetUrl("v1.1.5","Stream-Pocket-1.1.5.apk"),"size",payload.length)));}
 @Test public void authenticReleaseCanUpdateOlderVersionAndCacheRoundTrips()throws Exception{
  UpdateRelease.Info info=UpdateRelease.parse(release(),manifest());assertEquals(10,info.code);assertTrue(info.code>9);assertFalse(info.code>10);assertEquals("1.1.5",new UpdateRelease.Info(info.json()).version);assertEquals("Release notes",info.notes);
 }
 @Test public void draftPrereleaseAndMismatchedVersionAreRejected()throws Exception{
  for(String key:new String[]{"draft","prerelease"}){JSONObject r=release().put(key,true);reject(()->UpdateRelease.parse(r,manifest()));}
  JSONObject r=release().put("tag_name","v1.1.6");reject(()->UpdateRelease.parse(r,manifest()));
 }
 @Test public void wrongRepoPackageHashSizeAndMissingAssetAreRejected()throws Exception{
  JSONObject r=release();r.getJSONArray("assets").getJSONObject(1).put("browser_download_url","https://github.com/other/repo/releases/download/v1.1.5/Stream-Pocket-1.1.5.apk");reject(()->UpdateRelease.parse(r,manifest()));
  for(JSONObject m:new JSONObject[]{manifest().put("packageName","other.app"),manifest().put("sha256","bad"),manifest().put("size",1),manifest().put("versionCode",0),manifest().put("apk","different.apk")})reject(()->UpdateRelease.parse(release(),m));
  JSONObject empty=release().put("assets",new JSONArray());reject(()->UpdateRelease.asset(empty,"update.json"));
 }
 @Test public void downloadRedirectsStayOnGithubAndNoCredentialsOrHttp()throws Exception{
  for(String url:new String[]{"https://api.github.com/repos/zeusato/StreamPocket/releases/latest","https://release-assets.githubusercontent.com/asset?token=fixture","https://github.com/zeusato/StreamPocket/releases/download/v1.1.5/update.json"})UpdateRelease.downloadHost(url);
  for(String url:new String[]{"http://github.com/file","https://github.com.evil.example/file","https://user@github.com/file","https://127.0.0.1/file","https://github.com:8443/file"})reject(()->UpdateRelease.downloadHost(url));
 }
 @Test public void exactBytesVerifyAndReportProgress()throws Exception{
  UpdateRelease.Info info=UpdateRelease.parse(release(),manifest());ByteArrayOutputStream out=new ByteArrayOutputStream();long[] progress={0};
  UpdateRelease.copyVerified(new ByteArrayInputStream(payload),out,info,()->{},n->progress[0]=n);
  assertArrayEquals(payload,out.toByteArray());assertEquals(payload.length,progress[0]);
 }
 @Test public void corruptTruncatedOversizeAndCancelledDownloadsNeverVerify()throws Exception{
  UpdateRelease.Info info=UpdateRelease.parse(release(),manifest());byte[] bad=payload.clone();bad[0]^=1;
  for(byte[] bytes:new byte[][]{bad,new byte[1],new byte[payload.length+1]})reject(()->UpdateRelease.copyVerified(new ByteArrayInputStream(bytes),new ByteArrayOutputStream(),info,()->{},n->{}));
  reject(()->UpdateRelease.copyVerified(new ByteArrayInputStream(payload),new ByteArrayOutputStream(),info,()->{throw new InterruptedIOException("cancel");},n->{}));
 }
}
