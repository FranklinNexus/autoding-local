package android.content;
import java.util.*;
public class Intent {
 public String action,data; final Map<String,Object> extras=new HashMap<>();
 public Intent(Context c,Class<?> cls){}
 public Intent setAction(String v){action=v;return this;}
 public Intent setData(android.net.Uri v){data=v.value;return this;}
 public Intent putExtra(String k,String v){extras.put(k,v);return this;}
 public Intent putExtra(String k,long v){extras.put(k,v);return this;}
 public String getStringExtra(String k){return (String)extras.get(k);}
 public long getLongExtra(String k,long fallback){return extras.containsKey(k)?((Number)extras.get(k)).longValue():fallback;}
}
