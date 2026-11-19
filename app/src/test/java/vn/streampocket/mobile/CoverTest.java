package vn.streampocket.mobile;
import org.junit.Test;
import org.jsoup.Jsoup;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.util.Arrays;
import static org.junit.Assert.*;
public class CoverTest {
 @Test public void protocolUsesVersionFromSourcePage(){
  String html="<script src='/static/web/js/plugins/crypto-image-protocol.js?v=20261113008'></script>";
  assertEquals("https://huangguoai.com/static/web/js/plugins/crypto-image-protocol.js?v=20261113008",CoverCodec.protocolUrl(Jsoup.parse(html),Sources.MAIN));
  assertEquals("https://1bb4a2.ipycpknh.cc/static/web/js/plugins/crypto-image-protocol.js?v=20261113008",CoverCodec.protocolUrl(Jsoup.parse(html),Sources.FAST));
  assertEquals("",CoverCodec.protocolUrl(Jsoup.parse("<script src='http://evil.invalid/static/web/js/plugins/crypto-image-protocol.js'></script>"),Sources.MAIN));
 }
 @Test public void encryptedCoverBecomesDecodableImage()throws Exception{
  JSONObject p=CoverCodec.protocol("window.CryptoImageProtocol = Object.freeze ( { \"decryptEnabled\": true, \"decryptKey\":\"0123456789abcdef\", \"decryptIV\":\"fedcba9876543210\" } );");
  byte[] plain=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jk1sAAAAASUVORK5CYII=");
  Cipher c=Cipher.getInstance("AES/CBC/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(p.getString("decryptKey").getBytes("UTF-8"),"AES"),new IvParameterSpec(p.getString("decryptIV").getBytes("UTF-8")));
  byte[] encrypted=c.doFinal(Arrays.copyOf(plain,(plain.length+15)/16*16));byte[] result=CoverCodec.decode(encrypted,p);
  assertArrayEquals(plain,Arrays.copyOf(result,plain.length));
  assertArrayEquals(plain,CoverCodec.decode(plain,p));
 }
 @Test public void invalidProtocolIsNotUsed()throws Exception{
  try{CoverCodec.protocol("Object.freeze({\"decryptEnabled\":true,\"decryptKey\":\"short\",\"decryptIV\":\"short\"})");fail();}catch(IOException expected){}
 }
 @Test public void protocolHandlesBracesAndEscapesInsideJsonStrings()throws Exception{
  JSONObject original=new JSONObject().put("decryptEnabled",true).put("decryptKey","0123456789ab}def").put("decryptIV","fedcba9876543210").put("note","quoted \"text\" and {braces}").put("extra",new JSONObject().put("nested",true));
  JSONObject parsed=CoverCodec.protocol("window.CryptoImageProtocol = Object.freeze \n(\n"+original+"\n);");
  assertEquals(original.getString("decryptKey"),parsed.getString("decryptKey"));
  assertEquals(original.getString("note"),parsed.getString("note"));
  assertTrue(parsed.getJSONObject("extra").getBoolean("nested"));
 }
 @Test public void malformedProtocolWrapperIsRejected()throws Exception{
  for(String script:new String[]{"","Object.freeze[]","Object.freeze(null)","Object.freeze([])","Object.freeze({","Object.freeze({}","Object.freeze({}+other)"}){
   try{CoverCodec.protocol(script);fail(script);}catch(IOException expected){assertTrue(expected.getMessage().contains("thumbnail"));}
  }
 }
 @Test public void disabledOrWrongByteLengthProtocolIsRejected()throws Exception{
  for(JSONObject p:new JSONObject[]{
   new JSONObject().put("decryptEnabled",false).put("decryptKey","0123456789abcdef").put("decryptIV","fedcba9876543210"),
   new JSONObject().put("decryptEnabled",true).put("decryptKey","á23456789abcdefg").put("decryptIV","fedcba9876543210")}){
   try{CoverCodec.protocol("Object.freeze("+p+")");fail();}catch(IOException expected){}
  }
 }
 @Test public void malformedHandshakeFails()throws Exception{
  try{EdgePlayback.exchange("/api/videos/1/play?ep=1&page_texts=0",body->new JSONObject());fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("phiên phát"));}
 }
}
