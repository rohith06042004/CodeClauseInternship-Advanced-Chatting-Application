import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*; // For thread pooling

/**
 * ChatServer class implements a multi-threaded server for a chat application.
 * It listens for incoming client connections, manages them, and broadcasts
 * messages received from one client to all other connected clients.
 */
public class ChatServer {

    private static final int PORT = 12345; // The port on which the server will listen.
    private static Set<ClientHandler> clientHandlers = Collections.synchronizedSet(new HashSet<>()); // Thread-safe set to store all connected client handlers.
    private static ExecutorService pool = Executors.newFixedThreadPool(10); // Thread pool for managing client connections.

    public static void main(String[] args) {
        System.out.println("Chat Server started on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                // Server listens indefinitely for new client connections.
                Socket clientSocket = serverSocket.accept(); // Blocks until a client connects.
                System.out.println("New client connected: " + clientSocket);

                // Create a new ClientHandler for the connected client and submit it to the thread pool.
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler); // Add the handler to our set of active clients.
                pool.execute(clientHandler); // Start the client handler in a new thread.
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            pool.shutdown(); // Shutdown the thread pool when the server stops.
        }
    }

    /**
     * Broadcasts a message from one client to all other connected clients.
     * @param message The message to be broadcasted.
     * @param sender The ClientHandler that sent the message (so it doesn't receive its own message).
     */
    public static void broadcastMessage(String message, ClientHandler sender) {
        // Iterate through all connected clients and send the message.
        for (ClientHandler handler : clientHandlers) {
            if (handler != sender) { // Don't send the message back to the sender.
                handler.sendMessage(message);
            }
        }
        System.out.println("Broadcasted: " + message);
    }

    /**
     * ClientHandler is an inner class that manages communication with a single client.
     * Each ClientHandler runs in its own thread.
     */
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out; // For sending messages to the client.
        private BufferedReader in; // For receiving messages from the client.
        private String clientName; // Name of the client.

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
            try {
                // Initialize input and output streams for the client socket.
                out = new PrintWriter(clientSocket.getOutputStream(), true); // true for auto-flush.
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                System.err.println("Error setting up streams for client: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                // First message from client is expected to be their name.
                clientName = in.readLine();
                if (clientName == null) {
                    // If client disconnects immediately, clientName might be null.
                    throw new IOException("Client disconnected before sending name.");
                }
                System.out.println(clientName + " has joined the chat.");
                // Broadcast that a new user has joined.
                broadcastMessage(clientName + " has joined the chat.", this);


                String message;
                // Read messages from the client until the client disconnects or an error occurs.
                while ((message = in.readLine()) != null) {
                    System.out.println(clientName + ": " + message);
                    // Broadcast the received message to all other clients.
                    broadcastMessage(clientName + ": " + message, this);
                }
            } catch (IOException e) {
                // This typically happens when the client disconnects.
                System.out.println(clientName + " has left the chat due to: " + e.getMessage());
                broadcastMessage(clientName + " has left the chat.", this);
            } finally {
                // Clean up resources when the client handler stops.
                try {
                    clientSocket.close();
                    in.close();
                    out.close();
                } catch (IOException e) {
                    System.err.println("Error closing client resources: " + e.getMessage());
                }
                clientHandlers.remove(this); // Remove this handler from the active clients set.
                System.out.println("Client disconnected: " + clientName);
            }
        }

        /**
         * Sends a message to this specific client.
         * @param message The message to send.
         */
        public void sendMessage(String message) {
            out.println(message);
        }
    }
}

