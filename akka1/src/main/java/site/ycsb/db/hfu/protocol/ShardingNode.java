package site.ycsb.db.hfu.protocol;

import com.google.api.client.util.Key;

import java.util.List;

public class ShardingNode {
  @Key
  private String name;
  @Key
  private String host;
  @Key
  private int port;
  @Key
  List<String> shards;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public List<String> getShards() {
    return shards;
  }

  public void setShards(List<String> shards) {
    this.shards = shards;
  }
}
