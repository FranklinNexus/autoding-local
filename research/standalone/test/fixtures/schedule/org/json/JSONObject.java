package org.json;
import java.util.*;
/** State snapshots stand in for Android JSON serialization; this does not test a JSON parser. */
public class JSONObject {
 static final Map<String,JSONObject> snapshots=new HashMap<>();
 final Map<String,Object> fields=new LinkedHashMap<>();
 public JSONObject(){}
 public JSONObject(String token){if("{}".equals(token))return;JSONObject s=snapshots.get(token);if(s==null)throw new IllegalArgumentException();for(String k:s.fields.keySet())fields.put(k,copy(s.fields.get(k)));}
 static Object copy(Object v){if(v instanceof JSONObject)return new JSONObject(v.toString());if(v instanceof JSONArray){JSONArray a=new JSONArray();for(Object x:((JSONArray)v).values)a.put(copy(x));return a;}return v;}
 public JSONObject put(String k,Object v){fields.put(k,v);return this;}
 public boolean has(String k){return fields.containsKey(k);}public boolean isNull(String k){return fields.get(k)==null;}
 public String getString(String k){if(!has(k)||isNull(k))throw new IllegalArgumentException();return String.valueOf(fields.get(k));}
 public String optString(String k){return has(k)&&!isNull(k)?String.valueOf(fields.get(k)):"";}
 public boolean optBoolean(String k){return Boolean.TRUE.equals(fields.get(k));}
 public int getInt(String k){return ((Number)fields.get(k)).intValue();}public long getLong(String k){return ((Number)fields.get(k)).longValue();}
 public int optInt(String k){return has(k)?getInt(k):0;}public long optLong(String k){return has(k)?getLong(k):0;}
 public JSONObject getJSONObject(String k){return (JSONObject)fields.get(k);}public JSONObject optJSONObject(String k){return fields.get(k) instanceof JSONObject?(JSONObject)fields.get(k):null;}
 public JSONArray getJSONArray(String k){return (JSONArray)fields.get(k);}public JSONArray optJSONArray(String k){return fields.get(k) instanceof JSONArray?(JSONArray)fields.get(k):null;}
 public String toString(){String token=UUID.randomUUID().toString();JSONObject s=new JSONObject();for(String k:fields.keySet())s.fields.put(k,copy(fields.get(k)));snapshots.put(token,s);return token;}
}
