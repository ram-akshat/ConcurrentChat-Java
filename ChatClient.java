import java.io.*;
import java.net.*;

/**
 * ChatClient.java — Terminal client for the chat server.
 *
 * Architecture:
 *  - Main thread: reads lines from stdin (the user's keyboard) and sends them
 *    to the server. Blocks on userInput.readLine().
 *  - Reader thread: reads lines from the server and prints them to stdout.
 *    Runs concurrently so incoming messages appear immediately, even while the
 *    user is in the middle of typing.
 *
 * Usage:
 *   javac ChatClient.java && java ChatClient
 *   java ChatClient <host>            (default: localhost)
 *   java ChatClient <host> <port>     (default port: 12345)
 */
public class ChatClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 12345;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int    port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        System.out.println("Connecting to " + host + ":" + port + " ...");

        try (Socket socket = new Socket(host, port)) {
            System.out.println("Connected! (type /quit to exit)");

            BufferedReader serverIn  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    serverOut = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

            // ---------------------------------------------------------------
            // Reader thread — prints every line that arrives from the server.
            // Runs as a daemon so it dies automatically when the main thread exits.
            // ---------------------------------------------------------------
            Thread readerThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = serverIn.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    // Server closed the connection
                }
                System.out.println("-- Server connection closed --");
            });
            readerThread.setDaemon(true);
            readerThread.start();

            // ---------------------------------------------------------------
            // Main thread — sends everything the user types to the server.
            // ---------------------------------------------------------------
            String line;
            while ((line = userInput.readLine()) != null) {
                serverOut.println(line);
                if (line.trim().equalsIgnoreCase("/quit")) {
                    break;
                }
            }

        } catch (ConnectException e) {
            System.err.println("Could not connect to " + host + ":" + port +
                " — is the server running?");
        } catch (IOException e) {
            System.err.println("Connection error: " + e.getMessage());
        }

        System.out.println("You have left the chat. Goodbye!");
    }
}
