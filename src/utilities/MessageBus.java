package utilities;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import json.BetACK;
import json.RegREQ;
import json.SurrenderACK;
import json.TurnACK;

public class MessageBus {

    // This class implements a message bus

    public final ConcurrentHashMap<InetAddress, CompletableFuture<BetACK>> betRequests = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<InetAddress, CompletableFuture<RegREQ>> registerRequests = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<InetAddress, CompletableFuture<SurrenderACK>> surrenderRequests = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<InetAddress, CompletableFuture<TurnACK>> turnRequests = new ConcurrentHashMap<>();
}
