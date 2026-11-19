package vn.streampocket.mobile;
import java.util.Locale;
/** Numeric name ordering keeps Episode 2 before Episode 10 without numeric overflow. */
final class MediaOrder {
 static int compare(String left,String right){
  String a=left.toLowerCase(Locale.ROOT),b=right.toLowerCase(Locale.ROOT);int i=0,j=0;
  while(i<a.length()&&j<b.length()){
   char x=a.charAt(i),y=b.charAt(j);
   if(x>='0'&&x<='9'&&y>='0'&&y<='9'){
    int ie=i,je=j;while(ie<a.length()&&Character.isDigit(a.charAt(ie)))ie++;while(je<b.length()&&Character.isDigit(b.charAt(je)))je++;
    int ia=i,jb=j;while(ia<ie-1&&a.charAt(ia)=='0')ia++;while(jb<je-1&&b.charAt(jb)=='0')jb++;
    int n=Integer.compare(ie-ia,je-jb);if(n!=0)return n;
    n=a.substring(ia,ie).compareTo(b.substring(jb,je));if(n!=0)return n;
    i=ie;j=je;
   }else{if(x!=y)return Character.compare(x,y);i++;j++;}
  }
  int n=Integer.compare(a.length()-i,b.length()-j);return n!=0?n:left.compareTo(right);
 }
}
