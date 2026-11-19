package vn.streampocket.mobile;
import java.util.*;
import java.io.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.json.*;
import okhttp3.*;
/** Follows source-issued redirects; discovered origins are kept only for this process. */
final class SourceSites {
 private static final Set<String> known=new HashSet<>(List.of(Sources.MAIN,Sources.FAST,Sources.LEGACY_FAST));
 private static String main=Sources.MAIN,fast=Sources.FAST;
 static synchronized boolean known(String origin){return known.contains(origin);}
 static synchronized String current(String site){return "fast".equals(site)?fast:main;}
 static synchronized JSONObject domains(){return Json.obj("main",java.net.URI.create(main).getHost(),"fast",java.net.URI.create(fast).getHost());}
 static synchronized void accept(String original,Document page)throws IOException{
  String target=Net.origin(RemoteAccess.publicUrl(page.baseUri()));
  if(!known.contains(original))throw new IOException("Trang nguồn chưa được xác nhận.");
  if(page.selectFirst("main")==null||CoverCodec.protocolUrl(page,target).isEmpty())throw new IOException("Trang chuyển hướng không phải nguồn phim hợp lệ.");
  if(known.size()>=32&&!known.contains(target))throw new IOException("Trang nguồn chuyển tên miền quá nhiều lần.");
  known.add(target);
  if(original.equals(main)||original.equals(Sources.MAIN))main=target;
  else if(original.equals(fast)||original.equals(Sources.FAST)||original.equals(Sources.LEGACY_FAST))fast=target;
 }
 static Document document(String url)throws Exception{
  if(!known(Net.origin(url)))throw new IOException("Trang nguồn chưa được xác nhận.");
  JSONObject context=new RemoteAccess.Context(new JSONObject(),url,false);
  try(Response response=Net.forSource(context).newCall(new Request.Builder().url(url).header("User-Agent","Mozilla/5.0 StreamPocket/1.0").build()).execute()){
   if(!response.isSuccessful()||response.body()==null)throw new IOException("Trang nguồn trả HTTP "+response.code());
   String finalUrl=response.request().url().toString();
   Document d=Jsoup.parse(new String(Net.bounded(response.body().byteStream(),RemoteAccess.MAX_MANIFEST),java.nio.charset.StandardCharsets.UTF_8),finalUrl);
   accept(Net.origin(url),d);return d;
  }
 }
}
