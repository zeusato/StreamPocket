package vn.streampocket.mobile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import okhttp3.Dns;

/** Native-only, per-resource authority derived from a verified source response. */
final class RemoteAccess {
 static final int MAX_ORIGINS=256,MAX_MANIFEST=8*1024*1024;
 static String publicUrl(String value)throws IOException{
  try{
   URI u=new URI(value);String h=u.getHost();
   if(!"https".equals(u.getScheme())||h==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||u.getFragment()!=null)throw new IllegalArgumentException();
   h=h.toLowerCase(Locale.ROOT);
   if(!h.matches("(?=.{1,253}$)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")||!h.contains(".")||h.matches("[0-9.]+")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal")||h.endsWith(".lan")||h.endsWith(".home"))throw new IllegalArgumentException();
   return u.normalize().toASCIIString();
  }catch(Exception e){throw new IOException("Nguồn trả địa chỉ HTTPS không hợp lệ.",e);}
 }
 static boolean publicAddress(InetAddress a){
  if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())return false;
  byte[] b=a.getAddress();int first=b[0]&255,second=b[1]&255;
  if(b.length==4)return first!=0&&first!=127&&first<224&&!(first==100&&second>=64&&second<=127)&&!(first==169&&second==254)&&!(first==198&&(second==18||second==19));
  return b.length==16&&(first&0xe0)==0x20; // Public IPv6 global unicast only.
 }
 static Dns publicDns(Dns delegate){return hostname->{
  List<InetAddress> addresses=delegate.lookup(hostname);
  if(addresses.isEmpty())throw new UnknownHostException("Không tìm thấy máy chủ nguồn.");
  for(InetAddress a:addresses)if(!publicAddress(a))throw new UnknownHostException("Nguồn trỏ tới địa chỉ mạng riêng.");
  return addresses;
 };}
 static final class Context extends JSONObject {
  final Scope scope;
  Context(JSONObject fields,String initial,boolean manifests)throws Exception{
   super(fields.toString());scope=new Scope(initial,manifests);
  }
 }
 static final class Scope {
  final Set<String> origins=new HashSet<>();
  final Map<String,String> manifestRedirects=new HashMap<>();
  final boolean manifests;
  Scope(String initial,boolean manifests)throws IOException{this.manifests=manifests;grant(initial);}
  synchronized void grant(String url)throws IOException{
   String origin=Net.origin(publicUrl(url));
   if(!origins.contains(origin)&&origins.size()>=MAX_ORIGINS)throw new IOException("Nguồn trả quá nhiều CDN.");
   origins.add(origin);
  }
  synchronized void require(String url)throws IOException{
   if(!origins.contains(Net.origin(publicUrl(url))))throw new IOException("CDN chưa được trang hoặc playlist nguồn cấp.");
  }
  synchronized void redirect(String from,String to)throws IOException{require(from);grant(to);}
  synchronized void playlist(String original,String finalUrl,String text)throws IOException{
   require(finalUrl);if(!manifests||!text.replace("\uFEFF","").stripLeading().startsWith("#EXTM3U"))return;
   if(!original.equals(finalUrl)&&manifestRedirects.size()<256)manifestRedirects.put(original,finalUrl);
   for(String raw:text.split("\\r?\\n")){
    String line=raw.trim();if(line.isEmpty())continue;
    if(!line.startsWith("#"))grant(Net.resolve(finalUrl,line));
    else{
     Matcher m=Pattern.compile("(?:^|[:,])(?:[A-Z0-9-]*URI)=\\\"([^\\\"]+)\\\"").matcher(line);
     while(m.find())if(!m.group(1).startsWith("data:"))grant(Net.resolve(finalUrl,m.group(1)));
    }
   }
  }
  synchronized String finalUrl(String original){return manifestRedirects.getOrDefault(original,original);}
 }
 static Scope scope(JSONObject source){return source instanceof Context?((Context)source).scope:null;}
 static JSONObject media(JSONObject fields)throws Exception{return new Context(fields,fields.getString("url"),true);}
}
