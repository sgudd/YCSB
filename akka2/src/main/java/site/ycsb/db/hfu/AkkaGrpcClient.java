package site.ycsb.db.hfu;

import de.hfu.keyvaluestore.protocol.generated.Clientapi;
import site.ycsb.*;

import java.io.IOException;
import java.util.*;

public class AkkaGrpcClient extends DB {

  private static final String PROPERTY_HOSTS = "akka.hosts";
  private static final String PROPERTY_CLIENT_SIDE_HASHING = "akka.client-side-hashing";
  private static final String PROPERTY_NUMBER_OF_SHARDS = "akka.number-of-shards";

  private static int THREAD_COUNTER = 0;
  private final int threadId;

  private BlockingGrpcKeyValueStoreClient grpcClient;
  private Map<String, BlockingGrpcKeyValueStoreClient> clusterGrpcClients;
  private boolean clientSideHashingEnabled;
  private Map<String, String> shardToHostMapping;
  private int numberOfShards;

  private int allReads = 0;
  private int totalReads = 0;

  public AkkaGrpcClient() {
    // Object creation isn't done concurrently so there is no need for thread-safety here
    threadId = THREAD_COUNTER;
    THREAD_COUNTER = THREAD_COUNTER + 1;
  }

  @Override
  public void init() throws DBException {
    Properties properties = getProperties();
    String[] hosts = properties.getProperty(PROPERTY_HOSTS).split(",");
    String host = hosts[threadId % hosts.length];
    grpcClient = new BlockingGrpcKeyValueStoreClient(host);
    grpcClient.open();
    clusterGrpcClients = new HashMap<>();
    clusterGrpcClients.put(host, grpcClient);
    clientSideHashingEnabled = Boolean.parseBoolean(properties.getProperty(PROPERTY_CLIENT_SIDE_HASHING));
    if (clientSideHashingEnabled) {
      numberOfShards = Integer.parseInt(properties.getProperty(PROPERTY_NUMBER_OF_SHARDS, "100"));
      try {
        loadShardingState();
      } catch (IOException e) {
        throw new DBException(e);
      }
      if (threadId == 0) {
        System.out.printf("[%d] Client-side hashing is enabled with %d clients and %d/%d shards.%n",
            threadId, clusterGrpcClients.size(), shardToHostMapping.keySet().size(), numberOfShards);
      }
    } else {
      System.out.printf("[%d] Initialized for single host %s.%n", threadId, host);
    }
  }

  @Override
  public void cleanup() throws DBException {
    if (clientSideHashingEnabled) {
      for (String host : clusterGrpcClients.keySet()) {
        try {
          clusterGrpcClients.get(host).close();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    } else {
      try {
        grpcClient.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    System.out.printf("[%d] All fields read %d/%d times.%n", threadId, allReads, totalReads);
  }

  @Override
  public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    Status status = Status.OK;
    // TODO add support for fields
    Clientapi.GetCompleted response = getGrpcClientForKey(key).get(key);
    if (response.getFound()) {
      if (fields == null) {
        ++allReads;
        StringByteIterator.putAllAsByteIterators(result, response.getValueMap());
      } else {
        for (String field : fields) {
          result.put(field, new StringByteIterator(response.getValueMap().get(field)));
        }
      }
    } else {
      status = Status.NOT_FOUND;
    }
    ++totalReads;
    return status;
  }

  @Override
  public Status scan(String s, String s1, int i, Set<String> set, Vector<HashMap<String, ByteIterator>> vector) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    return insert(table, key, values);
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    getGrpcClientForKey(key)
        .put(key, StringByteIterator.getStringMap(values));
    return Status.OK;
  }

  @Override
  public Status delete(String s, String s1) {
    return Status.NOT_IMPLEMENTED;
  }

  private void loadShardingState() throws IOException {
    shardToHostMapping = new HashMap<>(numberOfShards);
    Clientapi.ShardingState state = grpcClient.queryClusterState();
    for (Clientapi.ShardingNode node : state.getNodesList()) {
      // In order for this to work all hosts must have their HTTP-listeners
      // bound to port (<akka-remote-port> + 10000)
      String host = node.getHost() + ":" + (node.getPort() + 10000);
      for (String shardId : node.getShardsList()) {
        shardToHostMapping.put(shardId, host);
      }
      if (!clusterGrpcClients.containsKey(host)) {
        BlockingGrpcKeyValueStoreClient nodeGrpcClient = new BlockingGrpcKeyValueStoreClient(host);
        nodeGrpcClient.open();
        clusterGrpcClients.put(host, nodeGrpcClient);
      }
    }
  }

  private BlockingGrpcKeyValueStoreClient getGrpcClientForKey(String key) {
    if (clientSideHashingEnabled) {
      String shardId = Integer.toString(Math.abs(key.hashCode() % numberOfShards));
      String host = shardToHostMapping.get(shardId);
      if (host != null) {
        return clusterGrpcClients.get(host);
      }
    }
    return grpcClient;
  }
}
