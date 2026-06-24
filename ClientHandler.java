import java.io.*;
import java.net.*;

/**
 * ClientHandler.java — One instance per connected client, runs on its own thread.
 *
 * Lifecycle:
 *  1. Set up I/O streams on the client's socket.
 *  2. Perform username handshake (before registering with the server).
 *  3. Call server.addClient() — client is now visible to everyone.
 *  4. Loop: read a line → handle command or broadcast.
 *  5. On disconnect or /quit → call cleanup() → server.removeClient().
 *
 * This class deliberately contains NO synchronized blocks.
 * All concurrency protection lives in Server's methods. ClientHandler just
 * calls those methods; it doesn't touch shared state directly.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Server server;

    private PrintWriter  out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            // auto-flush=true: println() immediately flushes the buffer to the socket
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // --- Username handshake -------------------------------------------
            // Do this BEFORE registering with the server so other clients don't
            // see a join announcement for an unnamed user.
            out.println("=== Welcome to the Chat Server ===");
            out.println("Enter your username: ");

            username = in.readLine();
            if (username == null || username.isBlank()) {
                username = "Anonymous_" + (int)(Math.random() * 1000);
            }
            username = username.trim().replaceAll("\\s+", "_"); // no spaces in names

            // Register — this triggers the "[X has joined]" broadcast to others
            server.addClient(out, username);
            out.println("Welcome, " + username + "!");
            out.println("Commands: /list  — see who's online");
            out.println("          /quit  — leave the chat");
            out.println("          /msg <user> <text>  — private message");
            out.println("--------------------------------------------------");

            // --- Main message loop --------------------------------------------
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("/quit")) {
                    out.println("Goodbye, " + username + "!");
                    break; // exits loop → cleanup() runs in finally

                } else if (line.equalsIgnoreCase("/list")) {
                    out.println(server.getConnectedUsers());

                } else if (line.startsWith("/msg ")) {
                    handlePrivateMessage(line);

                } else if (line.startsWith("/")) {
                    out.println("Unknown command. Try /list, /quit, or /msg <user> <text>.");

                } else {
                    // Normal message — broadcast to everyone else
                    ServerLogger.log("[MSG] " + username + ": " + line);
                    server.broadcast(username + ": " + line, out);
                }
            }
            // readLine() returns null when the client closes their side of the connection

        } catch (IOException e) {
            // Abrupt disconnect (e.g. terminal closed, network drop)
            // This is normal and expected — not a server bug.
            ServerLogger.log("Connection lost for " +
                (username != null ? username : "unregistered client") +
                " — " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Handles "/msg <username> <text>" private messaging.
     * Demonstrates looking up a specific client under the shared lock (via server method).
     */
    private void handlePrivateMessage(String line) {
        // Format: /msg targetUser the rest of the message
        String[] parts = line.split("\\s+", 3); // ["/msg", "target", "text"]
        if (parts.length < 3) {
            out.println("Usage: /msg <username> <message>");
            return;
        }
        String targetName = parts[1];
        String text       = parts[2];

        boolean sent = server.sendPrivate(username, targetName, text);
        if (!sent) {
            out.println("User '" + targetName + "' not found or not online.");
        } else {
            out.println("[PM to " + targetName + "] " + text);
            ServerLogger.log("[PM] " + username + " → " + targetName + ": " + text);
        }
    }

    /**
     * Always runs, even if an exception was thrown.
     * Removes the client from the server's shared list and closes the socket.
     */
    private void cleanup() {
        server.removeClient(out); // safe even if addClient was never called
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            ServerLogger.log("Error closing socket for " + username + ": " + e.getMessage());
        }
    }
}
