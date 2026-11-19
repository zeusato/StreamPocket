package vn.streampocket.mobile;

import org.json.*;
import org.jsoup.nodes.*;
import java.time.Duration;
import java.util.*;

final class SeriesMetadata {
 private SeriesMetadata() {}
 static String cover(Document page, JSONObject config, JSONObject schema, String base) {
  List<String> candidates=new ArrayList<>();
  candidates.add(config.optString("coverSrc"));candidates.add(config.optString("posterSrc"));
  Object thumbnail=schema.opt("thumbnailUrl");
  if(thumbnail instanceof JSONArray){JSONArray list=(JSONArray)thumbnail;for(int i=0;i<list.length();i++)if(list.opt(i) instanceof String)candidates.add(list.optString(i));}
  else if(thumbnail instanceof String)candidates.add((String)thumbnail);
  Element meta=page.selectFirst("meta[property=og:image]");if(meta!=null)candidates.add(meta.attr("content"));
  for(String candidate:candidates){
   if(candidate==null||candidate.isBlank())continue;
   try{String url=Net.resolve(base,candidate.trim());return RemoteAccess.publicUrl(url);}catch(Exception ignored){}
  }
  return "";
 }
 static String duration(JSONObject config, JSONObject schema, int episode) {
  JSONObject tracking=config.optJSONObject("trackingEpisodes");
  JSONObject item=tracking==null?null:tracking.optJSONObject(String.valueOf(episode));
  double seconds=item!=null&&item.optInt("episode",-1)==episode?item.optDouble("drama_duration",0):0;
  if((!Double.isFinite(seconds)||seconds<=0)&&config.optInt("ep",-1)==episode){
   try{seconds=Duration.parse(schema.optString("duration")).toMillis()/1000.0;}catch(Exception ignored){seconds=0;}
  }
  if(!Double.isFinite(seconds)||seconds<=0||seconds>Integer.MAX_VALUE)return "";
  long value=Math.round(seconds);
  if(value<=0)return "";
  return value>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",value/3600,value/60%60,value%60):String.format(Locale.ROOT,"%02d:%02d",value/60,value%60);
 }
}
