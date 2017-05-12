package au.edu.unimelb.mentalpoker;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;

/** This class provides an interface for reliable message delivery. */
public class Remote extends Thread implements Connection.Callbacks {
    private static final int RETRY_LIMIT = 10;

    private final ServerSocket serverSocket;
    private final HashMap<Address, Connection> outgoingConnections;
    private final int port;
    private Callbacks remoteListener;
    private boolean listening;

    /** Interface for callbacks. */
    public interface Callbacks {
        void onReceive(Address source, Proto.NetworkMessage message);
    }

    public Remote(int port, Callbacks listener) {
        setListener(listener);
        this.listening = true;
        this.port = port;
        this.outgoingConnections = new HashMap<>();
        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void setListener(Callbacks listener) {
        this.remoteListener = listener;
    }

    /** Handles messages received on connections. Parses a NetworkMessage proto and calls onReceive callback. */
    @Override
    public synchronized void onReceive(Address source, byte[] message) {
        try {
            Proto.NetworkMessage networkMessage = Proto.NetworkMessage.parseFrom(message);
            //System.out.println("Receiving ="+networkMessage.getType().toString());
            this.remoteListener.onReceive(source, networkMessage);
        } catch (InvalidProtocolBufferException e) {
            System.out.println("Error: Invalid protobuf message");
        }
    }

    @Override
    public synchronized void onConnectionClosed(Address source) {
        this.outgoingConnections.remove(source);
    }

    /** Sends a message reliably to the given destination address */
    public void send(Address destination, Proto.NetworkMessage message) throws IOException {
        int retries = 0;
        byte[] messageBytes = message.toByteArray();

        if (!this.outgoingConnections.containsKey(destination)) {
            connect(destination,retries);
        }
       boolean result = this.outgoingConnections.get(destination).Write(messageBytes);
    }

    private void connect(Address to, int retries) {
        try {
            Socket remoteSocket = new Socket(to.ip, to.port);
            Connection connection = new Connection(remoteSocket, this.port, this);
            new Thread(connection).start();
            this.outgoingConnections.put(to, connection);
        } catch (IOException e) {
            if(retries<RETRY_LIMIT){
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                retries++;
                System.out.println("retries ="+retries);
                connect(to,retries);
            }
            else{
                System.out.println("Could not resolve Hostname");
                e.printStackTrace();
            }

        }
    }

    public void run() {
        while (this.listening) {
            if (this.serverSocket != null) {
                try {
                    Socket clientSocket = this.serverSocket.accept();
                    Connection clientConnection = new Connection(clientSocket, this.port, this);
                    new Thread(clientConnection).start();
                } catch (IOException e) {
                    System.out.println("listening socket closed");
//                    e.printStackTrace();
                }
            }
        }
        System.out.println("Got here...");
        closeAllConnections();
    }

    public void finish() {
        System.out.println("Finishing...");
        this.listening = false;
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void closeAllConnections() {
        ArrayList<Connection> connections = new ArrayList<>(this.outgoingConnections.values());
        for (Connection connection : connections) {
            connection.close();
        }
        this.outgoingConnections.clear();
    }
}
