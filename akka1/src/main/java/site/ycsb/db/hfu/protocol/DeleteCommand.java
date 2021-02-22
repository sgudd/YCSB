package site.ycsb.db.hfu.protocol;

import com.google.api.client.util.Key;

public class DeleteCommand {
  @Key
  private String key;
  @Key
  private boolean persist;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public boolean isPersist() {
    return persist;
  }

  public void setPersist(boolean persist) {
    this.persist = persist;
  }
}
