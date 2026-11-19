package vn.streampocket.mobile;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.*;
import org.json.*;
import java.io.*;
import java.util.*;

public final class Files {
 public static String name(Context c,Uri uri){
  try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
   if(cursor!=null&&cursor.moveToFirst())return cursor.getString(0);
  }return "Video";
 }
 public static JSONObject pick(Context c,Uri uri,int flags)throws Exception{
  c.getContentResolver().takePersistableUriPermission(uri,flags&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
  JSONObject item=Json.obj("id",UUID.randomUUID().toString(),"name",name(c,uri),"uri",uri.toString());
  Json.put(item,"stamp",stamp(c,uri));return item;
 }
 public static void copy(Context c,String uri,File destination,Hls.Stop stop)throws Exception{
  try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(uri));OutputStream out=new FileOutputStream(destination)){
   if(in==null)throw new IOException("Không mở được file đã chọn.");byte[] buf=new byte[131072];int n;
   while((n=in.read(buf))!=-1){stop.check();out.write(buf,0,n);}
  }
 }
 public static String safe(String name){return name.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").replaceAll("^[. ]+|[. ]+$","").substring(0,Math.min(100,name.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").replaceAll("^[. ]+|[. ]+$","").length()));}
 public static JSONObject publish(Context c,File input,String title,String ext,Hls.Stop stop)throws Exception{
  ContentResolver r=c.getContentResolver();ContentValues v=new ContentValues();
  String name=safe(title)+"."+ext;
  v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,ext.equals("mp4")?"video/mp4":"video/mp2t");
  v.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/Stream Pocket");v.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri uri=r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("Không tạo được file trong Downloads.");
  boolean committed=false;
  try{
   try(InputStream in=new FileInputStream(input);OutputStream out=r.openOutputStream(uri,"w")){
    if(out==null)throw new IOException("Không ghi được file.");byte[] buf=new byte[131072];int n;
    while((n=in.read(buf))!=-1){stop.check();out.write(buf,0,n);}
   }
   stop.check();ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);
   if(r.update(uri,ready,null,null)!=1)throw new IOException("Không xác nhận được lưu file.");
   committed=true;return Json.obj("name",name,"uri",uri.toString(),"size",input.length());
  }finally{if(!committed)r.delete(uri,null,null);}
 }
 public static String stamp(Context c,Uri uri){
  try(Cursor cursor=c.getContentResolver().query(uri,new String[]{DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED},null,null,null)){
   if(cursor!=null&&cursor.moveToFirst())return cursor.getLong(0)+":"+cursor.getLong(1);
  }catch(Exception ignored){}return "";
 }
 public static String delete(Context c,JSONObject original){
  try{
   Uri uri=Uri.parse(original.getString("uri"));
   String previous=original.optString("stamp");
   if(previous.isEmpty() || !previous.equals(stamp(c,uri)))return "Giữ bản gốc vì file đã đổi hoặc chưa xác nhận được danh tính.";
   if(!DocumentsContract.isDocumentUri(c,uri))return "Android yêu cầu xóa file gốc trong trình quản lý tệp.";
   if(DocumentsContract.deleteDocument(c.getContentResolver(),uri))return "Đã xóa bản gốc.";
   return "Không xóa được bản gốc.";
  }catch(Exception e){return "Android chưa cấp quyền xóa bản gốc. Đại ca có thể xóa bằng trình quản lý tệp.";}
 }
}
