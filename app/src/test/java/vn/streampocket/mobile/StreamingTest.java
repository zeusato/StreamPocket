package vn.streampocket.mobile;
import org.junit.Test;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.*;
import javax.crypto.spec.*;
import static org.junit.Assert.*;
public class StreamingTest {
 @Test public void largeSegmentUsesBoundedReads()throws Exception{
  long total=188L*400000;AtomicLong read=new AtomicLong(),written=new AtomicLong();
  InputStream source=new InputStream(){
   public int read(){throw new AssertionError("Use bounded block reads");}
   public int read(byte[] b,int off,int len){
    assertTrue(len<=65536);long remaining=total-read.get();if(remaining==0)return -1;
    int n=(int)Math.min(len,remaining);Arrays.fill(b,off,off+n,(byte)0x47);read.addAndGet(n);return n;
   }
  };
  OutputStream sink=new OutputStream(){public void write(int b){written.incrementAndGet();}public void write(byte[] b,int off,int len){written.addAndGet(len);}};
  assertEquals(total,Hls.copySegment(source,sink,true,()->{}));assertEquals(total,written.get());
 }
 @Test public void aesDecryptStreamsWithoutWholeSegmentBuffer()throws Exception{
  byte[] key=new byte[16],iv=new byte[16],packet=new byte[188*100];Arrays.fill(packet,(byte)0x47);
  File encrypted=File.createTempFile("sp-segment-", ".bin");
  try{
   Cipher encode=Cipher.getInstance("AES/CBC/PKCS5Padding");encode.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));
   try(OutputStream out=new CipherOutputStream(new FileOutputStream(encrypted),encode)){for(int i=0;i<100;i++)out.write(packet);}
   Cipher decode=Cipher.getInstance("AES/CBC/PKCS5Padding");decode.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec(iv));
   AtomicLong count=new AtomicLong();
   try(InputStream in=new CipherInputStream(new FileInputStream(encrypted),decode)){
    long n=Hls.copySegment(in,new OutputStream(){public void write(int b){assertEquals(0x47,b);count.incrementAndGet();}public void write(byte[] b,int off,int len){for(int i=off;i<off+len;i++)assertEquals(0x47,b[i]);count.addAndGet(len);}},true,()->{});
    assertEquals(188L*100*100,n);assertEquals(n,count.get());
   }
  }finally{encrypted.delete();}
 }
 @Test public void cancellationStopsBetweenChunks()throws Exception{
  int[] checks={0};
  try{Hls.copySegment(new ByteArrayInputStream(new byte[200000]),new ByteArrayOutputStream(),false,()->{if(++checks[0]==2)throw new InterruptedIOException("cancel");});fail();}
  catch(InterruptedIOException expected){assertEquals(2,checks[0]);}
 }
}
