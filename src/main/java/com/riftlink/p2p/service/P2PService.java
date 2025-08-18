package com.riftlink.p2p.service;

import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages all DHT network operations using TomP2P.
 * This includes bootstrapping, announcing content, and discovering peers.
 */
public class P2PService {
    private static final Logger logger = LoggerFactory.getLogger(P2PService.class);

    private PeerDHT peer;
    private final int port;

    /**
     * Prepares the P2P service with a specific port.
     * @param port The port for P2P communication.
     */
    public P2PService(int port) {
        this.port = port;
    }

    /**
     * Starts the peer and bootstraps it into the DHT network.
     * @param bootstrapAddress The address of a known bootstrap node.
     * @param bootstrapPort The port of the bootstrap node.
     * @return A CompletableFuture that completes when bootstrapping is finished.
     */
    public CompletableFuture<Void> start(InetAddress bootstrapAddress, int bootstrapPort) {
        return CompletableFuture.runAsync(() -> {
            try {
                // A random peer ID is generated
                peer = new PeerBuilder(new Number160(new SecureRandom())).ports(port).start();
                logger.info("Peer started with ID: {}. Listening on port: {}", peer.peerID(), port);

                if (bootstrapAddress == null) {
                    logger.warn("No bootstrap address provided. Starting as the first peer in a new network.");
                    return;
                }

                logger.info("Bootstrapping to peer at {}:{}", bootstrapAddress.getHostAddress(), bootstrapPort);
                FutureBootstrap fb = peer.peer().bootstrap().inetAddress(bootstrapAddress).ports(bootstrapPort).start();
                fb.awaitUninterruptibly();

                if (fb.isSuccess()) {
                    logger.info("Successfully bootstrapped to the network.");
                } else {
                    logger.error("Failed to bootstrap: {}", fb.failedReason());
                    throw new RuntimeException("Bootstrap failed: " + fb.failedReason());
                }
            } catch (IOException e) {
                logger.error("Error starting P2P service", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Announces a file's infohash to the DHT, associating it with this peer.
     * @param infohash The SHA-256 hash of the .rift metadata file.
     * @return A CompletableFuture that completes when the announcement is stored.
     */
    public CompletableFuture<Void> announceFile(String infohash) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Number160 contentKey = Number160.createHash(infohash);
        logger.info("Announcing content for infohash: {} (key: {})", infohash, contentKey);

        peer.put(contentKey).data(new Data(peer.peerAddress())).start().addListener(futurePut -> {
            if (futurePut.isSuccess()) {
                logger.info("Successfully announced infohash: {}", infohash);
                future.complete(null);
            } else {
                logger.error("Failed to announce infohash: {}", futurePut.failedReason());
                future.completeExceptionally(new RuntimeException("DHT put failed: " + futurePut.failedReason()));
            }
        });

        return future;
    }

    /**
     * Queries the DHT to find peers that have announced a specific infohash.
     * @param infohash The infohash to search for.
     * @return A CompletableFuture that resolves to a collection of PeerAddress objects.
     */
    public CompletableFuture<Collection<PeerAddress>> findPeers(String infohash) {
        CompletableFuture<Collection<PeerAddress>> future = new CompletableFuture<>();
        Number160 contentKey = Number160.createHash(infohash);
        logger.info("Searching for peers with infohash: {} (key: {})", infohash, contentKey);

        FutureGet futureGet = peer.get(contentKey).all().start();
        futureGet.addListener(f -> {
            if (f.isSuccess()) {
                if (f.isEmpty()) {
                    logger.warn("No peers found for infohash: {}", infohash);
                    future.complete(Collections.emptyList());
                    return;
                }

                Collection<PeerAddress> peers = f.dataMap().values().stream()
                        .map(data -> (PeerAddress) data.object())
                        .collect(Collectors.toList());

                logger.info("Found {} peers for infohash {}", peers.size(), infohash);
                future.complete(peers);
            } else {
                logger.error("Failed to find peers for infohash {}: {}", infohash, f.failedReason());
                future.completeExceptionally(new RuntimeException("DHT get failed: " + f.failedReason()));
            }
        });
        return future;
    }

    /**
     * Gracefully shuts down the peer, leaving the DHT network.
     */
    public void shutdown() {
        if (peer != null) {
            logger.info("Shutting down P2P service...");
            peer.shutdown().awaitUninterruptibly();
            logger.info("P2P service shut down.");
        }
    }
}