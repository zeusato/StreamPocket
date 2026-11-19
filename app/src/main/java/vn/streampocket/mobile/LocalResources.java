package vn.streampocket.mobile;
/** Only locally generated, bounded raster thumbnails bypass the network block. */
final class LocalResources {
 static boolean isThumbnail(String url){
  return url!=null&&url.length()<=350_000&&url.startsWith("data:image/jpeg;base64,");
 }
}
