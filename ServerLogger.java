import java.time.*;
import java.time.format.*;

/**
 * ServerLogger.java — Simple timestamped console logger.
 *
 * All server-side events (connects, disconnects, messages, errors) route
 * through here so the log format is consistent and easy to change in one place.
 *
 * Thread-safety: System.out.println() is itself synchronized on the PrintStream
 * object, so concurrent calls from multiple ClientHandler threads won't interleave
 * partial lines. No additional locking needed here.
 */
public class ServerLogger {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Print a log line to stdout with a timestamp prefix.
     * Example output:  [2024-11-15 14:32:07] alice joined. Active clients: 3
     */
    public static void log(String message) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        System.out.println("[" + timestamp + "] " + message);
    }
}
