package vn.streampocket.mobile;
/** One finger, one locked axis, one action until UP/CANCEL. Android-independent for tests. */
final class PlaybackGesture {
 enum Mode {NONE, SEEK, BRIGHTNESS, VOLUME}
 Mode mode=Mode.NONE;float x,y,width,height;boolean active;
 void begin(float x,float y,float width,float height){this.x=x;this.y=y;this.width=Math.max(1,width);this.height=Math.max(1,height);mode=Mode.NONE;active=true;}
 Mode move(float nx,float ny,float slop){
  if(!active||mode!=Mode.NONE)return mode;
  float dx=Math.abs(nx-x),dy=Math.abs(ny-y);
  if(Math.max(dx,dy)<slop)return mode;
  if(dx>dy*1.2f)mode=Mode.SEEK;
  else if(dy>dx*1.2f)mode=x<width/2?Mode.BRIGHTNESS:Mode.VOLUME;
  return mode;
 }
 long target(float nx,long start,long duration){
  if(duration<=0)return start;
  double span=Math.min(duration,90_000L);
  long target=Math.round((start+(nx-x)/width*span)/100d)*100;
  return Math.max(0,Math.min(duration,target));
 }
 float level(float ny,float start){return Math.max(0f,Math.min(1f,start+(y-ny)/height));}
 void end(){active=false;mode=Mode.NONE;}
}
