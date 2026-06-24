import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Server.java — Main entry point.
 *
 * Responsibilities:
 *  - Open a ServerSocket and loop forever accepting new connections.
 *  - Spawn a ClientHandler thread for each connection.
 *  - Own the shared state: the list of connected client writers + their usernames.
 *  - Expose thread-safe methods (addClient, removeClient, broadcast, getConnectedUsers,
 *    sendPrivate) that ClientHandler threads call.
 *    ALL shared-state access is guarded by 'lock'.
 *
 * The concurrency model in one sentence:
 *   Multiple ClientHandler threads compete to call these methods simultaneously;
 *   'lock' ensures only one thread modifies clientWriters/clientNames at a time.
 */
public class Server {

    private static final int PORT = 12345;

    // -----------------------------------------------------------------------
    // Shared state — touched by every client thread simultaneously.
    // ALL access to these collections must go through synchronized(lock).
    // -----------------------------------------------------------------------
    private final Object lock = new Object();
    private final List<PrintWriter>        clientWriters = new ArrayList<>();
    private final Map<PrintWriter, String> clientNames   = new HashMap<>();
    private final Map<String, PrintWriter> nameToWriter  = new HashMap<>(); // for /msg

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        try {
            new Server().start();
        } catch (IOException e) {
            ServerLogger.log("Fatal: could not start server — " + e.getMessage());
        }
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true); // allows immediate restart without TIME_WAIT delay
        serverSocket.bind(new InetSocketAddress(PORT));

        // Graceful shutdown on Ctrl+C or SIGTERM
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ServerLogger.log("Server shutting down — notifying all clients...");
            broadcast("[Server is shutting down. Goodbye!]", null);
            try { serverSocket.close(); } catch (IOException ignored) {}
        }));

        ServerLogger.log("Chat server listening on port " + PORT);
        ServerLogger.log("Waiting for clients...");

        try {
            while (true) {
                // accept() blocks here until a client connects
                Socket clientSocket = serverSocket.accept();
                ServerLogger.log("Incoming connection: " + clientSocket.getInetAddress().getHostAddress());

                // Spawn a dedicated thread for this client, then immediately loop back
                // to accept the next one. Main thread never handles messages itself.
                Thread t = new Thread(new ClientHandler(clientSocket, this));
                t.setDaemon(true);
                t.start();
            }
        } finally {
            serverSocket.close();
        }
    }

    // -----------------------------------------------------------------------
    // Thread-safe shared-state operations.
    // These are the only methods that touch clientWriters / clientNames.
    // -----------------------------------------------------------------------

    /**
     * Register a newly connected client.
     * Called by ClientHandler AFTER the username handshake, so other clients never
     * see a join announcement for an unnamed user.
     */
    public void addClient(PrintWriter writer, String username) {
        synchronized (lock) {
            clientWriters.add(writer);
            clientNames.put(writer, username);
            nameToWriter.put(username, writer);
        }
        ServerLogger.log(username + " joined. Active clients: " + getClientCount());
        broadcast("[" + username + " has joined the chat]", null);
    }

    /**
     * Deregister a client that has disconnected or quit.
     * Safe to call even if addClient() was never reached (e.g. exception during
     * username handshake) — remove() on a missing key is a no-op.
     */
    public void removeClient(PrintWriter writer) {
        String username;
        synchronized (lock) {
            username = clientNames.remove(writer);
            clientWriters.remove(writer);
            if (username != null) nameToWriter.remove(username);
        }
        if (username != null) {
            ServerLogger.log(username + " left. Active clients: " + getClientCount());
            broadcast("[" + username + " has left the chat]", null);
        }
    }

    /**
     * Broadcast a message to every connected client except the sender.
     * Pass sender=null for server-generated messages (join/leave announcements).
     *
     * NOTE: PrintWriter.println() never throws — it silently sets an error flag.
     * Dead sockets are detected in each ClientHandler's readLine() loop, which
     * calls removeClient() on IOException. No error handling needed here.
     */
    public void broadcast(String message, PrintWriter sender) {
        synchronized (lock) {
            for (PrintWriter writer : clientWriters) {
                if (writer != sender) {
                    writer.println(message);
                }
            }
        }
    }

    /**
     * Send a private message from one user to another by name.
     * @return true if target was found and message was sent; false if not online.
     */
    public boolean sendPrivate(String fromUsername, String toUsername, String text) {
        PrintWriter targetWriter;
        synchronized (lock) {
            targetWriter = nameToWriter.get(toUsername);
        }
        if (targetWriter == null) return false;
        targetWriter.println("[PM from " + fromUsername + "] " + text);
        return true;
    }

    /**
     * Returns a formatted list of currently connected usernames.
     * Used by the /list command in ClientHandler.
     */
    public String getConnectedUsers() {
        synchronized (lock) {
            if (clientNames.isEmpty()) return "No users currently connected.";
            List<String> names = new ArrayList<>(clientNames.values());
            Collections.sort(names);
            return "Connected (" + names.size() + "): " + String.join(", ", names);
        }
    }

    private int getClientCount() {
        synchronized (lock) {
            return clientWriters.size();
        }
    }
}
