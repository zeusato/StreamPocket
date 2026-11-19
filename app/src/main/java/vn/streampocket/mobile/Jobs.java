package vn.streampocket.mobile;
import android.content.*;
import org.json.*;
import java.util.*;
public final class Jobs {
 private static JSONArray jobs;
 private static long lastProgressSave;
 public static synchronized void init(Context c){
  if(jobs!=null)return;
  try{jobs=new JSONArray(new Vault(c).get("jobs","[]"));
   for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(List.of("running","queued").contains(j.optString("state"))){Json.put(j,"state","interrupted");Json.put(j,"message","Tác vụ bị gián đoạn. Bấm thử lại để chạy lại.");}}
  }catch(Exception e){jobs=new JSONArray();}
 }
 public static synchronized JSONArray list(Context c)throws Exception{init(c);return new JSONArray(jobs.toString());}
 public static synchronized String enqueue(Context c,JSONObject spec)throws Exception{
  init(c);if(jobs.length()>=150)throw new Exception("Hàng đợi đã đầy. Xóa lịch sử đã hoàn tất.");
  String id=UUID.randomUUID().toString();jobs.put(Json.obj("id",id,"state","queued","progress",0,"message","Đang chờ","title",spec.optString("title","Video"),"spec",spec,"results",new JSONArray()));
  save(c);return id;
 }
 public static synchronized JSONObject next(Context c)throws Exception{
  init(c);for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.optString("state").equals("queued")){Json.put(j,"state","running");save(c);return j;}}return null;
 }
 public static synchronized void update(Context c,JSONObject j,String state,String message,int percent)throws Exception{
  if("cancelled".equals(j.optString("state"))&&"running".equals(state))return;
  Json.put(j,"state",state);Json.put(j,"message",message);Json.put(j,"progress",percent);
  long now=System.currentTimeMillis();if(!"running".equals(state)||now-lastProgressSave>=3000){save(c);lastProgressSave=now;}
 }
 public static synchronized void result(Context c,JSONObject j,JSONObject result)throws Exception{j.getJSONArray("results").put(result);save(c);}
 public static synchronized void retry(Context c,String id)throws Exception {
  for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.getString("id").equals(id)){
   if(!List.of("error","interrupted","cancelled").contains(j.optString("state")))throw new Exception("Tác vụ chưa thể thử lại.");
   if(j.getJSONArray("results").length()>0)throw new Exception("Tác vụ đã có kết quả. Chọn riêng các file còn lại để tránh tạo lại.");
   Json.put(j,"state","queued");Json.put(j,"message","Đang chờ");save(c);return;
  }}throw new Exception("Không tìm thấy tác vụ.");
 }
 public static synchronized void clear(Context c,boolean toolsOnly)throws Exception{
  init(c);JSONArray keep=new JSONArray();for(int i=0;i<jobs.length();i++){
   JSONObject j=jobs.getJSONObject(i),spec=j.optJSONObject("spec");String mode=spec==null?"download":spec.optString("mode","download");
   if(List.of("running","queued").contains(j.optString("state"))||(toolsOnly&&!List.of("compact","merge").contains(mode)))keep.put(j);
  }jobs=keep;save(c);
 }
 public static synchronized boolean cancelled(JSONObject j){return j.optString("state").equals("cancelled");}
 public static synchronized void cancel(Context c,String id)throws Exception{
  init(c);for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(j.getString("id").equals(id)&&List.of("running","queued").contains(j.optString("state")))update(c,j,"cancelled","Đã hủy",j.optInt("progress"));}
 }
 public static synchronized void failActive(Context c,String message)throws Exception{
  init(c);for(int i=0;i<jobs.length();i++){JSONObject j=jobs.getJSONObject(i);if(List.of("queued","running").contains(j.optString("state"))){Json.put(j,"state","error");Json.put(j,"message",message);}}save(c);
 }
 private static void save(Context c)throws Exception{new Vault(c).put("jobs",jobs.toString());}
}
