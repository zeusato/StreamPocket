package vn.streampocket.mobile;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class FeatureLockTest {
 @Rule public TemporaryFolder folder=new TemporaryFolder();
 String contents(String name)throws Exception{return new String(Files.readAllBytes(new File(folder.getRoot(),name).toPath()),StandardCharsets.UTF_8);}
 FeatureLock lock(String name){return new FeatureLock(new File(folder.getRoot(),name));}
 @Test public void firstUseConfirmationAndNoPlaintextThenRestartLocks()throws Exception{
  FeatureLock gate=lock("gate");assertFalse(gate.hasPassword());assertFalse(gate.isUnlocked());
  try{gate.create("123456".toCharArray(),"654321".toCharArray());fail();}catch(IOException expected){}
  assertFalse(gate.hasPassword());
  gate.create("correct-pass".toCharArray(),"correct-pass".toCharArray());assertTrue(gate.isUnlocked());
  String disk=contents("gate");assertFalse(disk.contains("correct-pass"));
  FeatureLock restarted=lock("gate");assertTrue(restarted.hasPassword());assertFalse(restarted.isUnlocked());
  assertFalse(restarted.unlock("wrong".toCharArray()));assertFalse(restarted.isUnlocked());
  assertTrue(restarted.unlock("correct-pass".toCharArray()));restarted.require();restarted.lock();
  try{restarted.require();fail();}catch(IOException expected){}
  try{restarted.create("newpass".toCharArray(),"newpass".toCharArray());fail();}catch(IOException expected){}
 }
 @Test public void validationSaltingAndCooldown()throws Exception{
  FeatureLock a=lock("a"),b=lock("b");
  try{a.create("12345".toCharArray(),"12345".toCharArray());fail();}catch(IOException expected){}
  for(FeatureLock gate:new FeatureLock[]{a,b})gate.create("123456".toCharArray(),"123456".toCharArray());
  assertNotEquals(contents("a"),contents("b"));
  a.lock();for(int i=0;i<5;i++)assertFalse(a.unlock("wrong".toCharArray()));
  try{a.unlock("123456".toCharArray());fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("30"));}
  assertFalse(a.isUnlocked());
 }
 @Test public void corruptedRecordFailsClosedAndCannotBeRecreated()throws Exception{
  Files.write(new File(folder.getRoot(),"gate").toPath(),"broken".getBytes(StandardCharsets.UTF_8));
  FeatureLock a=lock("gate");assertTrue(a.hasPassword());
  try{a.unlock("123456".toCharArray());fail();}catch(IOException expected){}
  try{a.create("123456".toCharArray(),"123456".toCharArray());fail();}catch(IOException expected){}
  assertFalse(a.isUnlocked());
 }
 @Test public void bridgePolicyProtectsSourcesButKeepsLocalTools()throws Exception{
  for(String method:new String[]{"catalogue","episodes","list","image","play","token","external","enqueue","clear"})assertTrue(method,FeatureAccess.protectedRequest(method,new JSONObject()));
  for(String method:new String[]{"localList","localThumbnail","localPlay","pick","settings","downloadFeatures","jobs"})assertFalse(method,FeatureAccess.protectedRequest(method,new JSONObject()));
  for(String mode:new String[]{"compact","merge"})assertFalse(FeatureAccess.protectedRequest("enqueue",new JSONObject().put("mode",mode)));
  assertFalse(FeatureAccess.protectedRequest("clear",new JSONObject().put("toolsOnly",true)));
 }
}
