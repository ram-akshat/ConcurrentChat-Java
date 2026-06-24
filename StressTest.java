import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * StressTest.java — Intentional stress test for the chat server.
 *
 * What it does:
 *   Spins up NUM_CLIENTS simultaneous connections, each connecting, sending
 *   MESSAGES_PER_CLIENT messages rapidly, then disconnecting. All clients run
 *   in parallel via a thread pool.
 *
 * How to use it:
 *   1. Start the server:          java Server
 *   2. Run the stress test:       java StressTest
 *
 * What to look for:
 *   - WITHOUT the synchronized lock in Server.java: you should see
 *     ConcurrentModificationException stack traces in the server's output,
 *     or some clients getting dropped silently.
 *   - WITH the lock: the server handles all connections cleanly with no errors.
 *
 * The before/after contrast is your best interview talking point — it proves
 * you understand WHY the lock is needed, not just that it exists.
 */
public class StressTest {

    private static final String HOST            = "localhost";
    private static final int    PORT            = 12345;
    private static final int    NUM_CLIENTS     = 20;   // simultaneous connections
    private static final int    MESSAGES_PER_CLIENT = 5;

    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger errorCount   = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Stress test: " + NUM_CLIENTS + " clients × " +
            MESSAGES_PER_CLIENT + " messages each");
        System.out.println("Make sure the server is running on port " + PORT);
        System.out.println("----------------------------------------------");

        ExecutorService pool = Executors.newFixedThreadPool(NUM_CLIENTS);
        CountDownLatch  latch = new CountDownLatch(NUM_CLIENTS);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < NUM_CLIENTS; i++) {
            final int clientId = i;
            pool.submit(() -> {
                runClient("stress_" + clientId);
                latch.countDown();
            });
        }

        latch.await(); // wait for all clients to finish
        pool.shutdown();

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("----------------------------------------------");
        System.out.println("Done in " + elapsed + "ms");
        System.out.println("Successful clients : " + successCount.get());
        System.out.println("Errored clients    : " + errorCount.get());

        if (errorCount.get() == 0) {
            System.out.println("PASS — no errors detected.");
        } else {
            System.out.println("FAIL — check server output for exceptions.");
        }
    }

    private static void runClient(String username) {
        try (Socket socket = new Socket(HOST, PORT)) {
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true);

            // Drain the server's welcome prompt
            // (server sends "Enter your username: " before reading)
            Thread.sleep(100);
            out.println(username);   // send username
            Thread.sleep(50);

            // Send messages rapidly
            for (int i = 0; i < MESSAGES_PER_CLIENT; i++) {
                out.println("Message " + i + " from " + username);
                Thread.sleep(10);
            }

            out.println("/quit");
            Thread.sleep(100); // give server time to process
            successCount.incrementAndGet();

        } catch (Exception e) {
            errorCount.incrementAndGet();
            System.err.println("Client " + username + " failed: " + e.getMessage());
        }
    }
}
