package vn.streampocket.mobile;
import java.util.*;
import org.json.*;
final class SourceImages {
 private static final Map<String,String> pages=new LinkedHashMap<>(32,.75f,true);
 static synchronized String remember(String page,String url)throws Exception{
  if(!SourceSites.known(Net.origin(page)))throw new java.io.IOException("Ảnh không đến từ trang nguồn.");
  url=RemoteAccess.publicUrl(url);pages.put(url,page);
  while(pages.size()>2048)pages.remove(pages.keySet().iterator().next());
  return url;
 }
 static synchronized JSONObject context(String url)throws Exception{
  url=RemoteAccess.publicUrl(url);String page=pages.get(url);
  if(page==null){Net.allowed(url);page=Sources.MAIN+"/";}
  return new RemoteAccess.Context(Json.obj("page",page),url,false);
 }
}
