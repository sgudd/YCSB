package site.ycsb.db.hfu.protocol;

import com.google.api.client.util.Key;

public class Ok {
  @Key
  private String key;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}
