package com.riftlink.p2p.model;

/**
 * A simple data record to hold a peer's connection information.
 *
 * @param host The IP address or hostname of the peer.
 * @param port The port number the peer is listening on.
 */
public record PeerInfo(String host, int port) {
}