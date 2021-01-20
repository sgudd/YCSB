package site.ycsb.db.hfu;

import com.google.api.client.http.GenericUrl;

public class AkkaRestApiUrl extends GenericUrl {

  public static String QUERY_SHARD_STATE = "shard-state";
  public static String GET = "get";
  public static String PUT = "put";

  public AkkaRestApiUrl(String host, String apiMethod) {
    super("http://" + host + "/rest-service/" + apiMethod);
  }

}
