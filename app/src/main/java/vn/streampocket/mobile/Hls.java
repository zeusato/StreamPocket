package vn.streampocket.mobile;
import org.json.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public final class Hls {
 public interface Progress {void update(int complete,int total,long bytes,double rate);}
 public interface Stop {void check() throws Exception;}
 public static final class Segment {
  public String url,key="",iv="";public long start=0,length=0,sequence;public boolean init;
 }
 public static final class Playlist {public final List<Segment> segments=new ArrayList<>();public String extension="ts";}
 static Map<String,String> attributes(String line){
  Map<String,String> a=new HashMap<>();Matcher m=Pattern.compile("([A-Z0-9-]+)=(?:\"([^\"]*)\"|([^,]*))").matcher(line);
  while(m.find())a.put(m.group(1),m.group(2)!=null?m.group(2):m.group(3));return a;
 }
 public static Playlist load(String url,JSONObject source,Stop stop)throws Exception{return load(url,source,stop,0);}
 static Playlist load(String url,JSONObject source,Stop stop,int depth)throws Exception{
  if(depth>4)throw new IOException("Playlist lồng quá sâu.");stop.check();
  String text=new String(Net.bytes(url,source,0,0,null),java.nio.charset.StandardCharsets.UTF_8);
  if(!text.stripLeading().startsWith("#EXTM3U"))throw new IOException("Nguồn không trả HLS.");
  url=Net.finalUrl(url,source);
  String[] lines=text.replace("\r","").split("\n");
  String variant="";long best=-1;boolean master=false;
  for(int i=0;i<lines.length-1;i++)if(lines[i].startsWith("#EXT-X-STREAM-INF:")){
   master=true;Map<String,String>a=attributes(lines[i]);if(a.containsKey("AUDIO"))continue;
   long bw=Long.parseLong(a.getOrDefault("BANDWIDTH","0"));if(bw>best){variant=Net.resolve(url,lines[i+1].trim());best=bw;}
  }
  if(!variant.isEmpty())return load(variant,source,stop,depth+1);
  if(master)throw new IOException("Nguồn yêu cầu ghép luồng âm thanh riêng, chưa hỗ trợ tải.");
  return parseMedia(url,text);
 }
 static Playlist parseMedia(String url,String text)throws Exception{
  if(!text.stripLeading().startsWith("#EXTM3U"))throw new IOException("Nguồn không trả HLS.");
  String[] lines=text.replace("\r","").split("\n");
  Playlist p=new Playlist();String key="",iv="",range="",previous="";long seq=0,lastEnd=0;
  for(String raw:lines){String line=raw.trim();
   if(line.startsWith("#EXT-X-MEDIA-SEQUENCE:"))seq=Long.parseLong(line.substring(line.indexOf(':')+1));
   else if(line.startsWith("#EXT-X-KEY:")){
    Map<String,String>a=attributes(line);String method=a.get("METHOD");
    if("NONE".equals(method)){key="";iv="";}
    else if("AES-128".equals(method)&&(!a.containsKey("KEYFORMAT")||a.get("KEYFORMAT").equals("identity"))){key=Net.resolve(url,a.get("URI"));iv=a.getOrDefault("IV","");}
    else throw new IOException("Kiểu mã hóa HLS chưa được hỗ trợ.");
   }else if(line.startsWith("#EXT-X-MAP:")){
    Map<String,String>a=attributes(line);Segment s=new Segment();s.init=true;s.url=Net.resolve(url,a.get("URI"));s.key=key;s.iv=iv;
    if(!key.isEmpty()&&iv.isEmpty())throw new IOException("Init segment mã hóa thiếu IV.");
    String r=a.getOrDefault("BYTERANGE","");if(!r.isEmpty()){String[] q=r.split("@");s.length=Long.parseLong(q[0]);s.start=q.length>1?Long.parseLong(q[1]):0;}
    p.extension="mp4";p.segments.add(s);
   }else if(line.startsWith("#EXT-X-BYTERANGE:"))range=line.substring(line.indexOf(':')+1);
   else if(!line.isEmpty()&&!line.startsWith("#")){
    Segment s=new Segment();s.url=Net.resolve(url,line);s.key=key;s.iv=iv;s.sequence=seq++;
    if(!range.isEmpty()){
     String[] q=range.split("@");s.length=Long.parseLong(q[0]);
     if(q.length==1&&!s.url.equals(previous))throw new IOException("Byte range thiếu offset.");
     s.start=q.length>1?Long.parseLong(q[1]):lastEnd;lastEnd=s.start+s.length;range="";
    }
    previous=s.url;p.segments.add(s);
   }
  }
  if(!Arrays.asList(lines).contains("#EXT-X-ENDLIST"))throw new IOException("Nguồn chưa kết thúc. Chỉ hỗ trợ tải video đầy đủ, không phải luồng live.");
  if(p.segments.isEmpty()||p.segments.size()>50000)throw new IOException("Danh sách đoạn HLS không hợp lệ.");
  return p;
 }
 public static String download(JSONObject source,File file,Stop stop,Progress progress)throws Exception{
  Playlist p=load(source.getString("url"),source,stop);
  Map<String,byte[]>keys=new HashMap<>();long[] bytes={0},last={System.nanoTime()},previous={0};int[] done={0};
  Net.Count count=n->{bytes[0]+=n;long now=System.nanoTime();if(now-last[0]>=500_000_000L){progress.update(done[0],p.segments.size(),bytes[0],(bytes[0]-previous[0])*1e9/(now-last[0]));previous[0]=bytes[0];last[0]=now;}};
  File segmentFile=new File(file.getParentFile(),"segment.part");
  try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
   for(Segment s:p.segments){stop.check();Net.transfer(s.url,source,s.start,s.length,segmentFile,stop,count);stop.check();
    Cipher cipher=null;
    if(!s.key.isEmpty()){
     byte[] k=keys.get(s.key);if(k==null){k=Net.bytes(s.key,source,0,0,null);if(k.length!=16)throw new IOException("Khóa AES không hợp lệ.");keys.put(s.key,k);}
     byte[] iv=new byte[16];byte[] num=(s.iv.isEmpty()?BigInteger.valueOf(s.sequence):new BigInteger(s.iv.replaceFirst("(?i)^0x",""),16)).toByteArray();
     System.arraycopy(num,Math.max(0,num.length-16),iv,Math.max(0,16-num.length),Math.min(16,num.length));
     cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(k,"AES"),new IvParameterSpec(iv));
    }
    try(InputStream raw=new BufferedInputStream(new FileInputStream(segmentFile));InputStream in=cipher==null?raw:new CipherInputStream(raw,cipher)){copySegment(in,out,p.extension.equals("ts"),stop);}
    segmentFile.delete();done[0]++;progress.update(done[0],p.segments.size(),bytes[0],(bytes[0]-previous[0])*1e9/Math.max(1,System.nanoTime()-last[0]));
   }
  }finally{segmentFile.delete();}return p.extension;
 }
 static long copySegment(InputStream in,OutputStream out,boolean ts,Stop stop)throws Exception{
  byte[] buffer=new byte[64*1024];long size=0;int n;
  while((n=in.read(buffer))!=-1){stop.check();if(n==0)continue;if(ts&&size==0&&buffer[0]!=0x47)throw new IOException("Đoạn tải về không phải video TS.");out.write(buffer,0,n);size+=n;}
  if(ts&&(size<188||size%188!=0))throw new IOException("Độ dài đoạn TS không hợp lệ.");return size;
 }
}
