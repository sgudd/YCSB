package site.ycsb.db.hfu;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import site.ycsb.*;
import site.ycsb.db.hfu.protocol.*;

import java.io.IOException;
import java.util.*;

/**
 * REST-API binding for the experimental Akka KV-Store.
 * Uses the google-http-client library and its jackson2 serialization.
 *
 * TODO:
 * - Support of Delete- and Scan-queries
 * - Support for specific Get-requests using the fields-argument.
 *
 * The akka1.hosts property expects all cluster nodes in a comma (",") separated string.
 * 1. If client-side-hashing is disabled, each thread uses exactly one of the hosts for all
 *    its requests.
 * 2. If client-side-hashing is enabled, the cluster state is queried and if a given key can be
 *    associated with a cluster node that host will be used for the request. If a given key has
 *    not been found in the cluster state (because the corresponding shard has not been initialized
 *    at the time), a fixed host is used (as in 1.).
 */
public class AkkaRestApiClient extends DB {

  private static final String PROPERTY_HOSTS = "akka.hosts";
  private static final String PROPERTY_CLIENT_SIDE_HASHING = "akka.client-side-hashing";
  private static final String PROPERTY_NUMBER_OF_SHARDS = "akka.number-of-shards";
  private static final String PROPERTY_PERSIST = "akka.persist";

  private static int THREAD_COUNTER = 0;
  private final int threadId;

  private final HttpTransport httpTransport;
  private final HttpRequestFactory requestFactory;
  private final JsonFactory jsonFactory;

  private String[] hosts;
  private String host;
  private boolean clientSideHashingEnabled;
  private Map<String, String> shardToHostMapping;
  private int numberOfShards;
  private boolean persistEnabled;

  public AkkaRestApiClient() {
    // Object creation isn't done concurrently so there is no need for thread-safety here
    threadId = THREAD_COUNTER;
    THREAD_COUNTER = THREAD_COUNTER + 1;

    // Do some more initialization here to avoid overloading the init() process
    httpTransport = new ApacheHttpTransport();
    jsonFactory = new JacksonFactory();
    requestFactory = httpTransport.createRequestFactory(
        (HttpRequest request) -> {
          request.setParser(new JsonObjectParser(jsonFactory));
        }
    );
  }

  @Override
  public void init() throws DBException {
    Properties properties = getProperties();
    hosts = properties.getProperty(PROPERTY_HOSTS).split(",");
    host = hosts[threadId % hosts.length];
    clientSideHashingEnabled = Boolean.parseBoolean(properties.getProperty(PROPERTY_CLIENT_SIDE_HASHING));
    if (clientSideHashingEnabled) {
      numberOfShards = Integer.parseInt(properties.getProperty(PROPERTY_NUMBER_OF_SHARDS, "100"));
      try {
        loadShardingState();
      } catch (IOException e) {
        throw new DBException(e);
      }
      if (threadId == 0) {
        System.out.printf("[%d] Client-side hashing is enabled with %d hosts and %d/%d shards.%n",
            threadId, hosts.length, shardToHostMapping.keySet().size(), numberOfShards);
      }
    } else {
      System.out.printf("[%d] Initialized for single host %s.%n", threadId, host);
    }
    persistEnabled = Boolean.parseBoolean(properties.getProperty(PROPERTY_PERSIST, "true"));
  }

  @Override
  public void cleanup() throws DBException {
    try {
      httpTransport.shutdown();
    } catch (IOException e) {
      throw new DBException(e);
    }
  }

  @Override
  public Status read(String table, String key, Set<String> fields, Map<String, ByteIterator> result) {
    Status status = Status.OK;
    GetCommand command = new GetCommand();
    command.setKey(key);
    // TODO add support for fields
    try {
      HttpResponse httpResponse = requestFactory.buildPostRequest(
            new AkkaRestApiUrl(getHostForKey(key), AkkaRestApiUrl.GET),
            new JsonHttpContent(jsonFactory, command))
          .execute();
      try {
        GetCompleted response = httpResponse.parseAs(GetCompleted.class);
        if (response.isFound()) {
          if (fields == null) {
            StringByteIterator.putAllAsByteIterators(result, response.getValue());
          } else {
            for (String field : fields) {
              result.put(field, new StringByteIterator(response.getValue().get(field)));
            }
          }
        } else {
          status = Status.NOT_FOUND;
        }
      } finally {
        httpResponse.disconnect();
      }
    } catch (IOException e) {
      e.printStackTrace();
      status = Status.ERROR;
    }
    return status;
  }

  @Override
  public Status scan(String table, String startKey, int recordCount, Set<String> fields,
                     Vector<HashMap<String, ByteIterator>> result) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {
    return insert(table, key, values);
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    Status status = Status.OK;
    PutCommand command = new PutCommand();
    command.setKey(key);
    command.setValue(StringByteIterator.getStringMap(values));
    command.setPersist(persistEnabled);
    try {
      requestFactory.buildPostRequest(
          new AkkaRestApiUrl(getHostForKey(key), AkkaRestApiUrl.PUT),
          new JsonHttpContent(jsonFactory, command))
          .execute()
          .disconnect();
    } catch (IOException e) {
      e.printStackTrace();
      status = Status.ERROR;
    }
    return status;
  }

  @Override
  public Status delete(String table, String key) {
    Status status = Status.OK;
    DeleteCommand command = new DeleteCommand();
    command.setKey(key);
    command.setPersist(persistEnabled);
    try {
      requestFactory.buildPostRequest(
          new AkkaRestApiUrl(getHostForKey(key), AkkaRestApiUrl.DELETE),
          new JsonHttpContent(jsonFactory, command))
          .execute()
          .disconnect();
    } catch (IOException e) {
      e.printStackTrace();
      status = Status.ERROR;
    }
    return status;
  }

  private void loadShardingState() throws IOException {
    shardToHostMapping = new HashMap<>(numberOfShards);
    ShardingState state = requestFactory
        .buildGetRequest(new AkkaRestApiUrl(hosts[0], AkkaRestApiUrl.QUERY_SHARD_STATE))
        .execute()
        .parseAs(ShardingState.class);
    for (ShardingNode node : state.getNodes()) {
      // In order for this to work all hosts must have their HTTP-listeners
      // bound to port (<akka-remote-port> + 10000)
      String host = node.getHost() + ":" + (node.getPort() + 10000);
      for (String shardId : node.getShards()) {
        shardToHostMapping.put(shardId, host);
      }
    }
  }

  private String getHostForKey(String key) {
    if (clientSideHashingEnabled) {
      String shardId = Integer.toString(Math.abs(key.hashCode() % numberOfShards));
      return shardToHostMapping.getOrDefault(shardId, host);
    } else {
      return host;
    }
  }
}
