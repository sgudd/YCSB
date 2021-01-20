package site.ycsb.db.hfu.protocol;

import com.google.api.client.util.Key;

import java.util.Map;

public class PutCommand {
  @Key
  private String key;
  @Key
  private Map<String, String> value;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Map<String, String> getValue() {
    return value;
  }

  public void setValue(Map<String, String> value) {
    this.value = value;
  }
}
