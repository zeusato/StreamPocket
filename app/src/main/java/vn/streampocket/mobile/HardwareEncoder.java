package vn.streampocket.mobile;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.*;
import com.google.common.collect.ImmutableList;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class HardwareEncoder {
 final Context context;
 final Handler handler=new Handler(Looper.getMainLooper());
 volatile Transformer current;
 public HardwareEncoder(Context c){context=c.getApplicationContext();}
 public void cancel(){handler.post(()->{if(current!=null)current.cancel();});}
 static ImmutableList<MediaCodecInfo> encoders(String mime){
  ImmutableList.Builder<MediaCodecInfo> result=ImmutableList.builder();
  for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
   if(!info.isEncoder()||!info.isHardwareAccelerated())continue;
   for(String supported:info.getSupportedTypes())if(supported.equalsIgnoreCase(mime))result.add(info);
  }return result.build();
 }
 public static String capabilities(){
  return !encoders(MimeTypes.VIDEO_H265).isEmpty()?"H.265 bằng phần cứng":!encoders(MimeTypes.VIDEO_H264).isEmpty()?"H.264 bằng phần cứng · máy chưa có bộ mã hóa H.265":"Thiết bị chưa có bộ mã hóa phần cứng phù hợp";
 }
 public String encode(File input,File output,boolean compact,int width,int height,Hls.Stop stop,Media.Progress progress)throws Exception {
  String mime=compact&&!encoders(MimeTypes.VIDEO_H265).isEmpty()?MimeTypes.VIDEO_H265:MimeTypes.VIDEO_H264;
  if(encoders(mime).isEmpty())throw new IOException("Thiết bị thiếu bộ mã hóa phần cứng. Chọn tải nguyên bản hoặc ghép các file cùng định dạng.");
  String label=(mime.equals(MimeTypes.VIDEO_H265)?"H.265":"H.264")+" · bộ mã hóa phần cứng";
  CountDownLatch done=new CountDownLatch(1);Exception[] error={null};boolean[] cancelled={false};
  handler.post(()->{
   try {
    int bitrate=(int)Math.min(40_000_000,Math.max(500_000,(double)width*height*30*(compact?.065:.14)));
    DefaultEncoderFactory factory=new DefaultEncoderFactory.Builder(context).setVideoEncoderSelector(HardwareEncoder::encoders)
      .setEnableFallback(true).setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(bitrate).build()).build();
    current=new Transformer.Builder(context).setVideoMimeType(mime).setAudioMimeType(MimeTypes.AUDIO_AAC).setEncoderFactory(factory)
      .addListener(new Transformer.Listener(){
       @Override public void onCompleted(Composition composition,ExportResult result){done.countDown();}
       @Override public void onError(Composition composition,ExportResult result,ExportException ex){error[0]=new IOException("Bộ mã hóa Android không xử lý được độ phân giải/định dạng này ("+ex.getErrorCodeName()+").",ex);done.countDown();}
      }).build();
    EditedMediaItem item=new EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(input))).setEffects(new Effects(List.of(),List.of(Presentation.createForWidthAndHeight(width,height,Presentation.LAYOUT_SCALE_TO_FIT)))).build();
    current.start(item,output.getAbsolutePath());
    handler.post(new Runnable(){@Override public void run(){
     if(done.getCount()==0||cancelled[0])return;
     try{ProgressHolder holder=new ProgressHolder();if(current!=null&&current.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE)progress.update(holder.progress/100.0,label);}
     catch(Exception ignored){}handler.postDelayed(this,600);
    }});
   }catch(Exception e){error[0]=e;done.countDown();}
  });
  try {
   while(!done.await(250,TimeUnit.MILLISECONDS))stop.check();
   stop.check();if(error[0]!=null)throw error[0];return mime.equals(MimeTypes.VIDEO_H265)?"H265":"H264";
  }finally{
   CountDownLatch released=new CountDownLatch(1);
   handler.post(()->{cancelled[0]=true;if(current!=null){current.cancel();current=null;}released.countDown();});
   released.await(5,TimeUnit.SECONDS);
  }
 }
}
