package vn.streampocket.mobile;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Three-file sliding window: network workers never write the assembled output. */
final class SegmentPipeline {
 static final int WIDTH=3;
 static String formatRate(double rate){return rate<1048576?String.format(Locale.US,"%.0f KiB/s",rate/1024):String.format(Locale.US,"%.1f MiB/s",rate/1048576);}
 interface Fetch {void run(int index,File file,Hls.Stop stop,Net.Count count)throws Exception;}
 interface Append {void run(int index,File file,Hls.Stop stop)throws Exception;}
 static final class Rate {
  private final ArrayDeque<long[]> samples=new ArrayDeque<>();
  Rate(long now){samples.add(new long[]{now,0});}
  double sample(long now,long bytes){
   while(samples.size()>1&&now-samples.peekFirst()[0]>3_000_000_000L)samples.removeFirst();
   long[] first=samples.peekFirst();double rate=(bytes-first[1])*1e9/Math.max(1,now-first[0]);
   samples.addLast(new long[]{now,bytes});return Math.max(0,rate);
  }
 }
 static void run(int total,File output,Hls.Stop stop,Hls.Progress progress,Fetch fetch,Append append,Runnable cancelNetwork)throws Exception{
  File dir=java.nio.file.Files.createTempDirectory(output.getParentFile().toPath(),"segments-").toFile();
  ExecutorService pool=Executors.newFixedThreadPool(WIDTH);
  ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor();
  AtomicReference<Exception> failure=new AtomicReference<>();AtomicBoolean ended=new AtomicBoolean();
  AtomicLong bytes=new AtomicLong();AtomicInteger done=new AtomicInteger();
  Rate rate=new Rate(System.nanoTime());Object reporting=new Object();
  Hls.Stop check=()->{
   Exception e=failure.get();if(e!=null)throw e;
   if(ended.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Đã dừng tải.");
   stop.check();
  };
  java.util.function.Consumer<Exception> fail=e->{failure.compareAndSet(null,e);cancelNetwork.run();};
  List<Future<?>> pending=new ArrayList<>();List<File> parts=new ArrayList<>();
  try{
   monitor.scheduleAtFixedRate(()->{
    synchronized(reporting){
     if(ended.get())return;
     try{check.check();long n=bytes.get();progress.update(done.get(),total,n,rate.sample(System.nanoTime(),n));}
     catch(Exception e){fail.accept(e);}
    }
   },0,500,TimeUnit.MILLISECONDS);
   for(int next=0;next<Math.min(WIDTH,total);next++){
    final int index=next;File part=new File(dir,index+".part");parts.add(part);
    pending.add(pool.submit(()->{try{check.check();fetch.run(index,part,check,n->bytes.addAndGet(n));}catch(Exception e){fail.accept(e);throw new CompletionException(e);}}));
   }
   for(int index=0;index<total;index++){
    Future<?> first=pending.get(0);
    while(true){check.check();try{first.get(150,TimeUnit.MILLISECONDS);break;}catch(TimeoutException wait){}catch(ExecutionException e){check.check();throw new IOException("Không tải được đoạn video.",e.getCause());}}
    check.check();File part=parts.remove(0);append.run(index,part,check);check.check();
    if(!part.delete())throw new IOException("Không dọn được đoạn video tạm.");
    pending.remove(0);done.incrementAndGet();
    int next=index+WIDTH;
    if(next<total){
     final int item=next;File nextPart=new File(dir,item+".part");parts.add(nextPart);
     pending.add(pool.submit(()->{try{check.check();fetch.run(item,nextPart,check,n->bytes.addAndGet(n));}catch(Exception e){fail.accept(e);throw new CompletionException(e);}}));
    }
   }
   synchronized(reporting){check.check();ended.set(true);long n=bytes.get();progress.update(total,total,n,rate.sample(System.nanoTime(),n));}
  }finally{
   synchronized(reporting){ended.set(true);}
   cancelNetwork.run();for(Future<?> f:pending)f.cancel(true);monitor.shutdownNow();pool.shutdownNow();
   // No worker may write into a job directory after its owner starts cleanup.
   boolean interrupted=Thread.interrupted();
   for(ExecutorService service:List.of(pool,monitor)){
    while(!service.isTerminated()){try{service.awaitTermination(100,TimeUnit.MILLISECONDS);}catch(InterruptedException e){interrupted=true;}}
   }
   File[] leftovers=dir.listFiles();if(leftovers!=null)for(File f:leftovers)f.delete();dir.delete();
   if(interrupted)Thread.currentThread().interrupt();
  }
 }
}
