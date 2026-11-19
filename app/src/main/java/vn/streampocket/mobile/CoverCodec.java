package vn.streampocket.mobile;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;
import org.jsoup.nodes.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
final class CoverCodec {
 static String protocolUrl(Document doc,String root){
  for(Element script:doc.select("script[src]")){
   String url=Net.resolve(root+"/",script.attr("src"));
   if("/static/web/js/plugins/crypto-image-protocol.js".equals(java.net.URI.create(url).getPath()))try{return RemoteAccess.publicUrl(url);}catch(IOException ignored){}
  }return "";
 }
 static JSONObject protocol(String script)throws Exception{
  // Android's ICU regex rejects the bare closing brace accepted by desktop Java.
  // Parse the JSON argument directly, including quoted braces and escaped strings.
  final String marker="Object.freeze";int start=script.indexOf(marker);
  if(start<0)throw new IOException("Không đọc được cấu hình thumbnail.");
  JSONObject p;
  try{
   JSONTokener input=new JSONTokener(script.substring(start+marker.length()));
   if(input.nextClean()!='(')throw new JSONException("Missing opening parenthesis");
   Object value=input.nextValue();
   if(!(value instanceof JSONObject)||input.nextClean()!=')')throw new JSONException("Invalid protocol object");
   p=(JSONObject)value;
  }catch(JSONException e){throw new IOException("Không đọc được cấu hình thumbnail.",e);}
  if(!p.optBoolean("decryptEnabled")||p.optString("decryptKey").getBytes(StandardCharsets.UTF_8).length!=16||p.optString("decryptIV").getBytes(StandardCharsets.UTF_8).length!=16)throw new IOException("Cấu hình thumbnail không hợp lệ.");
  return p;
 }
 static boolean isImage(byte[] b){
  return b.length>12&&((b[0]==(byte)255&&b[1]==(byte)216&&b[2]==(byte)255)||
   (b[0]==(byte)137&&b[1]==80&&b[2]==78&&b[3]==71)||
   (b[0]==82&&b[1]==73&&b[2]==70&&b[3]==70&&b[8]==87&&b[9]==69&&b[10]==66&&b[11]==80));
 }
 static byte[] decode(byte[] raw,JSONObject p)throws Exception{
  if(isImage(raw))return raw;
  Cipher aes=Cipher.getInstance("AES/CBC/NoPadding");
  aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(p.getString("decryptKey").getBytes(StandardCharsets.UTF_8),"AES"),new IvParameterSpec(p.getString("decryptIV").getBytes(StandardCharsets.UTF_8)));
  byte[] result=Arrays.copyOf(aes.doFinal(Arrays.copyOf(raw,(raw.length+15)/16*16)),raw.length);
  if(!isImage(result))throw new IOException("Ảnh bìa giải mã không hợp lệ.");return result;
 }
}
