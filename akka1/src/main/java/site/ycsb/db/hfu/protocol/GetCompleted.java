package site.ycsb.db.hfu.protocol;

import com.google.api.client.util.Key;

import java.util.Map;

public class GetCompleted {
  @Key
  private String key;
  @Key
  private boolean found;
  @Key
  private Map<String, String> value;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public boolean isFound() {
    return found;
  }

  public void setFound(boolean found) {
    this.found = found;
  }

  public Map<String, String> getValue() {
    return value;
  }

  public void setValue(Map<String, String> value) {
    this.value = value;
  }
}
