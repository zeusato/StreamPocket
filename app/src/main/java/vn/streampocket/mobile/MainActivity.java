package vn.streampocket.mobile;
import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.webkit.*;
import androidx.webkit.WebViewAssetLoader;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 FeatureLock featureLock;AlertDialog unlockDialog;AppUpdates updates;
 WebView web;final ExecutorService pool=Executors.newFixedThreadPool(4),imagePool=Executors.newSingleThreadExecutor();
 final Map<String,JSONObject> picked=new ConcurrentHashMap<>();
 String pendingPick,pendingFolder,pendingLibrary;JSONObject pendingLibraryBody;Vault vault;LocalLibrary library;
 final ExecutorService libraryPool=Executors.newSingleThreadExecutor();
 static final String HOME="https://appassets.androidplatform.net/assets/index.html";
 @Override public void onCreate(Bundle state){
  super.onCreate(state);updates=AppUpdates.get(this);featureLock=new FeatureLock(new java.io.File(getNoBackupFilesDir(),"source-lock"));vault=new Vault(this);Jobs.init(this);library=new LocalLibrary(this);
  web=new WebView(this);android.widget.FrameLayout content=new android.widget.FrameLayout(this);content.setBackgroundColor(0xff101814);content.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));setContentView(content);
  content.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());}return insets;});
  content.requestApplyInsets();
  web.setBackgroundColor(0xff101814);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  WebViewAssetLoader assets=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){
    // WebView also intercepts data: resources. Let it decode our bounded JPEG thumbnails.
    if(LocalResources.isThumbnail(r.getUrl().toString()))return null;
    WebResourceResponse local=assets.shouldInterceptRequest(r.getUrl());if(local!=null)return local;
    return new WebResourceResponse("text/plain","utf-8",403,"Forbidden",Map.of(),new java.io.ByteArrayInputStream(new byte[0]));
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return true;}
  });
  web.addJavascriptInterface(new Bridge(),"Phone");web.loadUrl(HOME);
 }
 void reply(String id,Object data,String error){
  runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())web.evaluateJavascript("window.nativeReply("+JSONObject.quote(id)+","+String.valueOf(data==null?"null":data)+","+(error==null?"null":JSONObject.quote(error))+")",null);});
 }
 final class Bridge {
  @JavascriptInterface public void request(String id,String method,String input){
   if(isDestroyed()||pool.isShutdown())return;
   (("image".equals(method)||"localThumbnail".equals(method))?imagePool:pool).execute(()->{try{
    JSONObject b=new JSONObject(input);Object result;
    boolean protectedRequest=FeatureAccess.protectedRequest(method,b);if(protectedRequest)featureLock.require();
    if("retry".equals(method)||"cancel".equals(method))requireJobAccess(b.getString("id"));
    switch(method){
     case "updateStatus":result=updates.status();break;
     case "checkUpdates":result=updates.check(b.optBoolean("force"));break;
     case "downloadUpdate":result=updates.download();break;
     case "cancelUpdate":result=updates.cancel();break;
     case "installUpdate":requestInstall(id);return;
     case "localList":runOnUiThread(()->requestLibrary(id,b));return;
     case "chooseFolder":runOnUiThread(()->chooseFolder(id));return;
     case "localThumbnail":result=Json.obj("data",library.thumbnail(b.getString("id")));break;
     case "localPlay":{
      List<JSONObject> playlist=library.playlist(b);int index=PlaybackSession.index(playlist,b.getString("id"));
      if(index<0)throw new Exception("Video đã thay đổi. Quét lại danh sách.");
      runOnUiThread(()->PlaybackSession.open(MainActivity.this,playlist,index));result=Json.obj("ok",true);break;
     }
     case "catalogue":result=Sources.catalogue(b);break;
     case "episodes":result=Sources.episodes(b);break;
     case "list":result=Sources.ptList(b);break;
     case "image":result=Json.obj("data",Sources.image(b));break;
     case "diagnostics":result=Json.obj("text",Diagnostics.report(MainActivity.this));break;
     case "copyDiagnostics":getSystemService(android.content.ClipboardManager.class).setPrimaryClip(android.content.ClipData.newPlainText("Stream Pocket",Diagnostics.report(MainActivity.this)));result=Json.obj("ok",true);break;
     case "settings":result=Json.obj("token",featureLock.isUnlocked()?vault.get("token",""):"","output","Downloads/Stream Pocket","encoder",HardwareEncoder.capabilities(),"libraryMode",library.savedMode(),"downloadsEnabled",featureLock.isUnlocked(),"passwordSet",featureLock.hasPassword(),"domains",SourceSites.domains(),"version",BuildConfig.VERSION_NAME);break;
     case "downloadFeatures":runOnUiThread(()->changeDownloadFeatures(id,b.optBoolean("enabled")));return;
     case "token":vault.put("token",b.optBoolean("remember")?b.optString("token"):"");result=Json.obj("ok",true);break;
     case "jobs":{
      JSONArray jobs=Jobs.list(MainActivity.this),visibleJobs=new JSONArray();for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i),spec=job.optJSONObject("spec");String mode=spec==null?"download":spec.optString("mode","download");if(!featureLock.isUnlocked()&&!FeatureAccess.tool(mode))continue;Json.put(job,"mode",mode);job.remove("spec");visibleJobs.put(job);}result=visibleJobs;break;
     }
     case "enqueue":{
      JSONArray items=b.getJSONArray("items");if(items.length()<1||items.length()>250)throw new Exception("Chọn từ 1 đến 250 video.");
      if(b.optString("mode").equals("compact")||b.optString("mode").equals("merge")){
       JSONArray resolved=new JSONArray();Set<String> unique=new HashSet<>();
       for(int i=0;i<items.length();i++){String key=items.getJSONObject(i).getString("id");if(!unique.add(key)||!picked.containsKey(key))throw new Exception("Chọn lại danh sách file.");resolved.put(picked.get(key));}Json.put(b,"items",resolved);
      }else for(int i=0;i<items.length();i++)if(!items.getJSONObject(i).optString("id").matches("(?:hg-[1-9]\\d*-[1-9]\\d*|\\d+)"))throw new Exception("Mã video không hợp lệ.");
      String job=Jobs.enqueue(MainActivity.this,b);runOnUiThread(()->{if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},3);JobService.start(MainActivity.this);});result=Json.obj("id",job);break;
     }
     case "cancel":Jobs.cancel(MainActivity.this,b.getString("id"));result=Json.obj("ok",true);break;
     case "retry":Jobs.retry(MainActivity.this,b.getString("id"));runOnUiThread(()->JobService.start(MainActivity.this));result=Json.obj("ok",true);break;
     case "clear":Jobs.clear(MainActivity.this,b.optBoolean("toolsOnly"));result=Json.obj("ok",true);break;
     case "pick":runOnUiThread(()->pick(id));return;
     case "play":{
      List<JSONObject> playlist=PlaybackSession.online(b);int index=PlaybackSession.index(playlist,b.getString("id"));
      if(index<0)throw new Exception("Không tìm thấy video trong danh sách.");
      runOnUiThread(()->PlaybackSession.open(MainActivity.this,playlist,index));result=Json.obj("ok",true);break;
     }
     case "external":{
      String url=b.getString("url"),origin=Net.origin(url);
      if(!SourceSites.known(origin)&&!"https://translate.google.com".equals(origin))throw new Exception("Link không hợp lệ.");
      runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){new AlertDialog.Builder(MainActivity.this).setMessage("Chưa có trình duyệt để mở link.").setPositiveButton("Đóng",null).show();}});
      result=Json.obj("ok",true);break;
     }
     case "open":{
      String uri=b.getString("uri");if(!uri.startsWith("content://media/"))throw new Exception("File không hợp lệ.");
      JSONObject video=Json.obj("url",uri,"title",Files.name(MainActivity.this,Uri.parse(uri)),"local",true);
      runOnUiThread(()->PlaybackSession.open(MainActivity.this,List.of(video),0));
      result=Json.obj("ok",true);break;
     }
     default:throw new Exception("Thao tác không được hỗ trợ.");
    }if(protectedRequest)featureLock.require();reply(id,result,null);
   }catch(Exception e){if("image".equals(method))Diagnostics.imageError(MainActivity.this,e);reply(id,null,Objects.toString(e.getMessage(),"Có lỗi xảy ra."));}});
  }
 }
 void requestInstall(String callback){
  pool.execute(()->{
   try{
    java.io.File file=updates.prepareInstall();
    runOnUiThread(()->{
     if(isFinishing()||isDestroyed())return;
     try{
      if(!getPackageManager().canRequestPackageInstalls()){
       updates.installMessage("Cho phép Stream Pocket cài ứng dụng, rồi quay lại để tiếp tục.");
       startActivityForResult(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())),31);
      }else{
       Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",file);
       Intent install=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
       install.setClipData(ClipData.newRawUri("Stream Pocket update",uri));startActivity(install);
       updates.installMessage("Xác nhận trong trình cài đặt Android. Nếu đã hủy, có thể bấm Cài đặt lại.");
      }
      if(callback!=null)reply(callback,updates.status(),null);
     }catch(Exception e){updates.note("Không mở được trình cài đặt: "+Objects.toString(e.getMessage(),"Lỗi Android."));if(callback!=null)reply(callback,null,e.getMessage());}
    });
   }catch(Exception e){updates.note(Objects.toString(e.getMessage(),"Không cài được bản cập nhật."));if(callback!=null)reply(callback,null,e.getMessage());}
  });
 }
 void changeDownloadFeatures(String id,boolean enabled){
  if(!enabled){featureLock.lock();reply(id,Json.obj("enabled",false),null);return;}
  if(featureLock.isUnlocked()){reply(id,Json.obj("enabled",true),null);return;}
  if(unlockDialog!=null){reply(id,null,"Cửa sổ mở khóa đang mở.");return;}
  boolean setup=!featureLock.hasPassword();
  android.widget.LinearLayout fields=new android.widget.LinearLayout(this);fields.setOrientation(android.widget.LinearLayout.VERTICAL);int pad=(int)(24*getResources().getDisplayMetrics().density);fields.setPadding(pad,0,pad,0);
  android.widget.TextView help=new android.widget.TextView(this);help.setText(setup?"Tạo mật khẩu từ 6 đến 128 ký tự để mở hai tab ẩn.":"Nhập mật khẩu để mở Thư viện nguồn và Hàng đợi.");fields.addView(help);
  android.widget.EditText password=passwordField("Mật khẩu"),confirm=passwordField("Nhập lại mật khẩu");fields.addView(password);if(setup)fields.addView(confirm);
  android.widget.TextView error=new android.widget.TextView(this);error.setTextColor(0xffffb4ab);fields.addView(error);
  android.widget.TextView reset=new android.widget.TextView(this);reset.setText("Không có chức năng khôi phục mật khẩu. Quên mật khẩu cần gỡ app rồi cài lại; cài đè không reset.");reset.setPadding(0,pad/2,0,0);fields.addView(reset);
  AlertDialog dialog=new AlertDialog.Builder(this).setTitle(setup?"Đặt mật khẩu":"Mở khóa hai tab").setView(fields).setNegativeButton("Hủy",null).setPositiveButton(setup?"Tạo và mở":"Mở khóa",null).create();
  unlockDialog=dialog;java.util.concurrent.atomic.AtomicBoolean answered=new java.util.concurrent.atomic.AtomicBoolean();
  dialog.setOnDismissListener(d->{password.setText("");confirm.setText("");unlockDialog=null;if(answered.compareAndSet(false,true))reply(id,Json.obj("enabled",false),null);});
  dialog.setOnShowListener(d->{
   dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
    char[] pass=password.getText().toString().toCharArray(),confirmation=confirm.getText().toString().toCharArray();
    password.setText("");confirm.setText("");error.setText("Đang kiểm tra…");
    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);dialog.setCancelable(false);
    pool.execute(()->{
     String failure=null;
     try{if(setup)featureLock.create(pass,confirmation);else if(!featureLock.unlock(pass))failure="Mật khẩu không đúng.";}catch(Exception e){failure=Objects.toString(e.getMessage(),"Không mở khóa được.");}
     finally{Arrays.fill(pass,'\0');Arrays.fill(confirmation,'\0');}
     final String message=failure;
     runOnUiThread(()->{
      if(isFinishing()||isDestroyed()){featureLock.lock();return;}
      if(message==null){answered.set(true);reply(id,Json.obj("enabled",true),null);dialog.dismiss();}
      else{error.setText(message);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);dialog.setCancelable(true);password.requestFocus();}
     });
    });
   });
  });dialog.show();password.requestFocus();
 }
 android.widget.EditText passwordField(String hint){
  android.widget.EditText field=new android.widget.EditText(this);field.setHint(hint);field.setSingleLine(true);field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
  field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});field.setSaveEnabled(false);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);return field;
 }
 void requireJobAccess(String id)throws Exception{
  JSONArray jobs=Jobs.list(this);
  for(int i=0;i<jobs.length();i++){JSONObject job=jobs.getJSONObject(i);if(id.equals(job.optString("id"))){JSONObject spec=job.optJSONObject("spec");if(spec==null||!FeatureAccess.tool(spec.optString("mode")))featureLock.require();return;}}
  throw new Exception("Không tìm thấy tác vụ.");
 }

 void requestLibrary(String id,JSONObject b){
  if(b.optBoolean("requestAccess")&&b.optString("mode","device").equals("device")&&!library.fullAccess()){
   if(pendingLibrary!=null){reply(id,null,"Đang chờ cấp quyền thư viện.");return;}
   pendingLibrary=id;pendingLibraryBody=b;
   String[] permissions=Build.VERSION.SDK_INT>=34?new String[]{"android.permission.READ_MEDIA_VIDEO","android.permission.READ_MEDIA_VISUAL_USER_SELECTED"}:new String[]{Build.VERSION.SDK_INT>=33?"android.permission.READ_MEDIA_VIDEO":"android.permission.READ_EXTERNAL_STORAGE"};
   requestPermissions(permissions,21);return;
  }loadLibrary(id,b);
 }
 void loadLibrary(String id,JSONObject b){
  if(libraryPool.isShutdown())return;
  libraryPool.execute(()->{try{reply(id,library.list(b),null);}catch(Exception e){reply(id,null,Objects.toString(e.getMessage(),"Không đọc được thư viện."));}});
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
  super.onRequestPermissionsResult(request,permissions,grants);
  if(request==21&&pendingLibrary!=null){String id=pendingLibrary;JSONObject b=pendingLibraryBody;pendingLibrary=null;pendingLibraryBody=null;loadLibrary(id,b);}
 }
 void chooseFolder(String id){
  if(pendingFolder!=null||pendingPick!=null){reply(id,null,"Cửa sổ chọn file đang mở.");return;}
  pendingFolder=id;
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
  if(!library.folder().isEmpty())intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(library.folder()));
  try{startActivityForResult(intent,12);}catch(Exception e){pendingFolder=null;reply(id,null,"Không mở được bộ chọn thư mục.");}
 }
 void pick(String id){
  if(pendingPick!=null||pendingFolder!=null){reply(id,null,"Cửa sổ chọn file đang mở.");return;}pendingPick=id;
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("video/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
  intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,11);
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(request==31){if(getPackageManager().canRequestPackageInstalls())requestInstall(null);else updates.installMessage("Chưa được cấp quyền cài ứng dụng. Bấm Cài đặt để thử lại.");return;}
  if(request==12){
   String callback=pendingFolder;pendingFolder=null;if(callback==null)return;
   try{
    if(result!=RESULT_OK||data==null||data.getData()==null){reply(callback,Json.obj("cancelled",true),null);return;}
    Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
    library.rememberFolder(uri);reply(callback,Json.obj("cancelled",false),null);
   }catch(Exception e){reply(callback,null,"Không giữ được quyền thư mục. Chọn lại thư mục khác.");}
   return;
  }
  if(request!=11)return;String callback=pendingPick;pendingPick=null;
  try{JSONArray list=new JSONArray();if(result==RESULT_OK&&data!=null){
   List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());
   for(Uri uri:new LinkedHashSet<>(uris)){JSONObject item=null;for(JSONObject old:picked.values())if(old.optString("uri").equals(uri.toString()))item=old;
    if(item==null){item=Files.pick(this,uri,data.getFlags());picked.put(item.getString("id"),item);}list.put(Json.obj("id",item.getString("id"),"name",item.getString("name")));}
  }reply(callback,list,null);}catch(Exception e){reply(callback,null,e.getMessage());}
 }
 @Override public void onBackPressed(){web.evaluateJavascript("window.back && window.back()",null);}
 @Override protected void onDestroy(){featureLock.lock();if(unlockDialog!=null)unlockDialog.dismiss();web.removeJavascriptInterface("Phone");web.destroy();pool.shutdown();imagePool.shutdownNow();libraryPool.shutdownNow();super.onDestroy();}
}
