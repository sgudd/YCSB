package site.ycsb.db.hfu.protocol;

import com.google.api.client.util.Key;

import java.util.List;

public class ShardingState {
  @Key
  private List<ShardingNode> nodes;

  public List<ShardingNode> getNodes() {
    return nodes;
  }

  public void setNodes(List<ShardingNode> nodes) {
    this.nodes = nodes;
  }
}
