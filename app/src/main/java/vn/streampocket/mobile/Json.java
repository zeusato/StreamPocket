package vn.streampocket.mobile;
import org.json.*;
public final class Json {
 public static JSONObject obj(Object... values) {
  JSONObject result=new JSONObject();
  try {for(int i=0;i<values.length;i+=2) result.put(values[i].toString(),values[i+1]);}
  catch(JSONException e){throw new IllegalArgumentException(e);}
  return result;
 }
 public static java.util.List<String> keys(JSONObject value){java.util.List<String> keys=new java.util.ArrayList<>();value.keys().forEachRemaining(keys::add);return keys;}
 public static JSONArray array(Object... values){JSONArray result=new JSONArray();for(Object v:values)result.put(v);return result;}
 public static void put(JSONObject object,String key,Object value){try{object.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}}
}
