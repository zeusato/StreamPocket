package vn.streampocket.mobile;
import org.json.*;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.util.*;
final class UpdateRelease {
 static final String REPO="zeusato/StreamPocket",PACKAGE="vn.streampocket.mobile";
 static final String LATEST="https://api.github.com/repos/"+REPO+"/releases/latest";
 static final long MAX_APK=200L*1024*1024;
 static String tag(JSONObject release)throws Exception{
  String tag=release.getString("tag_name");
  if(release.optBoolean("draft")||release.optBoolean("prerelease")||!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("Bản phát hành chưa ổn định hoặc sai định dạng.");
  return tag;
 }
 static String assetUrl(String tag,String name)throws IOException{
  if(!tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+")||!name.matches("[A-Za-z0-9._-]+"))throw new IOException("Tên gói cập nhật không hợp lệ.");
  return "https://github.com/"+REPO+"/releases/download/"+tag+"/"+name;
 }
 static JSONObject asset(JSONObject release,String name)throws Exception{
  String expected=assetUrl(tag(release),name);JSONArray assets=release.getJSONArray("assets");
  for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(name.equals(a.optString("name"))){
   if(!expected.equals(a.optString("browser_download_url"))||!"uploaded".equals(a.optString("state")))throw new IOException("Địa chỉ gói cập nhật không khớp repo.");
   return a;
  }}throw new IOException("Release thiếu file "+name+".");
 }
 static final class Info {
  final long code,size;final String version,url,sha256,notes;
  Info(JSONObject data)throws Exception{
   code=data.getLong("versionCode");size=data.getLong("size");version=data.getString("versionName");sha256=data.getString("sha256");url=data.getString("url");notes=data.optString("notes");
   if(!PACKAGE.equals(data.optString("packageName"))||code<1||size<1||size>MAX_APK||!sha256.matches("[0-9a-f]{64}")||!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!assetUrl("v"+version,"Stream-Pocket-"+version+".apk").equals(url))throw new IOException("Thông tin cập nhật không hợp lệ.");
  }
  JSONObject json(){return Json.obj("packageName",PACKAGE,"versionCode",code,"versionName",version,"size",size,"sha256",sha256,"url",url,"notes",notes);}
 }
 static Info parse(JSONObject release,JSONObject manifest)throws Exception{
  String version=manifest.getString("versionName"),name="Stream-Pocket-"+version+".apk";
  if(!tag(release).equals("v"+version))throw new IOException("Phiên bản manifest không khớp release.");
  JSONObject apk=asset(release,name);
  if(!name.equals(manifest.optString("apk"))||apk.getLong("size")!=manifest.getLong("size"))throw new IOException("Kích thước hoặc tên APK không khớp.");
  JSONObject data=new JSONObject(manifest.toString());data.put("url",apk.getString("browser_download_url"));
  String notes=release.optString("body");data.put("notes",notes.substring(0,Math.min(6000,notes.length())));
  return new Info(data);
 }
 static void downloadHost(String url)throws IOException{
  URI u;
  try{u=URI.create(url);}catch(Exception e){throw new IOException("Link cập nhật không hợp lệ.");}
  if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||u.getPort()!=-1||!Set.of("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(u.getHost()))throw new IOException("Máy chủ cập nhật không hợp lệ.");
 }
 interface Stop {void check()throws IOException;}
 interface Progress {void update(long count);}
 static void copyVerified(InputStream in,OutputStream out,Info info,Stop stop,Progress progress)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long count=0;int n;
  while((n=in.read(buffer))!=-1){stop.check();count+=n;if(count>info.size)throw new IOException("APK lớn hơn kích thước công bố.");digest.update(buffer,0,n);out.write(buffer,0,n);progress.update(count);}
  stop.check();
  if(count!=info.size||!hex(digest.digest()).equals(info.sha256))throw new IOException("APK tải chưa đầy đủ hoặc sai SHA-256. Tải lại bản cập nhật.");
 }
 static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
}
