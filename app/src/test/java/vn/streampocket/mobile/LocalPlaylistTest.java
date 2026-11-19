package vn.streampocket.mobile;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
public class LocalPlaylistTest {
 @Test public void episodesSortNumericallyAndHugeNumbersDoNotOverflow(){
  List<String> names=new ArrayList<>(List.of("EP 10.mp4","EP 2.mp4","EP 1.mp4","EP 99999999999999999999999.mp4"));
  names.sort(MediaOrder::compare);assertEquals(List.of("EP 1.mp4","EP 2.mp4","EP 10.mp4","EP 99999999999999999999999.mp4"),names);
 }
 @Test public void filteringAndPlaybackUseSameStableOrder(){
  List<JSONObject> items=List.of(Json.obj("id","10","title","Film 10","date",5),Json.obj("id","2","title","Film 2","date",8),Json.obj("id","1","title","Other","date",9));
  List<JSONObject> filtered=LocalLibrary.select(items,Json.obj("query","FILM","sort","name"));
  assertEquals("2",filtered.get(0).optString("id"));assertEquals("10",filtered.get(1).optString("id"));
  assertEquals(1,PlaybackSession.index(filtered,"10"));assertEquals(-1,PlaybackSession.index(filtered,"1"));
  assertEquals("1",LocalLibrary.select(items,Json.obj("sort","newest")).get(0).optString("id"));
  assertEquals("10",items.get(0).optString("id"));
 }
 @Test public void defaultOrderIsNewestFirstWithStableTiesAndUnknownDatesLast(){
  List<JSONObject> items=List.of(Json.obj("id","old","title","A","date",1),Json.obj("id","unknown","title","B"),Json.obj("id","ten","title","EP 10","date",9),Json.obj("id","two","title","EP 2","date",9));
  List<JSONObject> sorted=LocalLibrary.select(items,new JSONObject());
  assertEquals(List.of("two","ten","old","unknown"),sorted.stream().map(v->v.optString("id")).collect(java.util.stream.Collectors.toList()));
 }
 @Test public void onlinePlaylistCarriesEpisodeOrderAndAuthWithoutAcceptingArbitraryUrls()throws Exception{
  JSONObject request=Json.obj("token","test","playlist",Json.array(Json.obj("id","hg-11547-2","site","fast"),Json.obj("id","hg-11547-1")));
  List<JSONObject> result=PlaybackSession.online(request);
  assertEquals("hg-11547-2",result.get(0).getString("id"));assertEquals("fast",result.get(0).getString("site"));assertEquals("test",result.get(1).getString("token"));
  try{PlaybackSession.online(Json.obj("id","file:///bad"));fail();}catch(IllegalArgumentException expected){}
 }
}
