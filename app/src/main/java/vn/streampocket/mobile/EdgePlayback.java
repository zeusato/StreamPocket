package vn.streampocket.mobile;
import org.json.JSONObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
/** Implements the source's public dx150-edge-v1 guest playback transport. */
final class EdgePlayback {
 interface Exchange {JSONObject post(JSONObject body)throws Exception;}
 static byte[] utf8(String s){return s.getBytes(StandardCharsets.UTF_8);}
 static String b64(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
 static byte[] decode(String s){return Base64.getUrlDecoder().decode(s);}
 static byte[] coordinate(BigInteger n){
  byte[] s=n.toByteArray(),r=new byte[32];System.arraycopy(s,Math.max(0,s.length-32),r,Math.max(0,32-s.length),Math.min(32,s.length));return r;
 }
 static byte[] publicKey(ECPublicKey k){return ByteBuffer.allocate(65).put((byte)4).put(coordinate(k.getW().getAffineX())).put(coordinate(k.getW().getAffineY())).array();}
 static byte[] derive(byte[] shared,byte[] salt,String info)throws Exception{
  Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(salt,"HmacSHA256"));byte[] prk=m.doFinal(shared);
  m.init(new SecretKeySpec(prk,"HmacSHA256"));m.update(utf8(info));return m.doFinal(new byte[]{1});
 }
 static byte[] crypt(int mode,byte[] key,byte[] iv,String aad,byte[] data)throws Exception{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));c.updateAAD(utf8(aad));return c.doFinal(data);
 }
 static JSONObject get(String root,String path,String page)throws Exception{
  if(!SourceSites.known(root)||!path.matches("/api/videos/[1-9]\\d*/episodes/[1-9]\\d*/playback"))throw new IOException("Yêu cầu phát không hợp lệ.");
  try{return exchange(path,b->Net.postJson(root+"/api/playback/request",b,Json.obj("page",page)));}
  catch(IOException first){return exchange(path,b->Net.postJson(root+"/api/playback/request",b,Json.obj("page",page)));}
 }
 static JSONObject exchange(String path,Exchange transport)throws Exception{
  KeyPairGenerator g=KeyPairGenerator.getInstance("EC");g.initialize(new ECGenParameterSpec("secp256r1"));KeyPair pair=g.generateKeyPair();
  long started=System.nanoTime();
  JSONObject h=transport.post(Json.obj("v",1,"type","hello","client_pub",b64(publicKey((ECPublicKey)pair.getPublic()))));
  String kid=h.optString("kid"),sid=h.optString("sid"),site=h.optString("site");long time=h.optLong("server_time"),expires=h.optLong("expires_at");
  if(h.optInt("v")!=1||!"hello".equals(h.optString("type"))||!kid.matches("[A-Za-z0-9_-]{1,64}")||!sid.matches("[A-Za-z0-9_-]{16,128}")||site.isBlank()||site.length()>256||expires<=time||expires-time>600)throw new IOException("Nguồn trả phiên phát không hợp lệ.");
  byte[] raw=decode(h.getString("server_pub")),salt=decode(h.getString("salt"));
  if(raw.length!=65||raw[0]!=4||salt.length!=32)throw new IOException("Khóa phiên phát không hợp lệ.");
  ECPoint point=new ECPoint(new BigInteger(1,Arrays.copyOfRange(raw,1,33)),new BigInteger(1,Arrays.copyOfRange(raw,33,65)));
  PublicKey remote=KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point,((ECPublicKey)pair.getPublic()).getParams()));
  KeyAgreement ka=KeyAgreement.getInstance("ECDH");ka.init(pair.getPrivate());ka.doPhase(remote,true);byte[] shared=ka.generateSecret();
  String info=String.join("\n","dx150-edge-v1","playback",site,kid,sid);
  byte[] req=derive(shared,salt,info+"\nreq"),res=derive(shared,salt,info+"\nres");
  int seq=1;long ts=time+(System.nanoTime()-started)/1_000_000_000L;byte[] iv=ByteBuffer.allocate(12).putLong(0).putInt(seq).array();
  JSONObject packet=Json.obj("request",Json.obj("method","POST","path",path,"body",Base64.getEncoder().encodeToString(utf8("{}")),"content_type","application/json"));
  byte[] ciphertext=crypt(Cipher.ENCRYPT_MODE,req,iv,info+"\nreq\n"+seq+"\n"+ts,utf8(packet.toString()));
  JSONObject r=transport.post(Json.obj("v",1,"type","call","kid",kid,"sid",sid,"seq",seq,"ts",ts,"ciphertext",b64(ciphertext)));
  if(r.optInt("v")!=1||!"result".equals(r.optString("type"))||!sid.equals(r.optString("sid"))||!kid.equals(r.optString("kid"))||r.optInt("seq")!=seq||r.optLong("ts")!=ts)throw new IOException("Phản hồi phiên phát không khớp.");
  JSONObject inner=new JSONObject(new String(crypt(Cipher.DECRYPT_MODE,res,iv,info+"\nres\n"+seq+"\n"+ts,decode(r.getString("ciphertext"))),StandardCharsets.UTF_8));
  int status=inner.getInt("status");if(status<200||status>=300)throw new IOException("Nguồn từ chối phát video (HTTP "+status+").");
  return new JSONObject(new String(Base64.getDecoder().decode(inner.getString("body")),StandardCharsets.UTF_8));
 }
}
