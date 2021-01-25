package site.ycsb.db.hfu;

import de.hfu.keyvaluestore.protocol.generated.Clientapi;
import de.hfu.keyvaluestore.protocol.generated.KeyValueStoreServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BlockingGrpcKeyValueStoreClient implements AutoCloseable {

    private final String host;
    private final int port;
    private ManagedChannel channel;
    private KeyValueStoreServiceGrpc.KeyValueStoreServiceBlockingStub blockingStub;

    public BlockingGrpcKeyValueStoreClient(String hostAndPort) {
      String[] parts = hostAndPort.split(":");
      this.host = parts[0];
      this.port = Integer.parseInt(parts[1]);
    }

    public BlockingGrpcKeyValueStoreClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void open() {
        if (channel != null)
            throw new IllegalStateException("Client already opened.");
        channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext().build();
        blockingStub = KeyValueStoreServiceGrpc.newBlockingStub(channel);
    }

    public Clientapi.GetCompleted get(String key) {
        Clientapi.GetCommand command = Clientapi.GetCommand
                .newBuilder()
                .setKey(key)
                .build();
        return blockingStub.get(command);
    }

    public Clientapi.PutCompleted put(String key, Map<String, String> values, boolean persist) {
        Clientapi.PutCommand command = Clientapi.PutCommand
                .newBuilder()
                .setKey(key)
                .putAllValue(values)
                .setPersist(persist)
                .build();
        return blockingStub.put(command);
    }

    public Clientapi.ShardingState queryClusterState() {
        Clientapi.QueryShardingStateMessage command = Clientapi.QueryShardingStateMessage
                .newBuilder()
                .build();
        return blockingStub.queryShardingState(command);
    }

    @Override
    public void close() throws Exception {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
