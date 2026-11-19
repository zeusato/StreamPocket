package vn.streampocket.mobile;
import android.content.*;
import android.security.keystore.*;
import android.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

public final class Vault {
 private final Context context;
 public Vault(Context c){context=c.getApplicationContext();}
 private SecretKey key()throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias("stream-pocket")){
   KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");
   gen.init(new KeyGenParameterSpec.Builder("stream-pocket",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();
  }return (SecretKey)ks.getKey("stream-pocket",null);
 }
 public synchronized void put(String name,String text)throws Exception{
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
  byte[] encrypted=c.doFinal(text.getBytes(StandardCharsets.UTF_8)),iv=c.getIV();
  String value=Base64.encodeToString(iv,2)+"."+Base64.encodeToString(encrypted,2);
  if(!context.getSharedPreferences("private",0).edit().putString(name,value).commit())throw new Exception("Không lưu được trạng thái.");
 }
 public synchronized String get(String name,String fallback){
  try{
   String value=context.getSharedPreferences("private",0).getString(name,null);if(value==null)return fallback;
   String[] parts=value.split("\\.");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],2)));
   return new String(c.doFinal(Base64.decode(parts[1],2)),StandardCharsets.UTF_8);
  }catch(Exception e){return fallback;}
 }
}
