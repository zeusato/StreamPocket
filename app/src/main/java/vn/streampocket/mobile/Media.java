package vn.streampocket.mobile;
import com.arthenica.ffmpegkit.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class Media {
 public interface Progress{void update(double fraction,String stage);}
 public volatile long sessionId=-1;
 final HardwareEncoder hardware;
 public Media(android.content.Context context){hardware=new HardwareEncoder(context);}
 public void cancel(){hardware.cancel();if(sessionId>=0)FFmpegKit.cancel(sessionId);}
 public JSONObject probe(File file)throws Exception{
  MediaInformationSession s=FFprobeKit.getMediaInformation(file.getAbsolutePath());
  if(s.getMediaInformation()==null)throw new IOException("Không đọc được thông tin video.");
  JSONObject all=s.getMediaInformation().getAllProperties();JSONArray streams=all.getJSONArray("streams");JSONObject video=null;boolean audio=false;
  for(int i=0;i<streams.length();i++){JSONObject stream=streams.getJSONObject(i);if(stream.optString("codec_type").equals("video")&&stream.optJSONObject("disposition")!=null&&stream.getJSONObject("disposition").optInt("attached_pic")==1)continue;
   if(stream.optString("codec_type").equals("video")&&video==null)video=stream;if(stream.optString("codec_type").equals("audio"))audio=true;}
  if(video==null)throw new IOException("File thiếu hình ảnh.");
  double duration=Double.parseDouble(all.getJSONObject("format").optString("duration","0"));if(!(duration>0))throw new IOException("Thời lượng không hợp lệ.");
  int w=video.optInt("width"),h=video.optInt("height");JSONArray sides=video.optJSONArray("side_data_list");
  if(sides!=null)for(int i=0;i<sides.length();i++)if(Math.abs(sides.getJSONObject(i).optInt("rotation"))%180==90){int tmp=w;w=h;h=tmp;}
  return Json.obj("duration",duration,"width",w,"height",h,"audio",audio,"extraTracks",streams.length()>(audio?2:1));
 }
 void run(List<String> args,double duration,Hls.Stop stop,Progress p,String stage)throws Exception{
  stop.check();CountDownLatch done=new CountDownLatch(1);FFmpegSession[] result={null};
  FFmpegSession session=FFmpegKit.executeWithArgumentsAsync(args.toArray(new String[0]),s->{result[0]=s;done.countDown();},log->{},stats->p.update(Math.min(.99,stats.getTime()/1000.0/Math.max(1,duration)),stage));
  sessionId=session.getSessionId();
  try{while(!done.await(200,TimeUnit.MILLISECONDS))try{stop.check();}catch(Exception e){cancel();done.await(10,TimeUnit.SECONDS);throw e;}
   stop.check();if(result[0]==null||!ReturnCode.isSuccess(result[0].getReturnCode()))throw new IOException("FFmpeg không xử lý được video. Kiểm tra định dạng hoặc dung lượng trống.");
  }finally{sessionId=-1;}
 }
 public String encode(File input,File output,boolean compact,JSONObject canvas,Hls.Stop stop,Progress progress)throws Exception{
  JSONObject info=probe(input);
  int width=(info.getInt("width")+1)/2*2,height=(info.getInt("height")+1)/2*2;
  File ready=input;
  if(canvas!=null){
   width=canvas.getInt("width");height=canvas.getInt("height");
   ready=new File(output.getParentFile(),"audio-"+output.getName());
   boolean audio=info.getBoolean("audio");
   List<String> args=new ArrayList<>(List.of("-nostdin","-v","error","-xerror","-y","-i",input.getAbsolutePath()));
   if(!audio)args.addAll(List.of("-f","lavfi","-i","anullsrc=r=48000:cl=stereo"));
   args.addAll(List.of("-map","0:V:0","-map",audio?"0:a:0":"1:a:0","-c:v","copy","-c:a","aac","-ar","48000","-ac","2","-af","apad","-shortest",ready.getAbsolutePath()));
   run(args,info.getDouble("duration"),stop,(f,t)->progress.update(f*.05,"Chuẩn bị âm thanh"),"Chuẩn bị âm thanh");
  }
  try{
   String codec=hardware.encode(ready,output,compact,width,height,stop,(f,t)->progress.update(.05+.95*f,t));
   verify(output,info.getDouble("duration"),info.getBoolean("audio"));return codec;
  }finally{if(ready!=input)ready.delete();}
 }
 public boolean compatible(List<File> inputs){
  String first=null;
  try{
   for(File input:inputs){
    android.media.MediaExtractor extractor=new android.media.MediaExtractor();
    StringBuilder signature=new StringBuilder();
    try{
     extractor.setDataSource(input.getAbsolutePath());
     for(int i=0;i<extractor.getTrackCount();i++){
      android.media.MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString("mime");
      if(!List.of("video/avc","video/hevc","audio/mp4a-latm").contains(mime))return false;
      signature.append(mime);
      for(String key:List.of("width","height","sample-rate","channel-count","rotation-degrees"))if(f.containsKey(key))signature.append(key).append(f.getInteger(key));
      for(String key:List.of("csd-0","csd-1","csd-2"))if(f.containsKey(key)){java.nio.ByteBuffer bytes=f.getByteBuffer(key).duplicate();while(bytes.hasRemaining())signature.append(String.format("%02x",bytes.get()));}
     }
    }finally{extractor.release();}
    if(first==null)first=signature.toString();else if(!first.equals(signature.toString()))return false;
   }return first!=null;
  }catch(Exception e){return false;}
 }
 public void remux(File input,File output,Hls.Stop stop,Progress progress)throws Exception{
  JSONObject info=probe(input);
  run(List.of("-nostdin","-v","error","-xerror","-y","-fflags","+genpts","-i",input.getAbsolutePath(),"-map","0:V:0","-map","0:a:0?","-c","copy","-movflags","+faststart",output.getAbsolutePath()),info.getDouble("duration"),stop,progress,"Đang lưu MP4");
  verify(output,info.getDouble("duration"),info.getBoolean("audio"));
 }
 public void join(List<File> parts,File output,Hls.Stop stop,Progress progress)throws Exception{
  File list=new File(output.getParentFile(),"concat.txt");double duration=0;StringBuilder text=new StringBuilder();
  for(File f:parts){text.append("file '").append(f.getName()).append("'\n");duration+=probe(f).getDouble("duration");}
  try(FileWriter w=new FileWriter(list)){w.write(text.toString());}
  run(List.of("-nostdin","-v","error","-xerror","-y","-f","concat","-safe","0","-i",list.getAbsolutePath(),"-c","copy","-bsf:a","setts=ts='if(isnan(PREV_OUTDTS),TS,max(TS,PREV_OUTDTS+1))'","-movflags","+faststart",output.getAbsolutePath()),duration,stop,progress,"Đang nối MP4");
  verify(output,duration,probe(parts.get(0)).getBoolean("audio"));
 }
 void verify(File file,double duration,boolean audio)throws Exception{
  JSONObject out=probe(file);if(Math.abs(out.getDouble("duration")-duration)>Math.max(.7,duration*.003)||(audio&&!out.getBoolean("audio")))throw new IOException("Kết quả sai thời lượng hoặc thiếu tiếng. File gốc được giữ lại.");
 }
}
