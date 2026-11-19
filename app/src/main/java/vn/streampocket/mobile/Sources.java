package vn.streampocket.mobile;
import org.json.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;

public final class Sources {
 public static final String MAIN="https://huangguoai.com",FAST="https://1bb4a2.ipycpknh.cc",LEGACY_FAST="https://duhgxf.lbvrhyir.cc",API="https://api.pt1269.com";
 static final Map<String,String> sections=Map.of("home","/","newest","/newest","drama","/ai-duanju/","animation","/ai-manju/","modern","/tag/dushi/","period","/tag/gufeng/");
 static final Pattern excluded=Pattern.compile("高考|高[一二三]|高中|中[小學学]|初中|中学生|未成年|未满十?八|未滿十八|幼[女男童]|儿[童时]|兒童|校园|校園|小学生|小學生|小男孩|小女孩|(?:1[0-7]|十[一二三四五六七])[岁歲]|underage|high.school|换脸|換臉|AI魔改|偷拍|强奸|強姦|迷奸|未授权|未授權|大雄一家|青春期|美少年|裸照泄露|deepfake",Pattern.CASE_INSENSITIVE);
 public static String site(JSONObject b){return SourceSites.current(b.optString("site"));}
 static String encode(String s){try{return URLEncoder.encode(s,"UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}}
 static String title(Document d){Element h=d.selectFirst("h1.hg-web-play__title");return h==null?"":h.text();}
 static Document document(String url)throws Exception{return SourceSites.document(url);}
 static JSONObject schema(Document d)throws Exception{
  for(Element s:d.select("script[type=application/ld+json]")){
   JSONObject root=new JSONObject(s.data());JSONArray graph=root.optJSONArray("@graph");if(graph==null)graph=Json.array(root);
   for(int i=0;i<graph.length();i++)if("VideoObject".equals(graph.getJSONObject(i).optString("@type")))return graph.getJSONObject(i);
  }throw new Exception("Thiếu thông tin video.");
 }
 static void verify(Document d,String id,int ep)throws Exception{
  JSONObject v=schema(d);String words=v.optString("name")+" "+v.optString("description")+" "+v.optString("genre")+" "+title(d);
  if(!"18+".equals(v.optString("contentRating"))||excluded.matcher(words).find()||id.equals("2183"))throw new Exception("Nội dung này chưa được hỗ trợ.");
  JSONObject c=config(d);Element player=d.selectFirst("section.hg-web-play");
  String pid=player==null?"":player.attr("data-track-drama-id"),pep=player==null?"":player.attr("data-track-episode");
  if(pid.isEmpty())pid=c.optString("id");if(pep.isEmpty())pep=c.optString("ep");
  if(!id.equals(pid)||!String.valueOf(ep).equals(pep))throw new Exception("Trang nguồn trả sai tập.");
 }
 static JSONObject config(Document d)throws Exception{Element e=d.getElementById("videoInitialData");return new JSONObject(e==null?"{}":e.data());}
 static String id(String url)throws Exception{
  URI u=new URI(url);Matcher m=Pattern.compile("^/video/([1-9]\\d*)/(?:ep-[1-9]\\d*/)?$").matcher(u.getPath());
  if(!SourceSites.known(Net.origin(url))||u.getUserInfo()!=null||!m.matches())throw new Exception("Dán link phim HuangguoAI hợp lệ.");
  return m.group(1);
 }
 public static JSONObject episodes(JSONObject b)throws Exception{
  String root=site(b),id=id(b.getString("url")),url=root+"/video/"+id+"/";Document d=document(url);url=d.baseUri();root=Net.origin(url);verify(d,id,1);
  rememberImageProtocol(d,root);JSONObject c=config(d);TreeSet<Integer> eps=new TreeSet<>();
  for(Element a:d.select("a.hg-web-play__ep[data-ep-id]")){
   int n=Integer.parseInt(a.attr("data-ep-id"));String p="/video/"+id+"/"+(n==1?"":"ep-"+n+"/");
   if(n>0&&p.equals(a.attr("href")))eps.add(n);
  }
  JSONObject tracking=c.optJSONObject("trackingEpisodes");
  if(tracking!=null)for(String key:Json.keys(tracking))if(key.matches("[1-9]\\d*")&&tracking.optJSONObject(key)!=null&&tracking.getJSONObject(key).optInt("episode")==Integer.parseInt(key))eps.add(Integer.parseInt(key));
  if(eps.isEmpty()){JSONObject urls=c.optJSONObject("epPlaySrcs");if(urls!=null)for(String key:Json.keys(urls))if(key.matches("[1-9]\\d*")&&!urls.optString(key).isBlank())eps.add(Integer.parseInt(key));}
  if(eps.isEmpty()&&d.selectFirst("[data-show-episodes=0]")!=null)eps.add(1);
  if(eps.isEmpty())throw new Exception("Trang nguồn không có tập phim.");
  JSONObject v=schema(d),part=v.optJSONObject("isPartOf");String name=part==null?title(d):part.optString("name",title(d));
  JSONObject author=null;
  for(Element fact:d.select(".hg-web-play__fact"))if(fact.select("b").text().equals("出品")){
   Element a=fact.selectFirst("a[href]");if(a!=null){Matcher m=Pattern.compile("/author/(\\d+)/?").matcher(a.attr("href"));if(m.matches())author=Json.obj("id",m.group(1),"name",a.text());}
  }
  JSONArray items=new JSONArray();for(int n:eps)items.put(Json.obj("id","hg-"+id+"-"+n,"title",name+" · Tập "+n,"duration",SeriesMetadata.duration(c,v,n),"site",b.optString("site","main")));
  String cover=SeriesMetadata.cover(d,c,v,url);if(!cover.isBlank())SourceImages.remember(url,cover);
  return Json.obj("title",name,"url",url,"cover",cover,"domains",SourceSites.domains(),"author",author,"items",items);
 }
 public static JSONObject catalogue(JSONObject b)throws Exception{
  String keyword=b.optString("keyword").trim(),section=b.optString("section","home"),root=site(b);
  if(keyword.length()>160)throw new Exception("Từ khóa quá dài.");
  boolean combined=keyword.equalsIgnoreCase("AI");List<String> feeds=combined?List.of("drama","animation"):List.of(section);
  JSONObject cursor=b.optJSONObject("cursor"),next=new JSONObject();Map<String,JSONObject> found=new LinkedHashMap<>();
  for(String feed:feeds){
   if(cursor!=null&&!cursor.has(feed))continue;
   int page=cursor==null?1:cursor.optInt(feed,1);if(page<1||page>10000)throw new Exception("Trang không hợp lệ.");
   String base=sections.get(feed);
   if(feed.matches("author:[1-9]\\d*"))base="/author/"+feed.substring(7)+"/";
   if(base==null)throw new Exception("Danh mục không hợp lệ.");
   if(!keyword.isEmpty()&&!combined)base="/search/video/"+encode(keyword)+"/";
   String path=pagePath(base,page);Document d=document(root+path);root=Net.origin(d.baseUri());rememberImageProtocol(d,root);Element main=d.selectFirst("main");if(main==null)throw new Exception("Không đọc được danh mục nguồn.");
   for(Element a:main.select("nav.hg-pager a[rel=next]"))if(Net.resolve(root,a.attr("href")).equals(root+pagePath(base,page+1)))Json.put(next,feed,page+1);
   Element active=main.selectFirst(".hg-card-grid[data-channel-panel=latest]");if(active==null)active=main;
   for(Element card:active.select(".hg-drama-card")){
    Element a=card.selectFirst(".hg-drama-card__cover-link"),t=card.selectFirst(".hg-drama-card__title");
    if(a==null||t==null||excluded.matcher(card.text()).find())continue;
    Matcher m=Pattern.compile("/video/(\\d+)/").matcher(a.attr("href"));if(!m.matches()||m.group(1).equals("2183"))continue;
    String url=root+a.attr("href");JSONObject item=Json.obj("title",t.text().replace("全集在线观看","").trim(),"url",url);
    Element img=a.selectFirst("img");if(img!=null){String src=img.hasAttr("data-src")?img.attr("data-src"):img.attr("src");try{Json.put(item,"cover",SourceImages.remember(d.baseUri(),Net.resolve(d.baseUri(),src)));}catch(Exception ignored){}}
    Element count=card.selectFirst("[data-ep-base]");if(count!=null){Matcher n=Pattern.compile("(\\d+)\\s*集").matcher(count.attr("data-ep-base"));if(n.find())Json.put(item,"count",Integer.parseInt(n.group(1)));}
    found.put(url,item);
   }
  }
  return Json.obj("items",new JSONArray(found.values()),"domains",SourceSites.domains(),"next",next.length()>0?next:JSONObject.NULL);
 }
 static String pagePath(String base,int page){return page==1?base:base.replaceFirst("/?$","/")+(base.startsWith("/author/")?"video/":base.startsWith("/tag/")?"page/":"")+page+"/";}
 public static JSONObject ptList(JSONObject b)throws Exception{
  URI u=new URI(b.getString("url"));if(!"https://porntasia.app".equals(Net.origin(u.toString()))||!u.getPath().startsWith("/videos/list/"))throw new Exception("Link danh sách không hợp lệ.");
  String tag="";for(String pair:Objects.toString(u.getQuery(),"").split("&"))if(pair.startsWith("tag_id="))tag=pair.substring(7);
  if(!tag.matches("\\d+"))throw new Exception("Link thiếu tag_id.");
  String word=b.optString("keyword");int page=Math.max(1,b.optInt("page",1));
  String params=word.isBlank()?"tag_id="+tag+"&order=time":"video_type=short&s="+encode(word)+"&keyword="+encode(word)+"&order=relevance";
  JSONObject r=pt("/pwa/videos/short/"+(word.isBlank()?"list":"keyword"),b.optString("token"),params+"&page="+page);
  JSONArray items=new JSONArray(),list=r.getJSONArray("videos");for(int i=0;i<list.length();i++){
   JSONObject v=list.getJSONObject(i);items.put(Json.obj("id",v.getString("video_id"),"title",v.optString("video_title"),"duration",v.optString("video_duration"),"date",v.optString("video_upload_date"),"thumb",Net.resolve("https://img.pt1269.com",v.optString("video_thumb",v.optString("video_cover")))));
  }
  int total=r.optInt("total_results",-1);return Json.obj("items",items,"page",page,"total",total,"more",list.length()==25&&(total<0||page*25<total));
 }
 static JSONObject pt(String endpoint,String token,String params)throws Exception{
  JSONObject r=Net.json(API+endpoint+"?lang=en&platform=web&limit=25&token="+encode(token)+"&"+params);
  if(r.optJSONObject("status")==null||r.getJSONObject("status").optInt("code")!=200)throw new Exception(token.isBlank()?"Nguồn từ chối phiên khách. Nội dung này có thể cần token tài khoản.":"Token bị từ chối hoặc không có quyền truy cập. Xóa token để dùng chế độ khách hoặc nhập token mới.");
  return r.getJSONObject("response");
 }
 public static JSONObject media(JSONObject b)throws Exception{
  String key=b.getString("id");
  if(key.matches("\\d+")){
   JSONObject r=pt("/pwa/video/info/"+key,b.optString("token"),"video_type=short");
   String url=r.getJSONObject("video_urls").optString("480");if(url.isBlank())throw new Exception("Không có nguồn HD cho tài khoản này.");
   return RemoteAccess.media(Json.obj("url",Net.resolve(API,url),"title",r.optString("video_title","Video "+key)));
  }
  Matcher m=Pattern.compile("hg-([1-9]\\d*)-([1-9]\\d*)").matcher(key);if(!m.matches())throw new Exception("Mã tập không hợp lệ.");
  String id=m.group(1),ep=m.group(2),root=site(b),page=root+"/video/"+id+"/"+(ep.equals("1")?"":"ep-"+ep+"/");
  Document d=document(page);page=d.baseUri();root=Net.origin(page);verify(d,id,Integer.parseInt(ep));
  JSONObject response=EdgePlayback.get(root,"/api/videos/"+id+"/episodes/"+ep+"/playback",page);
  JSONObject r=response.optJSONObject("data");
  if(r==null||!"GET".equals(r.optString("manifest_method")))throw new Exception("Nguồn chưa cấp phiên phát cho tập này.");
  String stream=r.getJSONObject("streams").optString("main");
  if(stream.isBlank())throw new Exception("Nguồn chưa cấp link phát cho tập này.");
  JSONObject cdn=r.optJSONObject("cdn");if(cdn==null)cdn=new JSONObject();
  JSONObject headers=Json.obj("cdnM3u8Enabled",cdn.optBoolean("m3u8Enabled"),"cdnOtherEnabled",cdn.optBoolean("otherEnabled"),"cdnAuthKey",cdn.optString("authKey"),"cdnAuthValue",cdn.optString("authValue"),"cdnApp",cdn.optString("app"));
  return RemoteAccess.media(Json.obj("url",Net.resolve(page,stream),"title",title(d)+" EP."+ep,"page",page,"config",headers));
 }
 static final Map<String,String> protocolUrls=new java.util.concurrent.ConcurrentHashMap<>();
 static final Map<String,JSONObject> protocols=new java.util.concurrent.ConcurrentHashMap<>();
 static void rememberImageProtocol(Document d,String root){String url=CoverCodec.protocolUrl(d,Net.origin(d.baseUri()));if(!url.isEmpty())protocolUrls.put(root,url);}
 static synchronized JSONObject imageProtocol(String root)throws Exception{
  String url=protocolUrls.get(root);
  if(url==null){rememberImageProtocol(document(root+"/"),root);url=protocolUrls.get(root);}
  if(url==null)throw new Exception("Trang nguồn thiếu cấu hình ảnh.");
  JSONObject cached=protocols.get(url);if(cached!=null)return cached;
  JSONObject context=new RemoteAccess.Context(Json.obj("page",root+"/"),url,false);
  JSONObject p=CoverCodec.protocol(new String(Net.bytes(url,context,0,0,null),java.nio.charset.StandardCharsets.UTF_8));protocols.put(url,p);return p;
 }
 static byte[] imageBytes(JSONObject b)throws Exception{
  String url=b.getString("url");JSONObject context=SourceImages.context(url);String root=Net.origin(context.getString("page"));
  byte[] raw=Net.bytes(url,context,0,0,null);
  if(!isImage(raw)){
   try{raw=CoverCodec.decode(raw,imageProtocol(root));}
   catch(java.io.IOException decode){
    synchronized(Sources.class){String old=protocolUrls.remove(root);if(old!=null)protocols.remove(old);}
    raw=CoverCodec.decode(raw,imageProtocol(root));
   }
  }
  if(!isImage(raw))throw new Exception("Ảnh không hợp lệ.");return raw;
 }
 public static String image(JSONObject b)throws Exception{
  byte[] raw=imageBytes(b);
  BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,bounds);
  if(bounds.outWidth<1||bounds.outHeight<1)throw new Exception("Không đọc được thumbnail.");
  BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
  while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>640)options.inSampleSize*=2;
  Bitmap bitmap=BitmapFactory.decodeByteArray(raw,0,raw.length,options);
  if(bitmap==null)throw new Exception("Không đọc được thumbnail.");
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
   if(!bitmap.compress(Bitmap.CompressFormat.JPEG,80,out))throw new Exception("Không tạo được thumbnail.");
   if(out.size()>256*1024)throw new Exception("Thumbnail quá lớn để hiển thị an toàn.");
   return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
  }finally{bitmap.recycle();}
 }
 static boolean isImage(byte[] b){return CoverCodec.isImage(b);}
}
