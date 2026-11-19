package vn.streampocket.mobile;
import android.app.*;
import android.content.*;
import android.os.Build;
import java.util.*;
final class Diagnostics {
 static void phase(Context c,String stage){c.getSharedPreferences("diagnostics",0).edit().putString("stage",stage).putLong("stageTime",System.currentTimeMillis()).apply();}
 static void record(Context c,Throwable error){
  try{
   StringBuilder text=new StringBuilder(error.getClass().getSimpleName()+": "+clean(Objects.toString(error.getMessage(),"")));
   int count=0;for(StackTraceElement frame:error.getStackTrace()){if(frame.getClassName().startsWith("vn.streampocket")&&count++<10)text.append("\n").append(frame);}
   c.getSharedPreferences("diagnostics",0).edit().putString("lastError",text.toString()).putLong("errorTime",System.currentTimeMillis()).commit();
  }catch(Throwable ignored){}
 }
 static void imageError(Context c,Throwable error){c.getSharedPreferences("diagnostics",0).edit().putString("imageError",error.getClass().getSimpleName()+": "+clean(Objects.toString(error.getMessage(),""))).apply();}
 static String clean(String s){return s.replaceAll("https?://[^\\s]+","[URL]").replaceAll("(?i)(token|authorization|auth_key)[:=]\\S+","$1=[hidden]");}
 static String report(Context c){
  android.content.SharedPreferences p=c.getSharedPreferences("diagnostics",0);
  StringBuilder text=new StringBuilder("Stream Pocket "+BuildConfig.VERSION_NAME+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE);
  text.append("\nGiai đoạn gần nhất: ").append(p.getString("stage","Chưa có"));
  text.append("\nLỗi ảnh: ").append(p.getString("imageError","Chưa có"));
  text.append("\nLỗi đã ghi: ").append(p.getString("lastError","Chưa có lỗi Java được ghi."));
  if(Build.VERSION.SDK_INT>=30)try{
   List<ApplicationExitInfo> exits=c.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(c.getPackageName(),0,1);
   if(!exits.isEmpty()){ApplicationExitInfo e=exits.get(0);
    String reason;
    switch(e.getReason()){
     case ApplicationExitInfo.REASON_LOW_MEMORY:reason="Android thu hồi do thiếu RAM";break;
     case ApplicationExitInfo.REASON_CRASH:reason="Lỗi Java";break;
     case ApplicationExitInfo.REASON_CRASH_NATIVE:reason="Lỗi thư viện native";break;
     case ApplicationExitInfo.REASON_ANR:reason="Ứng dụng không phản hồi";break;
     case ApplicationExitInfo.REASON_USER_REQUESTED:reason="Dừng theo yêu cầu người dùng/hệ thống";break;
     case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:reason="Dùng quá nhiều tài nguyên";break;
     default:reason="Mã "+e.getReason();
    }
    text.append("\nLần kết thúc trước: ").append(reason).append(" · ").append(new Date(e.getTimestamp())).append("\nRAM lúc dừng: ").append(e.getPss()/1024).append(" MiB");
    if(e.getDescription()!=null)text.append("\n").append(clean(e.getDescription()));
   }
  }catch(Exception ignored){}
  return text.toString();
 }
}
