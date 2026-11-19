package vn.streampocket.mobile;
import okhttp3.*;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

public final class Net {
 public static final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(25,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).followRedirects(false).dns(RemoteAccess.publicDns(Dns.SYSTEM)).build();
 public static byte[] bounded(InputStream in,int limit)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;
  while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>limit)throw new IOException("Phản hồi nguồn vượt giới hạn.");out.write(buffer,0,n);}return out.toByteArray();
 }
 public static void allowed(String url,JSONObject source)throws IOException{
  RemoteAccess.Scope scope=RemoteAccess.scope(source);if(scope!=null)scope.require(url);else allowed(url);
 }
 public static String finalUrl(String url,JSONObject source){RemoteAccess.Scope scope=RemoteAccess.scope(source);return scope==null?url:scope.finalUrl(url);}
 public static OkHttpClient forSource(JSONObject source){
  RemoteAccess.Scope scope=RemoteAccess.scope(source);if(scope==null)return client;
  return client.newBuilder().addInterceptor(chain->{
   Request request=chain.request();String original=request.url().toString();
   for(int hop=0;hop<5;hop++){
    String url=request.url().toString();scope.require(url);
    Request.Builder builder=request.newBuilder().removeHeader("x-cdn-auth").removeHeader("x-cdn-app").removeHeader("Referer").removeHeader("Origin");
    for(var h:headers(url,source).entrySet())builder.header(h.getKey(),h.getValue());
    request=builder.build();Response response=chain.proceed(request);
    try{
     if(Set.of(301,302,303,307,308).contains(response.code())&&response.header("Location")!=null){
      String next=resolve(url,response.header("Location"));scope.redirect(url,next);response.close();
      request=request.newBuilder().url(next).removeHeader("Authorization").removeHeader("Cookie").removeHeader("Proxy-Authorization").build();continue;
     }
     if(scope.manifests&&response.isSuccessful()&&response.body()!=null){
      String prefix=response.peekBody(64).string().replace("\uFEFF","").stripLeading();
      if(prefix.startsWith("#EXTM3U")){
       MediaType type=response.body().contentType();byte[] bytes=bounded(response.body().byteStream(),RemoteAccess.MAX_MANIFEST);response.close();
       scope.playlist(original,url,new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
       return response.newBuilder().body(ResponseBody.create(bytes,type)).header("Content-Length",String.valueOf(bytes.length)).build();
      }
     }
     return response;
    }catch(IOException|RuntimeException error){response.close();throw error;}
   }
   throw new IOException("Nguồn chuyển hướng quá nhiều lần.");
  }).build();
 }

 public interface Count {void bytes(int n);}
 public static String origin(String url){URI u=URI.create(url);String authority=u.getAuthority();if("https".equals(u.getScheme())&&u.getPort()==443&&u.getUserInfo()==null)authority=u.getHost();return u.getScheme()+"://"+(authority==null?null:authority.toLowerCase(Locale.ROOT));}
 public static String resolve(String base,String value){return URI.create(base).resolve(value.replace("&amp;","&")).toString();}
 public static void allowed(String url) throws IOException {
  URI u=URI.create(RemoteAccess.publicUrl(url));String h=u.getHost();
  if(!(SourceSites.known(origin(url)) || h.endsWith(".pt1269.com")))
    throw new IOException("Tên miền chưa được hỗ trợ: "+Objects.toString(h,"không hợp lệ"));
 }
 public static Map<String,String> headers(String url,JSONObject source){
  Map<String,String> h=new HashMap<>();
  if(source==null)return h;
  String page=source.optString("page");if(!page.isEmpty()){h.put("Referer",page);h.put("Origin",origin(page));}
  JSONObject c=source.optJSONObject("config");
  if(c!=null && origin(url).equals(origin(source.optString("url")))){
   String p=URI.create(url).getPath();
   boolean sign=p.endsWith(".m3u8")?c.optBoolean("cdnM3u8Enabled"):c.optBoolean("cdnOtherEnabled")&&p.matches(".*\\.(ts|m4s|key)$");
   if(sign && "x-cdn-auth".equalsIgnoreCase(c.optString("cdnAuthKey"))){h.put("x-cdn-auth",c.optString("cdnAuthValue"));h.put("x-cdn-app",c.optString("cdnApp"));}
  }
  return h;
 }
 public static byte[] bytes(String url,JSONObject source,long start,long length,Count count) throws Exception {
  for(int hop=0;hop<5;hop++){
   allowed(url,source);Request.Builder b=new Request.Builder().url(url).header("User-Agent","Mozilla/5.0 StreamPocket/1.0");
   for(var e:headers(url,source).entrySet())b.header(e.getKey(),e.getValue());
   if(length>0)b.header("Range","bytes="+start+"-"+(start+length-1));
   try(Response r=forSource(source).newCall(b.build()).execute()){
    if(Set.of(301,302,303,307,308).contains(r.code())&&r.header("Location")!=null){url=resolve(url,r.header("Location"));continue;}
    if(!r.isSuccessful())throw new IOException("Máy chủ trả HTTP "+r.code());
    if(length>0 && (r.code()!=206 || !r.header("Content-Range","").startsWith("bytes "+start+"-"+(start+length-1)+"/")))throw new IOException("Nguồn trả sai phạm vi dữ liệu.");
    if(r.body()==null)throw new IOException("Phản hồi rỗng.");
    try(InputStream in=r.body().byteStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] buf=new byte[65536];int n;
     while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out.size()+n>8*1024*1024)throw new IOException("Phản hồi metadata hoặc thumbnail vượt giới hạn.");out.write(buf,0,n);if(count!=null)count.bytes(n);}
     if(length>0 && out.size()!=length)throw new IOException("Thiếu dữ liệu đoạn video.");
     return out.toByteArray();
    }
   }
  }throw new IOException("Nguồn chuyển hướng quá nhiều lần.");
 }
 public static void transfer(String url,JSONObject source,long start,long length,File target,Hls.Stop stop,Count count)throws Exception{
  for(int hop=0;hop<5;hop++){
   stop.check();allowed(url,source);Request.Builder b=new Request.Builder().url(url).header("User-Agent","Mozilla/5.0 StreamPocket/1.0");
   for(var e:headers(url,source).entrySet())b.header(e.getKey(),e.getValue());
   if(length>0)b.header("Range","bytes="+start+"-"+(start+length-1));
   try(Response response=forSource(source).newCall(b.build()).execute()){
    if(Set.of(301,302,303,307,308).contains(response.code())&&response.header("Location")!=null){url=resolve(url,response.header("Location"));continue;}
    if(!response.isSuccessful()||response.body()==null)throw new IOException("Tải đoạn trả HTTP "+response.code());
    if(length>0&&(response.code()!=206||!response.header("Content-Range","").startsWith("bytes "+start+"-"+(start+length-1)+"/")))throw new IOException("Nguồn trả sai phạm vi dữ liệu.");
    long received=0,nextSpaceCheck=0;
    try(InputStream in=response.body().byteStream();OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
     byte[] buffer=new byte[64*1024];int n;
     while((n=in.read(buffer))!=-1){stop.check();
      if(received>=nextSpaceCheck){if(target.getParentFile().getUsableSpace()<32L*1024*1024)throw new IOException("Bộ nhớ lưu trữ sắp đầy. Dọn dung lượng rồi thử lại.");nextSpaceCheck=received+1024*1024;}
      out.write(buffer,0,n);received+=n;if(count!=null)count.bytes(n);
     }
    }
    if(length>0&&received!=length)throw new IOException("Thiếu dữ liệu đoạn video.");
    return;
   }
  }throw new IOException("Nguồn chuyển hướng quá nhiều lần.");
 }
 public static JSONObject postJson(String url,JSONObject body,JSONObject source)throws Exception{
  allowed(url);Request.Builder b=new Request.Builder().url(url).header("User-Agent","Mozilla/5.0 StreamPocket/1.0").post(RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8")));
  for(var e:headers(url,source).entrySet())b.header(e.getKey(),e.getValue());
  try(Response r=client.newCall(b.build()).execute()){
   if(!r.isSuccessful()||r.body()==null)throw new IOException("Phiên phát trả HTTP "+r.code());
   String text=r.body().string();if(text.length()>4*1024*1024)throw new IOException("Phản hồi phiên phát quá lớn.");
   return new JSONObject(text);
  }
 }
 public static String text(String url) throws Exception{return new String(bytes(url,null,0,0,null),java.nio.charset.StandardCharsets.UTF_8);}
 public static JSONObject json(String url)throws Exception{return new JSONObject(text(url));}
}
