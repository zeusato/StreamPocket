package vn.streampocket.mobile;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import org.json.*;
import okhttp3.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
final class AppUpdates {
 private static AppUpdates instance;
 static synchronized AppUpdates get(Context c){if(instance==null)instance=new AppUpdates(c.getApplicationContext());return instance;}
 final Context context;final SharedPreferences prefs;final File dir,apk,part;final long installed;
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final OkHttpClient http=Net.client.newBuilder().callTimeout(15,TimeUnit.MINUTES).build();
 volatile UpdateRelease.Info info;volatile Call active;volatile boolean cancelled;
 private String state="idle",message="Chưa kiểm tra bản cập nhật.";private int progress;private boolean busy;
 private AppUpdates(Context c){
  context=c;prefs=c.getSharedPreferences("app-updates",0);dir=new File(c.getCacheDir(),"updates");dir.mkdirs();apk=new File(dir,"StreamPocket.apk");part=new File(dir,"update.part");part.delete();
  long code=0;try{code=c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();}catch(Exception ignored){}installed=code;
  try{info=new UpdateRelease.Info(new JSONObject(prefs.getString("release","{}")));if(info.code<=installed){info=null;apk.delete();prefs.edit().remove("release").apply();}else{state=apk.isFile()&&apk.length()==info.size?"ready":"available";message=state.equals("ready")?"Đã tải xong. Bấm Cài đặt để cập nhật.":"Có bản "+info.version+".";}
  }catch(Exception ignored){info=null;}
 }
 synchronized JSONObject status(){
  return Json.obj("state",state,"message",message,"progress",progress,"busy",busy,"available",info!=null&&info.code>installed,"version",info==null?"":info.version,"size",info==null?0:info.size,"notes",info==null?"":info.notes,"lastChecked",prefs.getLong("lastChecked",0));
 }
 private synchronized void set(String next,String text,int percent){state=next;message=text;progress=percent;}
 synchronized JSONObject check(boolean force){
  long now=System.currentTimeMillis(),last=prefs.getLong("lastAttempt",0);
  if(busy||(!force&&now>=last&&now-last<24*60*60*1000L))return status();
  busy=true;state="checking";message="Đang kiểm tra GitHub Releases…";prefs.edit().putLong("lastAttempt",now).apply();
  worker.execute(()->{
   try{
    JSONObject release=json(UpdateRelease.LATEST,true);
    if(release==null){set(info==null?"current":"available","Chưa có bản phát hành mới trên GitHub.",0);return;}
    JSONObject asset=UpdateRelease.asset(release,"update.json");
    UpdateRelease.Info next=UpdateRelease.parse(release,json(asset.getString("browser_download_url"),false));
    prefs.edit().putLong("lastChecked",System.currentTimeMillis()).apply();
    if(next.code<=installed){info=null;apk.delete();prefs.edit().remove("release").apply();set("current","Đang dùng bản mới nhất.",0);}
    else{
     boolean ready=info!=null&&info.sha256.equals(next.sha256)&&apk.isFile()&&apk.length()==next.size;
     info=next;prefs.edit().putString("release",next.json().toString()).apply();set(ready?"ready":"available",ready?"Đã tải xong. Bấm Cài đặt để cập nhật.":"Có bản "+next.version+".",ready?100:0);
    }
   }catch(Exception e){set("error","Không kiểm tra được cập nhật: "+safe(e),0);}
   finally{synchronized(this){busy=false;active=null;}}
  });return status();
 }
 private Response open(String start)throws Exception{
  String url=start;
  for(int hop=0;hop<5;hop++){
   UpdateRelease.downloadHost(url);
   Request request=new Request.Builder().url(url).header("User-Agent","StreamPocket-Android-Updater").header("Accept",url.startsWith("https://api.github.com/")?"application/vnd.github+json":"*/*").build();
   Call call=http.newCall(request);active=call;Response response=call.execute();
   if(Set.of(301,302,303,307,308).contains(response.code())&&response.header("Location")!=null){url=Net.resolve(url,response.header("Location"));response.close();continue;}
   return response;
  }throw new IOException("GitHub chuyển hướng quá nhiều lần.");
 }
 private JSONObject json(String url,boolean missingOkay)throws Exception{
  try(Response response=open(url)){
   if(response.code()==404&&missingOkay)return null;
   if(!response.isSuccessful()||response.body()==null)throw new IOException("GitHub trả HTTP "+response.code()+((response.code()==403||response.code()==429)?". Thử lại sau.":""));
   return new JSONObject(new String(Net.bounded(response.body().byteStream(),1024*1024),java.nio.charset.StandardCharsets.UTF_8));
  }
 }
 synchronized JSONObject download(){
  if(busy)return status();
  UpdateRelease.Info selected=info;if(selected==null||selected.code<=installed){set("error","Kiểm tra bản cập nhật trước.",0);return status();}
  busy=true;cancelled=false;state="downloading";message="Đang tải APK…";progress=0;
  worker.execute(()->{
   try{
    if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Không tạo được thư mục cập nhật.");
    if(dir.getUsableSpace()<selected.size+32L*1024*1024)throw new IOException("Không đủ dung lượng trống để tải bản cập nhật.");
    part.delete();
    try(Response response=open(selected.url)){
     if(!response.isSuccessful()||response.body()==null)throw new IOException("Tải APK trả HTTP "+response.code());
     try(InputStream in=response.body().byteStream();FileOutputStream out=new FileOutputStream(part)){
      UpdateRelease.copyVerified(in,out,selected,()->{if(cancelled)throw new InterruptedIOException("Đã hủy tải.");},count->{
       int percent=(int)(count*100/selected.size);synchronized(this){progress=percent;message="Đang tải APK · "+percent+"%";}
      });out.getFD().sync();
     }
    }
    if(cancelled)throw new InterruptedIOException("Đã hủy tải.");
    set("downloading","Đang kiểm tra chữ ký APK…",100);validatePackage(part,selected);
    if(cancelled)throw new InterruptedIOException("Đã hủy tải.");
    java.nio.file.Files.move(part.toPath(),apk.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    set("ready","Đã kiểm tra APK. Bấm Cài đặt để cập nhật.",100);
   }catch(Exception e){part.delete();set(cancelled?"available":"error",cancelled?"Đã hủy tải cập nhật.":"Không tải được cập nhật: "+safe(e),0);}
   finally{synchronized(this){busy=false;active=null;}}
  });return status();
 }
 synchronized JSONObject cancel(){if("downloading".equals(state)){cancelled=true;message="Đang hủy tải cập nhật…";Call call=active;if(call!=null)call.cancel();}return status();}
 File prepareInstall()throws Exception{
  UpdateRelease.Info selected;
  synchronized(this){if(busy||!"ready".equals(state)||info==null)throw new IOException("Chưa có APK sẵn sàng để cài.");selected=info;}
  try{
   try(InputStream in=new FileInputStream(apk)){
    UpdateRelease.copyVerified(in,new OutputStream(){public void write(int b){}public void write(byte[] b,int off,int len){}},selected,()->{},count->{});
   }
   validatePackage(apk,selected);return apk;
  }catch(Exception e){apk.delete();set("error","APK không còn hợp lệ. Tải lại bản cập nhật.",0);throw e;}
 }
 private void validatePackage(File file,UpdateRelease.Info selected)throws Exception{
  PackageManager pm=context.getPackageManager();PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
  PackageInfo current=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
  if(archive==null||!context.getPackageName().equals(archive.packageName)||archive.getLongVersionCode()!=selected.code||archive.getLongVersionCode()<=current.getLongVersionCode()||!selected.version.equals(archive.versionName))throw new IOException("APK không đúng ứng dụng hoặc phiên bản.");
  if(archive.applicationInfo==null||archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IOException("Bản cập nhật chưa hỗ trợ Android trên máy này.");
  if(archive.signingInfo==null||current.signingInfo==null||!signers(archive).equals(signers(current)))throw new IOException("APK có chữ ký khác bản đang cài.");
 }
 private Set<String> signers(PackageInfo p){Set<String> result=new HashSet<>();for(Signature s:p.signingInfo.getApkContentsSigners())result.add(java.util.Base64.getEncoder().encodeToString(s.toByteArray()));return result;}
 synchronized void note(String text){message=text;}
 void installMessage(String message){set("ready",message,100);}
 private String safe(Exception e){return Diagnostics.clean(Objects.toString(e.getMessage(),"Lỗi kết nối."));}
}
