# Multi-Threaded Chat Server in Java

A concurrent, terminal-based chat server built from scratch in Java using raw TCP sockets and the `java.util.concurrent` primitives. Supports real-time group messaging, private messages, live user discovery, and graceful shutdown — all without any external libraries.

---

## Features

- **Thread-per-client architecture** — each connection gets its own dedicated thread; the main thread never blocks on I/O
- **Thread-safe broadcast** — a single explicit lock (`synchronized(lock)`) guards all shared state; no race conditions, no `ConcurrentModificationException`
- **Private messaging** — `/msg <user> <text>` routes a message to exactly one recipient using a `HashMap<String, PrintWriter>` lookup under the same lock
- **Live user list** — `/list` returns all connected usernames, sorted alphabetically
- **Graceful shutdown** — a JVM shutdown hook broadcasts a goodbye message to all clients on `Ctrl+C` or `SIGTERM`
- **Stress-tested** — `StressTest.java` hammers the server with 20 simultaneous clients firing 5 messages each; the server handles all connections cleanly with zero errors
- **Timestamped logging** — every server event (connect, disconnect, message, error) routes through `ServerLogger` for consistent, readable output

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     Server.java                     │
│                                                     │
│  ServerSocket.accept() loop  →  spawns thread per  │
│  client connection                                  │
│                                                     │
│  Shared state (guarded by lock):                    │
│    List<PrintWriter>        clientWriters           │
│    Map<PrintWriter, String> clientNames             │
│    Map<String, PrintWriter> nameToWriter            │
│                                                     │
│  Thread-safe methods:                               │
│    addClient()   removeClient()   broadcast()       │
│    sendPrivate() getConnectedUsers()                │
└────────────────────┬────────────────────────────────┘
                     │ one thread per client
          ┌──────────▼──────────┐
          │  ClientHandler.java │  (implements Runnable)
          │                     │
          │  1. Username handshake                    
          │  2. server.addClient()                    
          │  3. readLine() loop                       
          │     → /quit  /list  /msg  /help           
          │     → broadcast for plain text            
          │  4. cleanup() → server.removeClient()     
          └─────────────────────┘

┌─────────────────────────────────────────────────────┐
│                   ChatClient.java                   │
│                                                     │
│  Thread 1 (main):   stdin  → server                │
│  Thread 2 (daemon): server → stdout                 │
└─────────────────────────────────────────────────────┘
```

### Why a single lock, not `ConcurrentHashMap`?

`clientWriters`, `clientNames`, and `nameToWriter` must stay in sync with each other across every add and remove operation. A `ConcurrentHashMap` protects individual map operations but not the three-collection invariant as a unit. A single explicit lock makes the atomicity boundary obvious and verifiable.

---

## Files

| File | Responsibility |
|---|---|
| `Server.java` | Entry point. Owns all shared state and every `synchronized` block. |
| `ClientHandler.java` | One instance per client. Handles the I/O loop and all commands. Zero `synchronized` blocks — delegates entirely to `Server`. |
| `ServerLogger.java` | Timestamped stdout logger. `PrintStream` is intrinsically thread-safe; no extra locking needed. |
| `ChatClient.java` | Terminal client. Two threads: one for sending, one for receiving. |
| `StressTest.java` | Concurrency validator. 20 clients × 5 messages in parallel; reports pass/fail. |

---

## Getting Started

### Prerequisites

- Java 11 or later
- `make` (optional — you can also run `javac`/`java` directly)

### Build and Run

**Using Make:**
```bash
# compile
make

# start the server (terminal 1)
make server

# connect as a client (terminal 2, 3, ...)
make client

# run the stress test (server must be running)
make stress

# clean compiled output
make clean
```

**Without Make:**
```bash
# compile
javac -d out src/*.java

# start server
java -cp out Server

# connect client
java -cp out ChatClient

# connect to a remote host
java -cp out ChatClient <host> <port>

# stress test
java -cp out StressTest
```

---

## Demo

Open three terminals:

**Terminal 1 — Server**
```
[2025-06-24 10:00:00] Chat server listening on port 12345
[2025-06-24 10:00:05] Incoming connection: 127.0.0.1
[2025-06-24 10:00:07] alice joined. Active clients: 1
[2025-06-24 10:00:10] Incoming connection: 127.0.0.1
[2025-06-24 10:00:12] bob joined. Active clients: 2
```

**Terminal 2 — Alice**
```
Connected! (type /quit to exit)
Welcome, alice!
[bob has joined the chat]
bob: hey alice!
/msg bob hey, this is a private message
[PM to bob] hey, this is a private message
/list
Connected (2): alice, bob
```

**Terminal 3 — Bob**
```
Connected! (type /quit to exit)
Welcome, bob!
alice: hello everyone
[PM from alice] hey, this is a private message
```

---

## Stress Test Results

```
Stress test: 20 clients × 5 messages each
----------------------------------------------
Done in 843ms
Successful clients : 20
Errored clients    : 0
PASS — no errors detected.
```

Remove the `synchronized(lock)` blocks from `Server.java` and re-run — you will see `ConcurrentModificationException` stack traces within seconds. That before/after difference is the entire point of the lock.

---

## Supported Commands

| Command | Description |
|---|---|
| `/list` | Show all currently online users |
| `/msg <user> <text>` | Send a private message to a specific user |
| `/help` | Print all available commands |
| `/quit` | Disconnect from the server |

---

## Concurrency Model — Interview Notes

This project was built specifically to demonstrate applied concurrency knowledge. Key talking points:

**The one lock, one invariant principle.** Three collections (`clientWriters`, `clientNames`, `nameToWriter`) are always mutated together. Wrapping all three in a single `synchronized(lock)` block makes the invariant — "all three maps are always consistent with each other" — self-evident in the code.

**Separation of concerns for thread safety.** `ClientHandler` has zero `synchronized` blocks. It only calls `Server`'s thread-safe methods. This keeps the concurrency surface area in one place: if you want to audit thread safety, you only read `Server.java`.

**Daemon threads.** The reader thread in `ChatClient` and all `ClientHandler` threads are daemons. They die automatically when the main thread exits — no manual thread lifecycle management needed.

**`PrintWriter` never throws.** `println()` silently sets an error flag instead of throwing on a broken socket. Dead connections are detected naturally in the `readLine()` loop and cleaned up via `removeClient()`. No defensive error-handling needed in `broadcast()`.

---

