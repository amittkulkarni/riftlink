package com.riftlink.p2p.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.tomp2p.connection.Bindings;

public class NetworkUtils {
    private static final Logger logger = LoggerFactory.getLogger(NetworkUtils.class);

    public static InetAddress getBestNetworkInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress bestAddress = null;

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // Skip loopback, virtual, and inactive interfaces
                if (networkInterface.isLoopback() || 
                    networkInterface.isVirtual() || 
                    !networkInterface.isUp()) {
                    continue;
                }
                
                // Skip known problematic interface names
                String name = networkInterface.getName().toLowerCase();
                if (name.contains("vmware") || 
                    name.contains("virtualbox") || 
                    name.contains("hyper-v") ||
                    name.contains("vpn") ||
                    name.contains("tap") ||
                    name.contains("tun")) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    // Prefer IPv4 over IPv6, and non-link-local addresses
                    if (address instanceof Inet4Address && 
                        !address.isLoopbackAddress() &&
                        !address.isLinkLocalAddress()) {
                        
                        // Prefer public IPv4 over private IPv4
                        if (!address.isSiteLocalAddress()) {
                            logger.info("Selected public IPv4 interface: {} on {}", 
                                       address.getHostAddress(), networkInterface.getName());
                            return address;
                        } else if (bestAddress == null) {
                            bestAddress = address;
                            logger.info("Selected private IPv4 interface: {} on {}", 
                                       address.getHostAddress(), networkInterface.getName());
                        }
                    }
                }
            }
            
            // Return best found address or fallback to localhost
            return bestAddress != null ? bestAddress : InetAddress.getLocalHost();
            
        } catch (Exception e) {
            logger.warn("Could not determine best network interface, using localhost", e);
            try {
                return InetAddress.getLocalHost();
            } catch (UnknownHostException ex) {
                return InetAddress.getLoopbackAddress();
            }
        }
    }
    
    /**
     * Gets the name of the best network interface (e.g., "eth0", "wlan0")
     */
    public static String getBestNetworkInterfaceName() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                
                // Skip loopback, virtual, and inactive interfaces
                if (networkInterface.isLoopback() || 
                    networkInterface.isVirtual() || 
                    !networkInterface.isUp()) {
                    continue;
                }
                
                // Skip known problematic interface names
                String name = networkInterface.getName().toLowerCase();
                if (name.contains("vmware") || 
                    name.contains("virtualbox") || 
                    name.contains("hyper-v") ||
                    name.contains("vpn") ||
                    name.contains("tap") ||
                    name.contains("tun")) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    
                    if (address instanceof Inet4Address && 
                        !address.isLoopbackAddress() &&
                        !address.isLinkLocalAddress()) {
                        
                        logger.info("Selected network interface: {} with IP: {}", 
                                   networkInterface.getName(), address.getHostAddress());
                        return networkInterface.getName();
                    }
                }
            }
            
            logger.warn("No suitable network interface found");
            return null;
            
        } catch (SocketException e) {
            logger.warn("Failed to determine network interface name", e);
            return null;
        }
    }
    
    /**
     * Creates TomP2P Bindings configured for the best network interface
     */
    public static Bindings getBestBindings() {
        Bindings bindings = new Bindings();
        String interfaceName = getBestNetworkInterfaceName();
        
        if (interfaceName != null) {
            bindings.addInterface(interfaceName);  // Now uses interface name, not IP
            logger.info("Created TomP2P bindings for interface: {}", interfaceName);
        } else {
            logger.warn("No suitable network interface found, using default bindings");
        }
        
        return bindings;
    }
}
