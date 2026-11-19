package vn.streampocket.mobile;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;
/** Coalesces drag requests; at most one bounded bitmap is decoded at a time. Local files only. */
final class FramePreview implements AutoCloseable {
 interface Result {void ready(long generation,long time,Bitmap bitmap);}
 static final class Request {final String uri;final long generation,time;final Result result;Request(String uri,long generation,long time,Result result){this.uri=uri;this.generation=generation;this.time=time;this.result=result;}}
 final Context context;final Handler main=new Handler(Looper.getMainLooper());final ExecutorService worker=Executors.newSingleThreadExecutor();
 Request pending;boolean running,closed;
 FramePreview(Context c){context=c.getApplicationContext();}
 synchronized void request(String uri,long generation,long time,Result result){
  if(closed||!uri.startsWith("content://"))return;
  pending=new Request(uri,generation,time,result);if(!running){running=true;worker.execute(this::drain);}
 }
 void drain(){
  while(true){
   Request r;synchronized(this){if(closed||pending==null){running=false;return;}r=pending;pending=null;}
   Bitmap bitmap=null;
   try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){
    retriever.setDataSource(context,Uri.parse(r.uri));
    bitmap=retriever.getScaledFrameAtTime(r.time*1000,MediaMetadataRetriever.OPTION_CLOSEST,1280,1280);
   }catch(Exception|OutOfMemoryError ignored){}
   final Bitmap result=bitmap;
   main.post(()->{synchronized(FramePreview.this){if(closed){if(result!=null)result.recycle();return;}}r.result.ready(r.generation,r.time,result);});
  }
 }
 synchronized void clear(){pending=null;}
 @Override public synchronized void close(){closed=true;pending=null;worker.shutdownNow();}
}
