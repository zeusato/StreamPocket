package vn.streampocket.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaybackGestureTest {
 @Test public void tapsAndDiagonalJitterDoNotSelectAnAction(){
  PlaybackGesture g=new PlaybackGesture();g.begin(100,100,400,800);
  assertEquals(PlaybackGesture.Mode.NONE,g.move(105,107,12));
  assertEquals(PlaybackGesture.Mode.NONE,g.move(120,120,12));
 }
 @Test public void horizontalLockDoesNotBecomeVolumeOrBrightness(){
  PlaybackGesture g=new PlaybackGesture();g.begin(300,100,400,800);
  assertEquals(PlaybackGesture.Mode.SEEK,g.move(320,101,12));
  assertEquals(PlaybackGesture.Mode.SEEK,g.move(10,500,12));
 }
 @Test public void verticalSideIsChosenFromDownLocationAndRemainsLocked(){
  PlaybackGesture g=new PlaybackGesture();g.begin(100,400,400,800);
  assertEquals(PlaybackGesture.Mode.BRIGHTNESS,g.move(101,380,12));
  assertEquals(PlaybackGesture.Mode.BRIGHTNESS,g.move(390,300,12));
  g.end();g.begin(300,400,400,800);
  assertEquals(PlaybackGesture.Mode.VOLUME,g.move(300,420,12));
  assertEquals(PlaybackGesture.Mode.VOLUME,g.move(5,421,12));
 }
 @Test public void cancellationReleasesLockAndIgnoresFurtherMoves(){
  PlaybackGesture g=new PlaybackGesture();g.begin(100,100,400,800);g.move(150,100,12);g.end();
  assertEquals(PlaybackGesture.Mode.NONE,g.move(300,100,12));assertFalse(g.active);
  g.begin(10,10,400,800);assertEquals(PlaybackGesture.Mode.BRIGHTNESS,g.move(10,40,12));
 }
 @Test public void preciseSeekUsesRelativeStartAndClampsAtMediaEdges(){
  PlaybackGesture g=new PlaybackGesture();g.begin(200,100,400,800);
  assertEquals(75_000,g.target(400,30_000,600_000));
  assertEquals(0,g.target(0,30_000,600_000));
  assertEquals(600_000,g.target(400,590_000,600_000));
  assertEquals(15_000,g.target(300,10_000,20_000));
  assertEquals(30_000,g.target(400,30_000,-1));
  assertEquals(30_200,g.target(201,30_000,600_000));
 }
 @Test public void verticalLevelsAreRelativeBoundedAndIndependentOfCrossingScreen(){
  PlaybackGesture g=new PlaybackGesture();g.begin(300,400,400,800);
  assertEquals(.75f,g.level(200,.5f),.001);
  assertEquals(0,g.level(900,.5f),.001);
  assertEquals(1,g.level(-1000,.5f),.001);
 }
}
