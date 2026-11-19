package vn.streampocket.mobile;
import android.app.*;
import android.os.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.*;
import androidx.media3.ui.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlayerActivity extends Activity {
 ExoPlayer player;PlayerView view;GestureFrame surface;
 LinearLayout hud,seekTimeline;TextView status,gestureText,seekPosition,seekDuration;ImageView preview;
 ImageButton rotate,previous,next,capture;DefaultTimeBar seekProgress;View[] controlRegions;
 PlaybackSession session;String sessionId;JSONObject current;int index,generation;
 boolean visible,rendered,capturing,seekWasPlaying;float playbackRate=1;
 final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService io=Executors.newSingleThreadExecutor(),saver=Executors.newSingleThreadExecutor();Future<?> resolving;
 FramePreview frames;Bitmap previewBitmap;long previewGeneration,lastPreviewAt;
 final PlaybackGesture gesture=new PlaybackGesture();long seekStart,seekTarget,duration;
 float brightnessStart,volumeStart;AudioManager audio;
 @Override public void onCreate(Bundle state){
  super.onCreate(state);sessionId=getIntent().getStringExtra("session");session=PlaybackSession.sessions.get(sessionId==null?"":sessionId);
  if(session==null){Toast.makeText(this,"Danh sách phát đã hết phiên. Mở lại video.",Toast.LENGTH_LONG).show();finish();return;}
  frames=new FramePreview(this);audio=getSystemService(AudioManager.class);setVolumeControlStream(AudioManager.STREAM_MUSIC);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
  surface=new GestureFrame();root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
  view=new PlayerView(this);view.setBackgroundColor(Color.BLACK);view.setControllerShowTimeoutMs(3500);view.setControllerAnimationEnabled(false);view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
  view.setShowNextButton(false);view.setShowPreviousButton(false);
  surface.addView(view,new FrameLayout.LayoutParams(-1,-1));
  arrangeControls();
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(14);status.setGravity(Gravity.CENTER);status.setPadding(dp(20),dp(12),dp(20),dp(12));
  status.setBackgroundColor(0xaa000000);surface.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
  hud=new LinearLayout(this);hud.setOrientation(LinearLayout.VERTICAL);hud.setGravity(Gravity.CENTER);hud.setPadding(dp(14),dp(12),dp(14),dp(12));hud.setBackgroundColor(Color.TRANSPARENT);
  preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(Color.BLACK);preview.setVisibility(View.GONE);surface.addView(preview,new FrameLayout.LayoutParams(-1,-1));
  gestureText=new TextView(this);gestureText.setTextColor(Color.WHITE);gestureText.setGravity(Gravity.CENTER);gestureText.setTextSize(15);gestureText.setShadowLayer(dp(3),0,dp(1),Color.BLACK);hud.addView(gestureText);
  hud.setVisibility(View.GONE);surface.addView(hud,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
  seekTimeline=new LinearLayout(this);seekTimeline.setOrientation(LinearLayout.VERTICAL);seekTimeline.setPadding(dp(16),0,dp(16),dp(12));seekTimeline.setVisibility(View.GONE);
  seekProgress=new DefaultTimeBar(this);seekProgress.setPlayedColor(0xff8ad9b7);seekProgress.setScrubberColor(Color.WHITE);seekProgress.setBufferedColor(0x99ffffff);seekProgress.setUnplayedColor(0x66ffffff);
  seekProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  seekTimeline.addView(seekProgress,new LinearLayout.LayoutParams(-1,dp(36)));
  LinearLayout times=new LinearLayout(this);seekPosition=timeLabel();seekDuration=timeLabel();seekDuration.setGravity(Gravity.END);
  times.addView(seekPosition,new LinearLayout.LayoutParams(0,-2,1));times.addView(seekDuration,new LinearLayout.LayoutParams(0,-2,1));seekTimeline.addView(times);
  surface.addView(seekTimeline,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  root.setOnApplyWindowInsetsListener((v,insets)->{
   if(Build.VERSION.SDK_INT>=30){Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);}
   else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
   return insets;
  });
  setContentView(root);root.requestApplyInsets();
  updateRotationLabel();
  open(state==null?session.start:state.getInt("index",session.start));
  if(state!=null)restorePosition=state.getLong("position");
 }
 long restorePosition;
 void arrangeControls(){
  View dim=view.findViewById(androidx.media3.ui.R.id.exo_controls_background);dim.setBackgroundColor(Color.TRANSPARENT);
  View bottom=view.findViewById(androidx.media3.ui.R.id.exo_bottom_bar);bottom.setBackgroundColor(Color.TRANSPARENT);
  LinearLayout center=view.findViewById(androidx.media3.ui.R.id.exo_center_controls);
  previous=iconButton(androidx.media3.ui.R.drawable.exo_ic_skip_previous,"Tập trước",()->open(index-1));
  next=iconButton(androidx.media3.ui.R.drawable.exo_ic_skip_next,"Tập tiếp theo",()->open(index+1));
  center.addView(previous,0,new LinearLayout.LayoutParams(dp(48),dp(48)));center.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));
  LinearLayout basic=view.findViewById(androidx.media3.ui.R.id.exo_basic_controls);
  capture=iconButton(R.drawable.ic_capture_frame,"Chụp khung hình",this::captureFrame);
  rotate=iconButton(R.drawable.ic_rotate_screen,"Xoay màn hình",()->setRequestedOrientation(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE?ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE));
  int settings=basic.indexOfChild(view.findViewById(androidx.media3.ui.R.id.exo_settings));
  basic.addView(capture,settings,new LinearLayout.LayoutParams(dp(44),dp(44)));basic.addView(rotate,settings+1,new LinearLayout.LayoutParams(dp(44),dp(44)));
  controlRegions=new View[]{center,bottom,view.findViewById(androidx.media3.ui.R.id.exo_progress)};
 }
 ImageButton iconButton(int drawable,String description,Runnable click){
  ImageButton b=new ImageButton(this);b.setImageResource(drawable);b.setContentDescription(description);b.setTooltipText(description);b.setPadding(dp(10),dp(10),dp(10),dp(10));
  b.setImageTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{Color.WHITE,0x55ffffff}));
  android.util.TypedValue ripple=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,ripple,true);b.setBackgroundResource(ripple.resourceId);
  b.setOnClickListener(v->{view.showController();click.run();});return b;
 }
 TextView timeLabel(){TextView label=new TextView(this);label.setTextColor(Color.WHITE);label.setTextSize(13);label.setShadowLayer(dp(3),0,dp(1),Color.BLACK);return label;}
 boolean touchesControl(MotionEvent e){
  if(controlRegions==null)return false;Rect bounds=new Rect();
  for(View region:controlRegions)if(region!=null&&region.isShown()&&region.getAlpha()>.1f&&region.getGlobalVisibleRect(bounds)&&bounds.contains((int)e.getRawX(),(int)e.getRawY()))return true;
  return false;
 }
 void updateSeekTimeline(){
  seekProgress.setDuration(duration);seekProgress.setPosition(seekTarget);seekProgress.setBufferedPosition(player==null?0:player.getBufferedPosition());
  seekPosition.setText(clock(seekTarget));seekDuration.setText(clock(duration));seekTimeline.setVisibility(View.VISIBLE);
 }
 void open(int target){
  if(target<0||target>=session.items.size()||isFinishing())return;
  endGesture(false);index=target;int ticket=++generation;rendered=false;restorePosition=0;current=null;capture.setEnabled(false);
  previous.setEnabled(index>0);next.setEnabled(index<session.items.size()-1);
  JSONObject spec=session.items.get(index);
  status.setText("Đang mở video…");status.setVisibility(View.VISIBLE);view.showController();
  if(player!=null){view.setPlayer(null);player.release();player=null;}
  if(resolving!=null)resolving.cancel(true);
  resolving=io.submit(()->{
   try{
    JSONObject source=spec.optBoolean("local")?spec:Sources.media(spec);
    if(spec.optBoolean("local")&&!source.optString("url").startsWith("content://"))throw new IllegalArgumentException("File không hợp lệ.");
    main.post(()->{if(ticket!=generation||isFinishing()||isDestroyed())return;try{prepare(source);}catch(Exception e){showError(e.getMessage());}});
   }catch(Exception e){Diagnostics.record(PlayerActivity.this,e);main.post(()->{if(ticket==generation&&!isFinishing()&&!isDestroyed())showError(e.getMessage());});}
  });
 }
 void prepare(JSONObject source){
  current=source;
  androidx.media3.datasource.okhttp.OkHttpDataSource.Factory http=new androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(Net.forSource(source)).setUserAgent("Mozilla/5.0 StreamPocket/1.0");
  DefaultDataSource.Factory base=new DefaultDataSource.Factory(this,http);
  ResolvingDataSource.Factory data=new ResolvingDataSource.Factory(base,spec->{
   if(source.optBoolean("local")){
    if(!spec.uri.toString().equals(source.optString("url")))throw new java.io.IOException("Đường dẫn video không hợp lệ.");
    return spec;
   }
   Net.allowed(spec.uri.toString(),source);return spec.withRequestHeaders(Net.headers(spec.uri.toString(),source));
  });
  player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(data)).build();
  player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
  player.setHandleAudioBecomingNoisy(true);player.setSeekParameters(SeekParameters.EXACT);player.setPlaybackSpeed(playbackRate);view.setPlayer(player);
  player.addListener(new Player.Listener(){
   @Override public void onPlaybackParametersChanged(PlaybackParameters parameters){playbackRate=parameters.speed;}
   @Override public void onRenderedFirstFrame(){rendered=true;capture.setEnabled(!capturing);status.setVisibility(View.GONE);}
   @Override public void onPlaybackStateChanged(int state){
    if(state==Player.STATE_ENDED&&player!=null&&player.getPlayWhenReady()&&gesture.mode==PlaybackGesture.Mode.NONE&&visible&&index+1<session.items.size())open(index+1);
   }
   @Override public void onPlayerError(PlaybackException e){Diagnostics.record(PlayerActivity.this,e);rendered=false;capture.setEnabled(false);showError("Không phát được video ("+e.getErrorCodeName()+").");}
  });
  player.setMediaItem(MediaItem.fromUri(source.optString("url")));
  if(restorePosition>0){player.seekTo(restorePosition);restorePosition=0;}
  player.prepare();player.setPlayWhenReady(visible);
 }
 void showError(String message){
  status.setText(Objects.toString(message,"Không mở được video."));status.setVisibility(View.VISIBLE);view.showController();
  new AlertDialog.Builder(this).setMessage(status.getText()).setPositiveButton("Thử lại",(d,w)->open(index)).setNegativeButton("Để sau",null).show();
 }
 void beginGesture(MotionEvent e){
  gesture.begin(e.getX(),e.getY(),surface.getWidth(),surface.getHeight());
  seekStart=player==null?0:player.getCurrentPosition();seekTarget=seekStart;duration=player==null?0:player.getDuration();
  float brightness=getWindow().getAttributes().screenBrightness;
  if(brightness<0)brightness=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,128)/255f;
  brightnessStart=Math.max(.02f,brightness);volumeStart=audio.getStreamVolume(AudioManager.STREAM_MUSIC)/(float)Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
 }
 void startGesture(){
  FrameLayout.LayoutParams overlay=(FrameLayout.LayoutParams)hud.getLayoutParams();overlay.gravity=gesture.mode==PlaybackGesture.Mode.SEEK?(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL):Gravity.CENTER;overlay.bottomMargin=gesture.mode==PlaybackGesture.Mode.SEEK?dp(88):0;hud.setLayoutParams(overlay);
  hud.setVisibility(View.VISIBLE);preview.setVisibility(View.GONE);view.hideController();lastPreviewAt=0;previewGeneration++;
  if(gesture.mode==PlaybackGesture.Mode.SEEK){seekWasPlaying=player!=null&&player.getPlayWhenReady();if(player!=null){player.pause();if(duration>0&&player.isCurrentMediaItemSeekable())updateSeekTimeline();}}
 }
 void moveGesture(MotionEvent e){
  if(gesture.mode==PlaybackGesture.Mode.SEEK){
   if(player==null||duration<=0||!player.isCurrentMediaItemSeekable()){gestureText.setText("Luồng này chưa hỗ trợ tua.");return;}
   seekTarget=gesture.target(e.getX(),seekStart,duration);updateSeekTimeline();
   String delta=String.format(Locale.ROOT,"%+.1f",(seekTarget-seekStart)/1000d);
   gestureText.setText(clock(seekTarget)+" / "+clock(duration)+"\n"+delta+" giây · Nhả để tua");
   if(current!=null&&current.optBoolean("local")&&SystemClock.elapsedRealtime()-lastPreviewAt>180){
    lastPreviewAt=SystemClock.elapsedRealtime();long ticket=previewGeneration;
    frames.request(current.optString("url"),ticket,seekTarget,(g,time,bitmap)->{
     if(g!=previewGeneration||gesture.mode!=PlaybackGesture.Mode.SEEK||isDestroyed()){if(bitmap!=null)bitmap.recycle();return;}
     if(bitmap!=null){preview.setImageBitmap(bitmap);if(previewBitmap!=null)previewBitmap.recycle();previewBitmap=bitmap;preview.setVisibility(View.VISIBLE);}
    });
   }
  }else if(gesture.mode==PlaybackGesture.Mode.BRIGHTNESS){
   float level=Math.max(.02f,gesture.level(e.getY(),brightnessStart));WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=level;getWindow().setAttributes(p);
   gestureText.setText("☀ Độ sáng "+Math.round(level*100)+"%");
  }else if(gesture.mode==PlaybackGesture.Mode.VOLUME){
   if(audio.isVolumeFixed()){gestureText.setText("Âm lượng do thiết bị ngoài điều khiển.");return;}
   int max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),value=Math.round(gesture.level(e.getY(),volumeStart)*max);
   audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);gestureText.setText("Âm lượng "+Math.round(value*100f/Math.max(1,max))+"%");
  }
 }
 void endGesture(boolean commit){
  if(gesture.mode==PlaybackGesture.Mode.SEEK&&player!=null){
   if(commit&&duration>0&&player.isCurrentMediaItemSeekable())player.seekTo(seekTarget);
   if(seekWasPlaying&&visible)player.play();
  }
  gesture.end();previewGeneration++;if(frames!=null)frames.clear();
  if(hud!=null)hud.setVisibility(View.GONE);if(seekTimeline!=null)seekTimeline.setVisibility(View.GONE);
  if(preview!=null){preview.setVisibility(View.GONE);preview.setImageDrawable(null);}if(previewBitmap!=null){previewBitmap.recycle();previewBitmap=null;}
 }
 final class GestureFrame extends FrameLayout {
  boolean eligible;int pointer;
  GestureFrame(){super(PlayerActivity.this);}
  @Override public boolean onInterceptTouchEvent(MotionEvent e){
   int action=e.getActionMasked();
   if(action==MotionEvent.ACTION_DOWN){
    endGesture(false);pointer=e.getPointerId(0);
    eligible=player!=null&&!capturing&&!touchesControl(e);
    if(eligible)beginGesture(e);return false;
   }
   if(action==MotionEvent.ACTION_POINTER_DOWN){eligible=false;endGesture(false);return false;}
   if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_UP){if(gesture.mode==PlaybackGesture.Mode.NONE){eligible=false;gesture.end();}return false;}
   if(action==MotionEvent.ACTION_MOVE&&eligible&&e.getPointerId(0)==pointer){
    if(gesture.mode!=PlaybackGesture.Mode.NONE)return true;
    if(gesture.move(e.getX(),e.getY(),dp(12))!=PlaybackGesture.Mode.NONE){startGesture();return true;}
   }return false;
  }
  @Override public boolean performClick(){super.performClick();if(view!=null)view.showController();return true;}
  @Override public boolean onTouchEvent(MotionEvent e){
   if(!eligible)return true;
   if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN||e.getPointerId(0)!=pointer){eligible=false;endGesture(false);return true;}
   if(e.getActionMasked()==MotionEvent.ACTION_MOVE)moveGesture(e);
   else if(e.getActionMasked()==MotionEvent.ACTION_UP){if(gesture.mode==PlaybackGesture.Mode.NONE)performClick();else{moveGesture(e);endGesture(true);}eligible=false;}
   else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){endGesture(false);eligible=false;}
   return true;
  }
 }
 void captureFrame(){
  if(player==null||!rendered||capturing){Toast.makeText(this,"Chờ video hiện hình rồi chụp.",Toast.LENGTH_SHORT).show();return;}
  View video=view.getVideoSurfaceView();
  if(!(video instanceof SurfaceView)||video.getWidth()<1||video.getHeight()<1){Toast.makeText(this,"Chưa có khung hình để chụp.",Toast.LENGTH_SHORT).show();return;}
  capturing=true;capture.setEnabled(false);int ticket=generation;long position=player.getCurrentPosition();String name=session.items.get(index).optString("title","Video");
  double scale=Math.min(1d,1920d/Math.max(video.getWidth(),video.getHeight()));
  Bitmap bitmap;
  try{bitmap=Bitmap.createBitmap(Math.max(1,(int)(video.getWidth()*scale)),Math.max(1,(int)(video.getHeight()*scale)),Bitmap.Config.ARGB_8888);}
  catch(OutOfMemoryError e){capturing=false;capture.setEnabled(rendered);Toast.makeText(this,"Thiếu RAM để chụp khung hình.",Toast.LENGTH_LONG).show();return;}
  try{PixelCopy.request((SurfaceView)video,bitmap,result->{
   if(result!=PixelCopy.SUCCESS||ticket!=generation||isDestroyed()){
    bitmap.recycle();capturing=false;if(!isDestroyed()){capture.setEnabled(rendered);Toast.makeText(this,"Chưa chụp được khung hình. Thử lại khi video đã hiện.",Toast.LENGTH_SHORT).show();}return;
   }
   saver.execute(()->{
    String message;
    try{FrameCapture.save(getApplicationContext(),bitmap,name,position);message="Đã lưu ảnh vào Pictures/Stream Pocket.";}catch(Exception e){message="Không lưu được ảnh: "+e.getMessage();}finally{bitmap.recycle();}
    String notice=message;main.post(()->{capturing=false;if(!isDestroyed()){capture.setEnabled(rendered);Toast.makeText(this,notice,Toast.LENGTH_LONG).show();}});
   });
  },main);}catch(Exception e){bitmap.recycle();capturing=false;capture.setEnabled(rendered);Toast.makeText(this,"Chưa chụp được khung hình.",Toast.LENGTH_SHORT).show();}
 }
 static String clock(long millis){long s=Math.max(0,millis/1000);return s>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
 int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
 void updateRotationLabel(){boolean wide=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;rotate.setContentDescription(wide?"Xoay dọc":"Xoay ngang");rotate.setTooltipText(wide?"Xoay dọc":"Xoay ngang");}
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);endGesture(false);updateRotationLabel();}
 @Override protected void onStart(){super.onStart();visible=true;}
 @Override protected void onStop(){visible=false;endGesture(false);if(player!=null)player.pause();super.onStop();}
 @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("index",index);state.putLong("position",player==null?0:player.getCurrentPosition());}
 @Override protected void onDestroy(){
  generation++;endGesture(false);if(frames!=null)frames.close();if(player!=null){view.setPlayer(null);player.release();}
  io.shutdownNow();saver.shutdown();if(isFinishing()&&sessionId!=null)PlaybackSession.sessions.remove(sessionId);super.onDestroy();
 }
}
