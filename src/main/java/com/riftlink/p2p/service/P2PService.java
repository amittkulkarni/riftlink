package com.riftlink.p2p.service;

import com.riftlink.p2p.util.Constants;
import net.tomp2p.dht.FutureGet;
import net.tomp2p.dht.FuturePut;
import net.tomp2p.dht.PeerBuilderDHT;
import net.tomp2p.dht.PeerDHT;
import net.tomp2p.futures.BaseFutureListener;
import net.tomp2p.futures.FutureBootstrap;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;
import net.tomp2p.storage.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.security.SecureRandom; 
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class P2PService {
    private static final Logger logger = LoggerFactory.getLogger(P2PService.class);

    private PeerDHT peer;
    private final int port;

    public P2PService(int port) {
        this.port = port;
    }

    public CompletableFuture<Void> start(InetAddress bootstrapAddress, int bootstrapPort) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Corrected PeerDHT initialization
                Peer master = new PeerBuilder(new Number160(new SecureRandom())).ports(port).start();
                peer = new PeerBuilderDHT(master).start();

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

    public CompletableFuture<Void> announceFile(String infohash) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Number160 contentKey = Number160.createHash(infohash);
        logger.info("Announcing content for infohash: {} (key: {})", infohash, contentKey);

        try {
            // Data object can throw IOException, so it needs to be handled.

            PeerAddress uploadPeerAddress = peer.peerAddress()
            .changeAddress(peer.peerAddress().inetAddress())
            .changePorts(Constants.UPLOAD_PORT, Constants.UPLOAD_PORT);

            Data data = new Data(uploadPeerAddress);

            peer.put(contentKey).data(data).start().addListener(new BaseFutureListener<FuturePut>() {
                @Override
                public void operationComplete(FuturePut futurePut) {
                    if (futurePut.isSuccess()) {
                        logger.info("Successfully announced infohash: {}", infohash);
                        future.complete(null);
                    } else {
                        logger.error("Failed to announce infohash: {}", futurePut.failedReason());
                        future.completeExceptionally(new RuntimeException("DHT put failed: " + futurePut.failedReason()));
                    }
                }

                @Override
                public void exceptionCaught(Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (IOException e) {
            logger.error("Could not create Data object for announcement", e);
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<Collection<PeerAddress>> findPeers(String infohash) {
        CompletableFuture<Collection<PeerAddress>> future = new CompletableFuture<>();
        Number160 contentKey = Number160.createHash(infohash);
        logger.info("Searching for peers with infohash: {} (key: {})", infohash, contentKey);

        FutureGet futureGet = peer.get(contentKey).all().start();

        // Corrected listener syntax
        futureGet.addListener(new BaseFutureListener<FutureGet>() {
            @Override
            public void operationComplete(FutureGet f) {
                if (f.isSuccess()) {
                    if (f.isEmpty()) {
                        logger.warn("No peers found for infohash: {}", infohash);
                        future.complete(Collections.emptyList());
                        return;
                    }

                    try {
                        // data.object() can throw ClassNotFoundException
                        Collection<PeerAddress> peers = f.dataMap().values().stream()
                                .map(data -> {
                                    try {
                                        return (PeerAddress) data.object();
                                    } catch (ClassNotFoundException | IOException e) {
                                        logger.error("Could not deserialize PeerAddress from DHT data", e);
                                        return null;
                                    }
                                })
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toList());

                        logger.info("Found {} peers for infohash {}", peers.size(), infohash);
                        future.complete(peers);
                    } catch (Exception e) {
                        logger.error("Error processing peer data", e);
                        future.completeExceptionally(e);
                    }
                } else {
                    logger.error("Failed to find peers for infohash {}: {}", infohash, f.failedReason());
                    future.completeExceptionally(new RuntimeException("DHT get failed: " + f.failedReason()));
                }
            }

            @Override
            public void exceptionCaught(Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public void shutdown() {
        if (peer != null) {
            logger.info("Shutting down P2P service...");
            peer.shutdown().awaitUninterruptibly();
            logger.info("P2P service shut down.");
        }
    }
}