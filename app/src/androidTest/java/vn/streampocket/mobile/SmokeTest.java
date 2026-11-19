package vn.streampocket.mobile;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SmokeTest {
 Context context;
 @Before public void before(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();}
 @Test public void nativeCdnContextSurvivesAndroidJsonAndLearnsChild()throws Exception{
  JSONObject source=RemoteAccess.media(Json.obj("url","https://new-manifest.example/main.m3u8"));
  Net.allowed(source.getString("url"),source);
  RemoteAccess.scope(source).playlist(source.getString("url"),source.getString("url"),"#EXTM3U\n#EXTINF:6,\nhttps://new-segment.example/a.ts");
  Net.allowed("https://new-segment.example/a.ts",source);
  try{Net.allowed("https://new-segment.example/a.ts",new JSONObject(source.toString()));fail();}catch(IOException expected){}
 }
 @Test public void passwordVerifierSurvivesRestartButUnlockDoesNot()throws Exception{
  File file=new File(context.getNoBackupFilesDir(),"test-lock-"+UUID.randomUUID());
  try{
   FeatureLock a=new FeatureLock(file);a.create("test-password".toCharArray(),"test-password".toCharArray());assertTrue(a.isUnlocked());
   FeatureLock b=new FeatureLock(file);assertTrue(b.hasPassword());assertFalse(b.isUnlocked());
   assertFalse(b.unlock("wrong".toCharArray()));assertTrue(b.unlock("test-password".toCharArray()));b.lock();
   try{b.require();fail();}catch(IOException expected){}
  }finally{file.delete();}
 }
 @Test public void coverProtocolParsesWithAndroidJson()throws Exception{
  JSONObject original=new JSONObject().put("decryptEnabled",true).put("decryptKey","0123456789ab}def").put("decryptIV","fedcba9876543210");
  JSONObject parsed=CoverCodec.protocol("window.CryptoImageProtocol = Object.freeze \n("+original+");");
  assertEquals(original.getString("decryptKey"),parsed.getString("decryptKey"));
  try{CoverCodec.protocol("Object.freeze({}");fail();}catch(IOException expected){}
 }
 @Test public void encryptedStateAndOriginGuard()throws Exception{
  Vault vault=new Vault(context);vault.put("test","secret-local");assertEquals("secret-local",vault.get("test",""));
  assertFalse(context.getSharedPreferences("private",0).getString("test","").contains("secret-local"));
  for(String url:List.of("http://127.0.0.1/","https://huangguoai.com.evil.test/","https://user@huangguoai.com/")){
   try{Net.allowed(url);fail(url);}catch(IOException expected){}
  }
  assertEquals("https://huangguoai.com/2.ts",Net.resolve("https://huangguoai.com/1.m3u8","2.ts"));
 }
 @Test public void realFfmpegProbeAndLosslessJoin()throws Exception{
  File dir=new File(context.getCacheDir(),"instrumentation");dir.mkdirs();
  try{
   File a=new File(dir,"a.mp4"),b=new File(dir,"b.mp4"),out=new File(dir,"joined.mp4");
   for(File f:List.of(a,b)){
    try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sample.mp4");OutputStream stream=new FileOutputStream(f)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)stream.write(bytes,0,n);}
   }
   Media media=new Media(context);assertTrue(media.probe(a).getDouble("duration")>0);
   assertTrue(media.compatible(List.of(a,b)));
   media.join(List.of(a,b),out,()->{},(f,s)->{});
   assertEquals(media.probe(a).getDouble("duration")*2,media.probe(out).getDouble("duration"),.5);
   FFmpegSession session=FFmpegKit.executeWithArguments(new String[]{"-v","error","-xerror","-i",out.getAbsolutePath(),"-f","null","-"});
   assertTrue(ReturnCode.isSuccess(session.getReturnCode()));
  }finally{for(File f:Objects.requireNonNull(dir.listFiles()))f.delete();dir.delete();}
 }
 @Test public void compressionNeverUsesSoftwareVideoEncoder()throws Exception{
  if(!HardwareEncoder.capabilities().startsWith("Thiết bị chưa"))return;
  File input=new File(context.getCacheDir(),"sample.mp4");
  try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sample.mp4");OutputStream out=new FileOutputStream(input)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  try{
   new Media(context).encode(input,new File(context.getCacheDir(),"compressed.mp4"),true,null,()->{},(f,s)->{});
   fail("Software-only device should reject encoding.");
  }catch(IOException expected){assertTrue(expected.getMessage().contains("phần cứng"));}
  finally{input.delete();}
 }
}
