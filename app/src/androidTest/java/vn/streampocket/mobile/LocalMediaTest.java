package vn.streampocket.mobile;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
/** Only creates and deletes its own test media; requires a connected Android device. */
@RunWith(AndroidJUnit4.class)
public class LocalMediaTest {
 @Test public void ownedVideoIsListedWithThumbnailAndCanPublishFrame()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  ContentResolver r=context.getContentResolver();ContentValues values=new ContentValues();
  String title="StreamPocket-test-"+UUID.randomUUID();
  values.put(MediaStore.Video.Media.DISPLAY_NAME,title+".mp4");values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
  values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/StreamPocket-tests");values.put(MediaStore.Video.Media.IS_PENDING,1);
  Uri video=r.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(video);Uri picture=null;
  try{
   try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("sample.mp4");OutputStream out=r.openOutputStream(video)){
    assertNotNull(out);byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
   }
   ContentValues ready=new ContentValues();ready.put(MediaStore.Video.Media.IS_PENDING,0);assertEquals(1,r.update(video,ready,null,null));
   LocalLibrary library=new LocalLibrary(context);JSONObject list=library.list(Json.obj("mode","device","refresh",true,"query",title,"sort","name"));
   assertEquals(1,list.getInt("total"));String id=list.getJSONArray("items").getJSONObject(0).getString("id");
   String thumbnail=library.thumbnail(id);assertTrue(thumbnail.startsWith("data:image/jpeg;base64,"));
   byte[] image=android.util.Base64.decode(thumbnail.substring(thumbnail.indexOf(',')+1),android.util.Base64.DEFAULT);
   Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length);assertNotNull(bitmap);
   String name;try{name=FrameCapture.save(context,bitmap,title,0);}finally{bitmap.recycle();}
   try(Cursor c=r.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Images.Media._ID,MediaStore.Images.Media.IS_PENDING},MediaStore.Images.Media.DISPLAY_NAME+"=?",new String[]{name},null)){
    assertNotNull(c);assertTrue(c.moveToFirst());picture=ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0));assertEquals(0,c.getInt(1));
   }
   try(InputStream in=r.openInputStream(picture)){assertNotNull(in);assertEquals(137,in.read());assertEquals(80,in.read());}
  }finally{if(picture!=null)r.delete(picture,null,null);r.delete(video,null,null);}
 }
}
