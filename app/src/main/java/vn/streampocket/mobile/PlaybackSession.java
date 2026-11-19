package vn.streampocket.mobile;
import android.app.Activity;
import android.content.Intent;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** In-memory playlist: tokens and expiring online URLs are never written to disk. */
final class PlaybackSession {
 static final Map<String,PlaybackSession> sessions=new ConcurrentHashMap<>();
 final List<JSONObject> items;final int start;
 PlaybackSession(List<JSONObject> items,int start){this.items=List.copyOf(items);this.start=start;}
 static void open(Activity activity,List<JSONObject> items,int start){
  if(items.isEmpty()||start<0||start>=items.size())throw new IllegalArgumentException("Danh sách phát không hợp lệ.");
  String key=UUID.randomUUID().toString();sessions.put(key,new PlaybackSession(items,start));
  try{activity.startActivity(new Intent(activity,PlayerActivity.class).putExtra("session",key));}catch(RuntimeException e){sessions.remove(key);throw e;}
 }
 static List<JSONObject> online(JSONObject b)throws Exception{
  JSONArray raw=b.optJSONArray("playlist");if(raw==null)raw=Json.array(b);
  if(raw.length()>10000)throw new IllegalArgumentException("Danh sách phát quá dài.");
  List<JSONObject> list=new ArrayList<>();
  for(int i=0;i<raw.length();i++){
   JSONObject v=raw.getJSONObject(i);String id=v.optString("id");
   if(!id.matches("(?:hg-[1-9]\\d*-[1-9]\\d*|\\d+)"))throw new IllegalArgumentException("Mã video không hợp lệ.");
   list.add(Json.obj("id",id,"site",v.optString("site","main"),"title",v.optString("title","Video"),"token",b.optString("token")));
  }return list;
 }
 static int index(List<JSONObject> items,String id){for(int i=0;i<items.size();i++)if(id.equals(items.get(i).optString("id")))return i;return -1;}
}
