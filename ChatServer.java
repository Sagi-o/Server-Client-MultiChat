import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * A multithreaded chat room server.  When a client connects the
 * server requests a screen name by sending the client the
 * text "SUBMITNAME", and keeps requesting a name until
 * a unique one is received.  After a client submits a unique
 * name, the server acknowledges with "NAMEACCEPTED".  Then
 * all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name.  The
 * broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple
 * chat server, there are a few features that have been left out.
 * Two are very useful and belong in production code:
 *
 *     1. The protocol should be enhanced so that the client can
 *        send clean logout messages to the server.
 *
 *     2. The server should do some logging.
 *
 * PROTOCOL:
 *      CLIENT COMMANDS:
 *          SENDTO ALL : Broadcast message
 *          SENDTO $USERNAME : If user connected, send. Else print error.
 *          GETUSERS : Print all connected users
 *          LOGOUT : Disconnects user from chat
 *
 *      SERVER COMMANDS:
 *          $USERNAME Connected: When user entered the chat
 *          $USERNAME Disconnected: When user exit the chat
 *          Server is Running: When the server started and running
 */

public class ChatServer {

    /**
     * The port that the server listens on.
     */
    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room.  Maintained
     * so that we can check that new clients are not registering name
     * already in use.
     */
    private static HashSet<String> names = new HashSet<>();

    private static HashMap<String, PrintWriter> users = new HashMap<>();

    /**
     * The set of all the print writers for all the clients.  This
     * set is kept so we can easily broadcast messages.
     */
    private static HashSet<PrintWriter> writers = new HashSet<>();

    /**
     * The appplication main method, which just listens on a port and
     * spawns handler threads.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");

        //users.put("itai", new PrintWriter());
    /*
        String user = "itai";
        if (users.get(user) != null) {
            // User exists!
            PrintWriter toUser = users.get(user); // Value of user -> PrintWriter -> Adrres to send msgs
            toUser.println("hi itai");
        } else {
            // User not exists or disconnected
        }
        */
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for a dealing with a single client
     * and broadcasting its messages.
     */
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {
                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(
                        socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (users) {
                        if (users.get(name) == null) { // Client is disconnected
                            users.put(name, out);
                            out.println("MESSAGE Welcome, " + name);
                            break;
                        } else {
                            out.println("ERROR " + name + " Is already connected, try another user name");
                        }
                    }
                }

                // Now that a successful name has been chosen, add the
                // socket's print writer to the set of all writers so
                // this client can receive broadcast messages.

                // Client
                out.println("NAMEACCEPTED");
                sendBroadcast("Client " + name + " logged in", true, true);

                // Server log
                System.out.println("Client " + name + " logged in");

                // Accept messages from this client and broadcast them.
                // Ignore other clients that cannot be broadcasted to.
                while (true) {
                    String input = in.readLine();

                    if (input == null) {
                        return;
                    }

                    else if (input.startsWith("SENDTO")) {
                        String substring = input.substring(7);

                        // Broadcast message
                        if (substring.startsWith("ALL")) {

                            sendBroadcast(substring.substring(4), false, false);

                            // Message to specific user
                        } else {
                            sendTo(substring);
                        }

                    } else if (input.startsWith("GETUSERS"))  {
                        printConnections();
                    } else if (input.startsWith("LOGOUT")) {
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                logout();
            }
        }

        private void sendBroadcast(String message ,boolean system, boolean logging) {
            for (Map.Entry<String, PrintWriter> user : users.entrySet()) {
                PrintWriter writer = user.getValue();

                if (!name.equals(user.getKey())) {
                    if (!system){
                        writer.println("MESSAGE " + name + ": " + message);
                    }
                    else writer.println("MESSAGE " + message);
                }
            }

            if (!logging) out.println("MESSAGE " + "me: " + message);
        }

        private void sendTo(String substring) {
            String toUser = "";
            int index = 0;

            for (int i = 0; i<substring.length() ; i++) {
                if (substring.charAt(i) != ' ') {
                    toUser += substring.charAt(i);
                } else {
                    index = i;
                    break;
                }
            }

            if (users.containsKey(toUser)) {
                PrintWriter writer = users.get(toUser);
                writer.println("MESSAGE " + name + ": " + substring.substring(index));
                out.println("MESSAGE " + "me" + ": " + substring.substring(index));
            } else out.println("ERROR Can't send message. " + toUser + " is disconnected");
        }

        private void printConnections() {
            String names = "";

            for (Map.Entry<String, PrintWriter> user : users.entrySet()) {
                names += user.getKey() + ", ";
            }
            out.println("GETUSERS  " + names.substring(0, names.length()-2));
        }

        private void logout() {
            // This client is going down!  Remove its name and its print
            // writer from the sets, and close its socket.
            if (name != null) {
                out.println("MESSAGE Goodbye");
                users.remove(name);
                sendBroadcast(name + " logged out", true, true);
                System.out.println("Client " + name + " logged out");
            }
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error, client " + name + " can't logout: " + e);
            }
        }
    }
}