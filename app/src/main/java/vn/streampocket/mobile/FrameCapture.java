package vn.streampocket.mobile;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
final class FrameCapture {
 static String save(Context context,Bitmap bitmap,String title,long position)throws IOException{
  ContentResolver resolver=context.getContentResolver();ContentValues values=new ContentValues();
  String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.ROOT).format(new Date());
  String name=Files.safe(title)+"-"+position+"ms-"+stamp+".png";
  values.put(MediaStore.Images.Media.DISPLAY_NAME,name);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
  values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Stream Pocket");values.put(MediaStore.Images.Media.IS_PENDING,1);
  Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("Không tạo được ảnh.");
  boolean committed=false;
  try{
   try(OutputStream out=resolver.openOutputStream(uri,"w")){
    if(out==null||!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Không lưu được ảnh.");
   }
   ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);
   if(resolver.update(uri,ready,null,null)!=1)throw new IOException("Không hoàn tất lưu ảnh.");
   committed=true;return name;
  }finally{if(!committed)resolver.delete(uri,null,null);}
 }
}
