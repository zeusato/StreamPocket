package vn.streampocket.mobile;
import org.json.JSONObject;
import java.util.Set;
final class FeatureAccess {
 static boolean tool(String mode){return "compact".equals(mode)||"merge".equals(mode);}
 static boolean protectedRequest(String method,JSONObject body){
  return Set.of("catalogue","episodes","list","image","play","token","external").contains(method)
   ||("enqueue".equals(method)&&!tool(body.optString("mode")))
   ||("clear".equals(method)&&!body.optBoolean("toolsOnly"));
 }
}
