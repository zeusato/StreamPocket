package vn.streampocket.mobile;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class JobService extends Service {
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 Media media;
 boolean working=false;
 volatile boolean stopped=false;
 PowerManager.WakeLock wake;
 public static void start(Context c){try{c.startForegroundService(new Intent(c,JobService.class));}catch(RuntimeException e){Diagnostics.record(c,e);try{Jobs.failActive(c,"Android chưa cho chạy tác vụ nền. Mở app rồi bấm thử lại.");}catch(Exception ignored){}}}
 @Override public void onCreate(){super.onCreate();media=new Media(this);Jobs.init(this);NotificationManager n=getSystemService(NotificationManager.class);n.createNotificationChannel(new NotificationChannel("jobs","Tải và xử lý video",NotificationManager.IMPORTANCE_LOW));}
 Notification notification(String text){
  PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,"jobs").setSmallIcon(R.drawable.ic_app).setContentTitle("Stream Pocket").setContentText(text).setContentIntent(open).setOngoing(true).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  try{startForeground(1,notification("Đang xử lý hàng đợi"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);}catch(RuntimeException e){Diagnostics.record(this,e);try{Jobs.failActive(this,"Không khởi động được tải nền. Xem mục Chẩn đoán lỗi.");}catch(Exception ignored){}stopSelf();return START_NOT_STICKY;}
  if(!working){working=true;stopped=false;worker.execute(()->{
   wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"StreamPocket:jobs");
   try{wake.acquire(6*60*60*1000L);JSONObject job;while(!stopped&&(job=Jobs.next(this))!=null)execute(job);}
   catch(Exception e){Diagnostics.record(this,e);try{Jobs.failActive(this,"Tác vụ bị dừng: "+Diagnostics.clean(Objects.toString(e.getMessage(),"Lỗi hệ thống")));}catch(Exception ignored){}}
   finally{if(wake.isHeld())wake.release();new Handler(getMainLooper()).post(()->{working=false;try{boolean queued=false;JSONArray pending=Jobs.list(this);for(int i=0;i<pending.length();i++)if(pending.getJSONObject(i).optString("state").equals("queued"))queued=true;if(queued&&!stopped){start(this);return;}}catch(Exception ignored){}stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});}
  });}return START_NOT_STICKY;
 }
 @Override public void onTimeout(int startId,int fgsType){stopped=true;media.cancel();stopSelf();}
 @Override public void onDestroy(){stopped=true;media.cancel();worker.shutdown();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
 void check(JSONObject j)throws Exception{if(stopped||Jobs.cancelled(j)||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Đã hủy hoặc Android đã dừng tác vụ.");}
 long lastUpdate,lastDiagnostic;
 void progress(JSONObject j,double fraction,String text){
  long now=System.currentTimeMillis();if(now-lastUpdate<450)return;lastUpdate=now;
  try{if(Jobs.cancelled(j))return;Jobs.update(this,j,"running",text,(int)Math.max(0,Math.min(99,fraction*100)));getSystemService(NotificationManager.class).notify(1,notification(text));if(now-lastDiagnostic>3000){Diagnostics.phase(this,text);lastDiagnostic=now;}}catch(Exception ignored){}
 }
 void execute(JSONObject j){
  File dir=new File(getCacheDir(),"job-"+j.optString("id"));dir.mkdirs();
  try {
   Diagnostics.phase(this,"Bắt đầu tác vụ");
   JSONObject spec=j.getJSONObject("spec");String mode=spec.optString("mode","download");
   JSONArray items=spec.getJSONArray("items");boolean local=mode.equals("compact")||mode.equals("merge"),merge=mode.equals("merge")||spec.optBoolean("merge"),compact=mode.equals("compact")||spec.optBoolean("compact");
   if(items.length()<(merge?2:1))throw new Exception("Chọn đủ video trước khi thực thi.");
   List<File> inputs=new ArrayList<>();List<JSONObject> infos=new ArrayList<>();List<String> exts=new ArrayList<>();
   for(int i=0;i<items.length();i++){
    check(j);final int index=i;JSONObject item=items.getJSONObject(i);File input=new File(dir,"source-"+i);
    if(local){progress(j,i*.35/items.length(),"Đang đọc file "+(i+1)+"/"+items.length());Files.copy(this,item.getString("uri"),input,()->check(j));exts.add("mp4");}
    else{
     progress(j,i*.5/items.length(),"Đang lấy nguồn "+(i+1)+"/"+items.length());
     JSONObject req=new JSONObject(item.toString());Json.put(req,"token",spec.optString("token"));JSONObject source=Sources.media(req);
     String ext=Hls.download(source,input,()->check(j),(complete,total,bytes,rate)->progress(j,(index+complete/(double)total)*(merge||compact?.5:.9)/items.length(),
       "Video "+(index+1)+"/"+items.length()+" · "+complete+"/"+total+" đoạn · "+String.format(Locale.US,"%.1f MiB/s",rate/1048576)));
     exts.add(ext);
    }
    inputs.add(input);
    if(merge||compact)infos.add(media.probe(input));
   }
   if(merge){
    File joined=new File(dir,"joined.mp4");boolean copied=false;
    if(!compact && media.compatible(inputs)){
     try{
      media.join(inputs,joined,()->check(j),(f,text)->progress(j,.5+.49*f,"Ghép nhanh · giữ nguyên luồng video"));
      copied=true;
     }catch(Exception e){check(j);joined.delete();}
    }
    if(!copied){
     JSONObject first=infos.get(0),canvas=Json.obj("width",(first.getInt("width")+1)/2*2,"height",(first.getInt("height")+1)/2*2);
     List<File> parts=new ArrayList<>();
     for(int i=0;i<inputs.size();i++){check(j);final int index=i;File part=new File(dir,"part-"+i+".mp4");
      media.encode(inputs.get(i),part,compact,canvas,()->check(j),(f,text)->progress(j,.5+.45*(index+f)/inputs.size(),text+" "+(index+1)+"/"+inputs.size()));
      parts.add(part);inputs.get(i).delete();
     }
     media.join(parts,joined,()->check(j),(f,text)->progress(j,.95+.04*f,text));
    }
    JSONObject saved=Files.publish(this,joined,spec.optString("title","Ghep video")+" - ghep "+items.length(),"mp4",()->check(j));
    Jobs.result(this,j,saved);
    if(local&&spec.optBoolean("deleteOriginals"))for(int i=0;i<items.length();i++){
     check(j);String message=infos.get(i).optBoolean("extraTracks")?"Giữ bản gốc có thêm track.":Files.delete(this,items.getJSONObject(i));
     Jobs.result(this,j,Json.obj("name",items.getJSONObject(i).optString("name"),"message",message));
    }
   }else{
    for(int i=0;i<inputs.size();i++){check(j);final int index=i;JSONObject item=items.getJSONObject(i);File input=inputs.get(i),result=input;String ext=exts.get(i);
     if(compact){File encoded=new File(dir,"compact-"+i+".mp4");media.encode(input,encoded,true,null,()->check(j),(f,text)->progress(j,.5+.49*(index+f)/inputs.size(),text+" "+(index+1)+"/"+inputs.size()));
      if(encoded.length()<input.length()){result=encoded;ext="mp4";}
     }
     if(local&&result==input){Jobs.result(this,j,Json.obj("name",item.optString("name"),"message","Giữ bản gốc vì bản nén không nhỏ hơn."));continue;}
     if(!local&&result==input){File mp4=new File(dir,"saved-"+i+".mp4");media.remux(input,mp4,()->check(j),(f,text)->progress(j,.9+.09*f,text));result=mp4;ext="mp4";}
     String name=item.optString("title",item.optString("name","Video")).replaceFirst("(?i)\\.(mp4|mkv|ts|mov|webm)$","");
     JSONObject saved=Files.publish(this,result,name+(compact?" - optimized":""),ext,()->check(j));Jobs.result(this,j,saved);
     if(local&&spec.optBoolean("deleteOriginals")){
      check(j);String message=infos.get(i).optBoolean("extraTracks")?"Giữ bản gốc có thêm track.":Files.delete(this,item);Jobs.result(this,j,Json.obj("name",name,"message",message));
     }
    }
   }
   check(j);Jobs.update(this,j,"done","Đã hoàn tất",100);Diagnostics.phase(this,"Đã hoàn tất");
   getSystemService(NotificationManager.class).notify(2,new Notification.Builder(this,"jobs").setSmallIcon(R.drawable.ic_app).setContentTitle("Đã hoàn tất · Stream Pocket").setContentText(j.optString("title")).setContentIntent(PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT)).setAutoCancel(true).build());
  }catch(Exception|LinkageError e){Diagnostics.record(this,e);try{Jobs.update(this,j,Jobs.cancelled(j)?"cancelled":"error",Objects.toString(e.getMessage(),"Không xử lý được video."),j.optInt("progress"));}catch(Exception ignored){}}
  finally{File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}
 }
}
