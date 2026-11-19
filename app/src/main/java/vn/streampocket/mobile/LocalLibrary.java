package vn.streampocket.mobile;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.util.Size;
import android.util.Base64;
import androidx.documentfile.provider.DocumentFile;
import org.json.*;
import java.io.*;
import java.util.*;

final class LocalLibrary {
 final Context context;
 volatile List<JSONObject> entries=List.of();
 volatile Map<String,JSONObject> byId=Map.of();
 String mode="",label="Video trên máy",warning="";
 static final int LIMIT=10000,PAGE=60;
 LocalLibrary(Context context){this.context=context.getApplicationContext();}
 boolean fullAccess(){return context.checkSelfPermission(Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_VIDEO":"android.permission.READ_EXTERNAL_STORAGE")==PackageManager.PERMISSION_GRANTED;}
 String savedMode(){return context.getSharedPreferences("library",0).getString("mode","device");}
 String folder(){return context.getSharedPreferences("library",0).getString("folder","");}
 void rememberFolder(Uri uri){
  context.getSharedPreferences("library",0).edit().putString("folder",uri.toString()).putString("mode","folder").apply();
 }
 synchronized JSONObject list(JSONObject b)throws Exception{
  String requested=b.optString("mode",savedMode());if(!requested.equals("folder"))requested="device";
  if(b.optBoolean("refresh")||!requested.equals(mode))scan(requested);
  List<JSONObject> filtered=filtered(b);
  int offset=Math.max(0,Math.min(b.optInt("offset"),filtered.size())),end=Math.min(filtered.size(),offset+PAGE);
  JSONArray page=new JSONArray();
  for(int i=offset;i<end;i++){JSONObject v=filtered.get(i);page.put(Json.obj("id",v.optString("id"),"title",v.optString("title"),"durationMs",v.optLong("durationMs"),"size",v.optLong("size"),"date",v.optLong("date")));}
  String access="device".equals(mode)&&!fullAccess()?"Android chỉ cho xem video được cấp quyền và video của ứng dụng. Dùng Cấp quyền hoặc Chọn thư mục để mở thêm.":"";
  return Json.obj("items",page,"total",filtered.size(),"next",end<filtered.size()?end:-1,"mode",mode,"label",label,"limited",!fullAccess()&&mode.equals("device"),"message",(access+" "+warning).trim());
 }
 synchronized List<JSONObject> playlist(JSONObject b)throws Exception{
  List<JSONObject> result=filtered(b);if(result.isEmpty())throw new IOException("Danh sách trống. Quét lại video.");
  return new ArrayList<>(result);
 }
 List<JSONObject> filtered(JSONObject b){return select(entries,b);}
 static List<JSONObject> select(List<JSONObject> entries,JSONObject b){
  String query=b.optString("query").trim().toLowerCase(Locale.ROOT);
  List<JSONObject> result=new ArrayList<>();for(JSONObject item:entries)if(item.optString("title").toLowerCase(Locale.ROOT).contains(query))result.add(item);
  if(b.optString("sort","newest").equals("newest"))result.sort(Comparator.<JSONObject>comparingLong(v->v.optLong("date")).reversed().thenComparing(v->v.optString("title"),MediaOrder::compare));
  else result.sort((a,c)->MediaOrder.compare(a.optString("title"),c.optString("title")));
  return result;
 }
 void scan(String requested)throws Exception{
  List<JSONObject> found=new ArrayList<>();warning="";
  if(requested.equals("folder"))scanFolder(found);else scanDevice(found);
  Map<String,JSONObject> index=new HashMap<>();for(JSONObject v:found)index.put(v.optString("id"),v);
  entries=List.copyOf(found);byId=Map.copyOf(index);mode=requested;
  context.getSharedPreferences("library",0).edit().putString("mode",mode).apply();
  if(found.size()>=LIMIT)warning="Đã hiển thị giới hạn 10.000 video. Chọn thư mục nhỏ hơn để xem phần còn lại.";
 }
 void add(List<JSONObject> list,Uri uri,String title,long duration,long size,long date){
  list.add(Json.obj("id",UUID.randomUUID().toString(),"url",uri.toString(),"title",title==null?"Video":title,"durationMs",duration,"size",size,"date",date,"local",true));
 }
 void scanDevice(List<JSONObject> found)throws Exception{
  label="Video trên máy";
  Uri base=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
  String[] columns={MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME,MediaStore.Video.Media.DURATION,MediaStore.Video.Media.SIZE,MediaStore.Video.Media.DATE_ADDED};
  try(Cursor c=context.getContentResolver().query(base,columns,MediaStore.Video.Media.IS_PENDING+"=0",null,MediaStore.Video.Media.DATE_ADDED+" DESC")){
   if(c==null)throw new IOException("Không đọc được thư viện Android.");
   while(c.moveToNext()&&found.size()<LIMIT){check();add(found,ContentUris.withAppendedId(base,c.getLong(0)),c.getString(1),c.getLong(2),c.getLong(3),c.getLong(4)*1000);}
  }catch(SecurityException denied){warning="Chưa có quyền đọc thư viện. Đại ca có thể chọn một thư mục.";}
 }
 void scanFolder(List<JSONObject> found)throws Exception{
  if(folder().isEmpty())throw new IOException("Chưa chọn thư mục.");
  Uri tree=Uri.parse(folder());DocumentFile root=DocumentFile.fromTreeUri(context,tree);
  if(root==null||!root.canRead())throw new IOException("Quyền thư mục đã hết. Bấm Chọn thư mục để cấp lại.");
  label=Objects.toString(root.getName(),"Thư mục");
  ArrayDeque<String> dirs=new ArrayDeque<>();dirs.add(DocumentsContract.getTreeDocumentId(tree));Set<String> seen=new HashSet<>();int skipped=0,visited=0;
  String[] cols={DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED};
  while(!dirs.isEmpty()&&found.size()<LIMIT&&visited<20000){
   check();String dir=dirs.removeFirst();if(!seen.add(dir))continue;
   try(Cursor c=context.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,dir),cols,null,null,null)){
    if(c==null)throw new IOException("Không đọc được thư mục.");
    while(c.moveToNext()&&found.size()<LIMIT&&visited++<20000){
     check();String id=c.getString(0),name=Objects.toString(c.getString(1),"Video"),mime=Objects.toString(c.getString(2),"");
     if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))dirs.addLast(id);
     else if(mime.startsWith("video/")||name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|mkv|webm|mov|avi|m4v|ts|3gp)$"))
      add(found,DocumentsContract.buildDocumentUriUsingTree(tree,id),name,0,c.getLong(3),c.getLong(4));
    }
   }catch(SecurityException|IOException e){if(seen.size()==1)throw e;skipped++;}
  }
  warning="Thư mục dùng ngày sửa đổi vì Android không cung cấp ngày tạo file.";
  if(skipped>0||visited>=20000)warning+=" Một số thư mục con chưa đọc được hoặc cây thư mục quá lớn.";
 }
 static void check()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
 String thumbnail(String id)throws Exception{
  JSONObject item=byId.get(id);if(item==null)throw new IOException("Video đã thay đổi. Quét lại danh sách.");
  Uri uri=Uri.parse(item.getString("url"));Bitmap bitmap;
  try{bitmap=context.getContentResolver().loadThumbnail(uri,new Size(320,180),null);}
  catch(Exception first){
   try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(context,uri);bitmap=r.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,320,180);}
  }
  if(bitmap==null)throw new IOException("Video này chưa có thumbnail.");
  try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
   bitmap.compress(Bitmap.CompressFormat.JPEG,78,out);
   if(out.size()>256*1024)throw new IOException("Thumbnail quá lớn.");
   return "data:image/jpeg;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
  }finally{bitmap.recycle();}
 }
}
