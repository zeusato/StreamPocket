package vn.streampocket.mobile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** App-private password verifier; plaintext and unlocked state are never persisted. */
final class FeatureLock {
 private static final int ITERATIONS=210000;
 private final File file;
 private volatile boolean unlocked;
 private int failures;
 private long retryAfter;
 FeatureLock(File file){this.file=file;}
 boolean isUnlocked(){return unlocked;}
 boolean hasPassword(){return file.exists();}
 synchronized void lock(){unlocked=false;}
 void require()throws IOException{if(!unlocked)throw new IOException("Mở khóa nguồn video trong Cài đặt trước.");}
 static byte[] derive(char[] password,byte[] salt)throws Exception{
  PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
  try{return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}finally{spec.clearPassword();}
 }
 synchronized void create(char[] password,char[] confirm)throws Exception{
  if(hasPassword())throw new IOException("Mật khẩu đã được thiết lập.");
  if(password.length<6||password.length>128)throw new IOException("Mật khẩu cần từ 6 đến 128 ký tự.");
  if(!Arrays.equals(password,confirm))throw new IOException("Hai lần nhập mật khẩu chưa khớp.");
  byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
  String record="1."+Base64.getEncoder().encodeToString(salt)+"."+Base64.getEncoder().encodeToString(derive(password,salt));
  File temp=new File(file.getPath()+".tmp");
  try{
   try(FileOutputStream out=new FileOutputStream(temp)){out.write(record.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
   java.nio.file.Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   unlocked=true;
  }finally{java.nio.file.Files.deleteIfExists(temp.toPath());}
 }
 synchronized boolean unlock(char[] password)throws Exception{
  if(System.nanoTime()<retryAfter)throw new IOException("Nhập sai nhiều lần. Đợi 30 giây rồi thử lại.");
  if(!hasPassword())throw new IOException("Chưa thiết lập mật khẩu.");
  if(file.length()>512)throw new IOException("Dữ liệu khóa bị lỗi. Không thể mở khóa.");
  String[] parts=new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8).split("\\.");
  byte[] salt,expected;
  try{
   if(parts.length!=3||!parts[0].equals("1"))throw new IllegalArgumentException();
   salt=Base64.getDecoder().decode(parts[1]);expected=Base64.getDecoder().decode(parts[2]);
   if(salt.length!=16||expected.length!=32)throw new IllegalArgumentException();
  }catch(IllegalArgumentException e){throw new IOException("Dữ liệu khóa bị lỗi. Không thể mở khóa.");}
  boolean valid=password.length<=128&&MessageDigest.isEqual(expected,derive(password,salt));
  if(valid){unlocked=true;failures=0;retryAfter=0;}
  else if(++failures>=5){failures=0;retryAfter=System.nanoTime()+30_000_000_000L;}
  return valid;
 }
}
