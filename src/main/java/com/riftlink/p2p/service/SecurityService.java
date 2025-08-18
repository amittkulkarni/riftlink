package com.riftlink.p2p.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

/**
 * Manages TLS/SSL context for secure peer-to-peer communication.
 * It creates a self-signed certificate and a keystore if they don't exist.
 */
public class SecurityService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityService.class);
    private static final String KEYSTORE_PASSWORD = "changeit"; // A default password
    private static final String KEYSTORE_ALIAS = "riftlink";
    private static final Path KEYSTORE_PATH = Paths.get(System.getProperty("user.home"), ".riftlink", "riftlink.jks");

    private SSLContext sslContext;

    /**
     * Initializes the security service by loading or creating the SSL context.
     */
    public SecurityService() {
        try {
            Files.createDirectories(KEYSTORE_PATH.getParent());
            KeyStore keyStore = loadKeyStore();
            this.sslContext = createSslContext(keyStore);
            logger.info("SSL Context initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize SecurityService", e);
            throw new RuntimeException("Could not initialize SSL context", e);
        }
    }

    /**
     * Creates a secure server socket.
     * @param port The port to bind the socket to.
     * @return An initialized SSLServerSocket.
     * @throws IOException if an I/O error occurs.
     */
    public SSLServerSocket createServerSocket(int port) throws IOException {
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);
        serverSocket.setNeedClientAuth(false); // Clients don't need to present a certificate
        return serverSocket;
    }

    /**
     * Creates a secure client socket.
     * @param host The host to connect to.
     * @param port The port to connect to.
     * @return An initialized SSLSocket.
     * @throws IOException if an I/O error occurs.
     */
    public SSLSocket createSocket(String host, int port) throws IOException {
        SSLSocketFactory factory = sslContext.getSocketFactory();
        return (SSLSocket) factory.createSocket(host, port);
    }

    private SSLContext createSslContext(KeyStore keyStore) throws NoSuchAlgorithmException, KeyManagementException, UnrecoverableKeyException, KeyStoreException {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        return context;
    }

    private KeyStore loadKeyStore() throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        KeyStore keyStore = KeyStore.getInstance("JKS");

        if (Files.exists(KEYSTORE_PATH)) {
            logger.info("Loading existing keystore from: {}", KEYSTORE_PATH);
            try (FileInputStream fis = new FileInputStream(KEYSTORE_PATH.toFile())) {
                keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
            }
        } else {
            logger.info("Keystore not found. Creating a new self-signed certificate at: {}", KEYSTORE_PATH);
            keyStore.load(null, null); // Initialize empty keystore
            generateAndStoreCertificate(keyStore);
        }
        return keyStore;
    }

    private void generateAndStoreCertificate(KeyStore keyStore) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            // Use an external library or a more robust method for production certs
            // For this example, we create a simple self-signed certificate
            // This part can be complex; a simple placeholder is used.
            // A common way is to use BouncyCastle or Java's keytool command line.
            // Here's a simplified programmatic approach:
            Process process = new ProcessBuilder(
                "keytool", "-genkeypair", "-alias", KEYSTORE_ALIAS,
                "-keyalg", "RSA", "-keysize", "2048",
                "-storetype", "JKS", "-keystore", KEYSTORE_PATH.toString(),
                "-validity", "3650", "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=RiftLink P2P, OU=Development, O=RiftLink, L=Unknown, S=Unknown, C=Unknown"
            ).inheritIO().start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("keytool command failed with exit code " + exitCode);
            }
            logger.info("Successfully generated and stored new certificate.");

        } catch (Exception e) {
            throw new RuntimeException("Could not generate and store self-signed certificate", e);
        }
    }
}