package vn.streampocket.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class PackagingTest {
 @Test public void ffmpegRuntimeExceptionFormatterIsPresent()throws Exception{
  Class<?> formatter=Class.forName("com.arthenica.smartexception.java.Exceptions");
  assertNotNull(formatter.getMethod("getStackTraceString",Throwable.class).invoke(null,new Exception("fixture")));
 }
 @Test public void thumbnailsPassButRemoteAndActiveDataResourcesStayBlocked(){
  assertTrue(LocalResources.isThumbnail("data:image/jpeg;base64,/9j/test"));
  for(String value:new String[]{"https://pic.drleao.cn/a.jpg","data:text/html,test","data:image/svg+xml,test","file:///etc/hosts","data:image/jpeg;base64,"+"a".repeat(350_000)})
   assertFalse(LocalResources.isThumbnail(value));
 }
}
