# RiftLink P2P
  

RiftLink is a decentralized, peer-to-peer (P2P) file-sharing application built with Java. It allows users to share files, search for content on the network, and download files directly from other peers without relying on a central server. The application features a clean, intuitive user interface built with JavaFX.

## Features

  * **Decentralized Network**: Built on the TomP2P library, creating a robust and serverless P2P network.
  * **Share Files**: Easily add files to your shared folder to make them available to other peers on the network.
  * **Search for Content**: Find files shared by other users by searching for them with an infohash.
  * **Download Manager**: A simple interface to manage and monitor your active downloads.
  * **Self-Contained**: The application is packaged as a single, executable JAR file with all dependencies included.

## Technologies Used

RiftLink is built with the following key technologies:

  * **Java 17**: The core programming language for the application.
  * **JavaFX**: Used for creating the graphical user interface.
  * **TomP2P**: A powerful library for implementing the underlying peer-to-peer network and Distributed Hash Table (DHT).
  * **Maven**: For project management and building the application.
  * **SLF4J & Logback**: For logging and diagnostics.

## Getting Started

Follow these instructions to get a local copy of the project up and running.

### Prerequisites

  * **Java Development Kit (JDK) 17 or newer**
  * **Apache Maven**

### Building the Application

1.  **Clone the repository:**

    ```sh
    git clone https://github.com/amittkulkarni/riftlink.git
    cd riftlink
    ```

2.  **Build with Maven:**
    This command will compile the source code, run tests, and package the application into a single executable "fat JAR" in the `target/` directory.

    ```sh
    mvn clean package
    ```

### Running RiftLink

After building, you can run the application from the command line.

1.  **Start the First Peer (Bootstrap Node):**
    Open a terminal and run the following command. This will start a new P2P network.

    ```sh
    java -jar target/p2p-1.0.0.jar
    ```

2.  **Start a Second Peer and Connect:**
    Open a *second* terminal and run the same command, but this time, provide the IP address of the first peer as a command-line argument.

    ```sh
    # Replace 127.0.0.1:4000 with the IP of the first peer
    java -jar target/p2p-1.0.0.jar 127.0.0.1:4001
    ```

## How to Use

1.  **Sharing Files**:

      * When you first run RiftLink, it will create a `.riftlink/shared` directory in your user's home folder.
      * To share a file, simply place it inside this `shared` folder.
      * Click the "Share Files" button in the application to generate and display the infohashes for your shared files.

2.  **Searching and Downloading**:

      * To find a file, you need its infohash.
      * Paste the infohash into the search bar and click "Search".
      * If peers are found with that file, it will appear in the search results.
      * Click the "Download" button to start downloading the file. You can monitor its progress in the "Downloads" tab.

## License

This project is licensed under Eclipse Public License 2.0 - see the [LICENSE](https://github.com/amittkulkarni/riftlink/blob/main/LICENSE) file for details.