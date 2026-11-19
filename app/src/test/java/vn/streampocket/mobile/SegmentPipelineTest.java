package vn.streampocket.mobile;
import org.junit.Test;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import javax.crypto.*;import javax.crypto.spec.*;import okhttp3.*;
import static org.junit.Assert.*;
public class SegmentPipelineTest {
 Hls.Playlist playlist(int n){Hls.Playlist p=new Hls.Playlist();for(int i=0;i<n;i++){Hls.Segment s=new Hls.Segment();s.sequence=i+50;s.url="https://cdn.example/"+i;p.segments.add(s);}return p;}
 byte[] packet(int index){byte[] b=new byte[188*10];Arrays.fill(b,(byte)index);b[0]=0x47;return b;}
 File directory()throws Exception{return java.nio.file.Files.createTempDirectory("sp-parallel-test-").toFile();}
 void clean(File dir){File[] f=dir.listFiles();if(f!=null)for(File p:f){if(p.isDirectory())clean(p);else p.delete();}dir.delete();}
 void onlyOutput(File dir,File out){assertArrayEquals(new String[]{out.getName()},dir.list());}
 @Test(timeout=8000) public void overlapsThreeTransfersButWritesOnlyPlaylistOrder()throws Exception{
  File dir=directory(),out=new File(dir,"result");Hls.Playlist p=playlist(17);
  AtomicInteger active=new AtomicInteger(),max=new AtomicInteger(),started=new AtomicInteger();
  CountDownLatch firstThree=new CountDownLatch(3),laterFinished=new CountDownLatch(2);AtomicLong lastBytes=new AtomicLong();AtomicInteger lastDone=new AtomicInteger();
  try{
   Hls.assemble(p,out,()->{},(done,total,bytes,rate)->{assertTrue(done>=lastDone.get());assertTrue(bytes>=lastBytes.get());assertTrue(Double.isFinite(rate)&&rate>=0);lastDone.set(done);lastBytes.set(bytes);},(i,file,stop,count)->{
    int running=active.incrementAndGet();max.accumulateAndGet(running,Math::max);started.incrementAndGet();
    try{
     if(i<3){firstThree.countDown();assertTrue(firstThree.await(2,TimeUnit.SECONDS));}
     if(i==0){assertTrue(laterFinished.await(2,TimeUnit.SECONDS));assertEquals(3,started.get());assertEquals(0,out.length());}
     byte[] b=packet(i);java.nio.file.Files.write(file.toPath(),b);count.bytes(b.length);if(i==1||i==2)laterFinished.countDown();
    }finally{active.decrementAndGet();}
   },key->{throw new AssertionError();},()->{});
   assertEquals(3,max.get());assertEquals(0,active.get());assertEquals(17,lastDone.get());assertEquals(17*1880,lastBytes.get());
   ByteArrayOutputStream expected=new ByteArrayOutputStream();for(int i=0;i<17;i++)expected.write(packet(i));
   assertArrayEquals(expected.toByteArray(),java.nio.file.Files.readAllBytes(out.toPath()));onlyOutput(dir,out);
  }finally{clean(dir);}
 }
 @Test(timeout=8000) public void aesRotationAndExplicitIvRemainCorrectWithParallelFetch()throws Exception{
  File dir=directory(),out=new File(dir,"result");Hls.Playlist p=playlist(9);Map<String,byte[]> keys=new HashMap<>();byte[] a=new byte[16],b=new byte[16];Arrays.fill(b,(byte)7);keys.put("a",a);keys.put("b",b);
  for(int i=0;i<8;i++){Hls.Segment s=p.segments.get(i);s.key=i<4?"a":"b";if(i==4)s.iv="0x1234";}
  AtomicInteger loads=new AtomicInteger();
  try{
   Hls.assemble(p,out,()->{},(d,t,n,r)->{},(i,f,stop,count)->{
    Hls.Segment s=p.segments.get(i);byte[] data=packet(i);
    if(!s.key.isEmpty()){byte[] iv=new byte[16];long seq=s.iv.isEmpty()?s.sequence:0x1234;for(int k=15;k>=0;k--){iv[k]=(byte)seq;seq>>>=8;}
     Cipher c=Cipher.getInstance("AES/CBC/PKCS5Padding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(keys.get(s.key),"AES"),new IvParameterSpec(iv));data=c.doFinal(data);}
    java.nio.file.Files.write(f.toPath(),data);count.bytes(data.length);
   },key->{loads.incrementAndGet();return keys.get(key);},()->{});
   ByteArrayOutputStream expected=new ByteArrayOutputStream();for(int i=0;i<9;i++)expected.write(packet(i));
   assertArrayEquals(expected.toByteArray(),java.nio.file.Files.readAllBytes(out.toPath()));assertEquals(2,loads.get());onlyOutput(dir,out);
  }finally{clean(dir);}
 }
 @Test(timeout=8000) public void failureOfLaterSegmentCancelsEarlierBlockedTransferAndRemovesPartial()throws Exception{
  File dir=directory(),out=new File(dir,"result");CountDownLatch entered=new CountDownLatch(3),abort=new CountDownLatch(1);AtomicInteger active=new AtomicInteger();
  try{
   try{Hls.assemble(playlist(8),out,()->{},(d,t,b,r)->{},(i,f,stop,count)->{
    active.incrementAndGet();try{java.nio.file.Files.write(f.toPath(),packet(i));entered.countDown();assertTrue(entered.await(2,TimeUnit.SECONDS));if(i==2)throw new IOException("fixture failure");
     assertTrue(abort.await(2,TimeUnit.SECONDS));stop.check();
    }finally{active.decrementAndGet();}
   },key->new byte[16],abort::countDown);fail();}catch(IOException e){assertEquals("fixture failure",e.getMessage());}
   assertEquals(0,active.get());assertEquals(0,dir.list().length);
  }finally{clean(dir);}
 }
 @Test(timeout=8000) public void cancellationWhileNoBytesArriveClosesAllWorkers()throws Exception{
  File dir=directory(),out=new File(dir,"result");CountDownLatch entered=new CountDownLatch(3),abort=new CountDownLatch(1);AtomicInteger active=new AtomicInteger();
  try{
   try{Hls.assemble(playlist(10),out,()->{if(entered.getCount()==0)throw new InterruptedIOException("user cancelled");},(d,t,b,r)->{},(i,f,stop,count)->{
    active.incrementAndGet();try{entered.countDown();assertTrue(abort.await(3,TimeUnit.SECONDS));stop.check();}finally{active.decrementAndGet();}
   },key->new byte[16],abort::countDown);fail();}catch(InterruptedIOException expected){}
   assertEquals(0,active.get());assertEquals(0,dir.list().length);
  }finally{clean(dir);}
 }
 @Test(timeout=8000) public void malformedSegmentNeverLeavesAnOutput()throws Exception{
  File dir=directory(),out=new File(dir,"result");
  try{
   try{Hls.assemble(playlist(4),out,()->{},(d,t,b,r)->{},(i,f,stop,count)->java.nio.file.Files.write(f.toPath(),new byte[188]),key->new byte[16],()->{});fail();}
   catch(IOException expected){assertTrue(expected.getMessage().contains("TS"));}
   assertEquals(0,dir.list().length);
  }finally{clean(dir);}
 }
 @Test(timeout=8000) public void fmp4InitAndFragmentsKeepOrderAndBytes()throws Exception{
  File dir=directory(),out=new File(dir,"result");Hls.Playlist p=playlist(4);p.extension="mp4";p.segments.get(0).init=true;
  try{Hls.assemble(p,out,()->{},(d,t,b,r)->{},(i,f,stop,count)->java.nio.file.Files.write(f.toPath(),new byte[]{(byte)i}),key->new byte[16],()->{});
   assertArrayEquals(new byte[]{0,1,2,3},java.nio.file.Files.readAllBytes(out.toPath()));
  }finally{clean(dir);}
 }
 @Test public void rateUsesRollingWindowAndDecaysDuringStall(){
  SegmentPipeline.Rate rate=new SegmentPipeline.Rate(0);assertEquals(2000,rate.sample(500_000_000L,1000),.001);
  assertEquals(2000,rate.sample(1_000_000_000L,2000),.001);
  for(int i=3;i<=9;i++)rate.sample(i*500_000_000L,2000);
  assertEquals(0,rate.sample(5_000_000_000L,2000),.001);
  assertEquals("100 KiB/s",SegmentPipeline.formatRate(100*1024));
  assertEquals("3.5 MiB/s",SegmentPipeline.formatRate(3.5*1048576));
 }
 @Test public void cancellationGroupDoesNotCancelUnrelatedPlayerRequests()throws Exception{
  OkHttpClient http=new OkHttpClient();Request req=new Request.Builder().url("https://example.com").build();
  Call a=http.newCall(req),b=http.newCall(req),player=http.newCall(req);Net.CallGroup group=new Net.CallGroup();
  try(Net.Ticket ta=group.attach(a);Net.Ticket tb=group.attach(b)){
   group.close();assertTrue(a.isCanceled());assertTrue(b.isCanceled());assertFalse(player.isCanceled());
   try{group.attach(http.newCall(req));fail();}catch(InterruptedIOException expected){}
  }
 }
}
