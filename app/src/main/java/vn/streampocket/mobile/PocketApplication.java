package vn.streampocket.mobile;
import android.app.Application;
public class PocketApplication extends Application {
 @Override public void onCreate(){
  super.onCreate();Thread.UncaughtExceptionHandler original=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{Diagnostics.record(this,error);if(original!=null)original.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}});
 }
}
