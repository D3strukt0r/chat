package com.orbitrondev.Controller;

import com.orbitrondev.Exception.InvalidIpException;
import com.orbitrondev.Exception.InvalidPortException;
import com.orbitrondev.Model.UserModel;
import com.sun.net.ssl.internal.ssl.Provider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Backend utility class. Acts as an interface between the program and the server.
 *
 * @author Manuele Vaccari
 * @version %I%, %G%
 * @since 0.0.1
 */
public class BackendController implements Closeable {

    private static final Logger logger = LogManager.getLogger(BackendController.class);

    private Socket socket;

    private BufferedReader socketIn;
    private OutputStreamWriter socketOut;

    private ArrayList<String> lastMessage = new ArrayList<>();

    /**
     * Creates a Socket (insecure) to the backend.
     *
     * @param ipAddress A String containing the ip address to reach the server.
     * @param port      An integer containing the port which the server uses.
     *
     * @throws InvalidIpException   Is thrown when the IP address is not a valid IP address.
     * @throws InvalidPortException Is thrown when the port is not in the acceptable range.
     * @since 0.0.1
     */
    public BackendController(String ipAddress, int port) throws InvalidIpException, InvalidPortException {
        if (!isValidIpAddress(ipAddress)) {
            throw new InvalidIpException();
        }
        if (!isValidPortNumber(port)) {
            throw new InvalidPortException();
        }

        try {
            createStandardSocket(ipAddress, port);

            socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socketOut = new OutputStreamWriter(socket.getOutputStream());

            createResponseThread();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Creates a Socket (insecure or secure) to the backend.
     *
     * @param ipAddress A String containing the ip address to reach the server.
     * @param port      An integer containing the port which the server uses.
     * @param secure    A boolean defining whether to use SSL or not.
     *
     * @throws InvalidIpException   Is thrown when the IP address is not a valid IP address.
     * @throws InvalidPortException Is thrown when the port is not in the acceptable range.
     * @since 0.0.1
     */
    public BackendController(String ipAddress, int port, boolean secure) throws InvalidIpException, InvalidPortException {
        if (!isValidIpAddress(ipAddress)) {
            throw new InvalidIpException();
        }
        if (!isValidPortNumber(port)) {
            throw new InvalidPortException();
        }

        try {
            if (secure) {
                createSecureSocket(ipAddress, port);
            } else {
                createStandardSocket(ipAddress, port);
            }

            socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            socketOut = new OutputStreamWriter(socket.getOutputStream());

            createResponseThread();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Creates a thread in the background, which waits for a response from the server.
     *
     * @since 0.0.1
     */
    private void createResponseThread() {
        // Create thread to read incoming messages
        Runnable r = () -> {
            while (true) {
                String msg;
                String[] msgSplit;
                try {
                    msg = socketIn.readLine();
                    logger.info("Response received: " + msg);

                    if (msg != null && msg.length() > 0) {
                        msgSplit = msg.split("\\|");
                        lastMessage.addAll(Arrays.asList(msgSplit));

                        switch (lastMessage.get(0)) {
                            case "MessageText":
                                receivedMessageText(lastMessage.get(1), lastMessage.get(2), lastMessage.get(3));
                                break;
                            case "MessageError":
                                receivedMessageError(lastMessage.get(1));
                                break;
                        }
                    }
                } catch (IOException e) {
                    break;
                }
                if (msg == null) break; // In case the server closes the socket
            }
        };
        Thread t = new Thread(r);
        t.start();
    }

    private void receivedMessageError(String errorMessage) {
        // TODO: Handle if we send wrong commands.
    }

    /**
     * Handle an incoming "MessageText" response, for when someone posts something.
     *
     * @param username   A string containing the name of the user who posted.
     * @param targetChat A string containing the chat where the message was sent.
     * @param text       A string containing the message.
     *
     * @since 0.0.1
     */
    private void receivedMessageText(String username, String targetChat, String text) {
        // TODO: Handle incoming new messages.
    }

    /**
     * Wait until the a "Result" response arrives from the server.
     *
     * @since 0.0.1
     */
    private void waitForResultResponse() {
        // TODO: This is somehow the reason the tests fail
        while (lastMessage.size() == 0 || !lastMessage.get(0).equals("Result")) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // We don't care
                e.printStackTrace();
            }
        }
    }

    /**
     * Sends a command to the server.
     *
     * @param command A string containing the whole string which is sent to the server.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public void sendCommand(String command) throws IOException {
        logger.info("Command sent: " + command);
        socketOut.write(command + "\n");
        socketOut.flush();
    }

    /**
     * Builds the command from the parts and send the command to the server.
     *
     * @param commandParts An array of strings containing all the command parts.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public void sendCommand(String[] commandParts) throws IOException {
        sendCommand(String.join("|", commandParts));
    }

    /**
     * Register a user on the the server.
     *
     * @param username A string containing the name of the user.
     * @param password A string containing the password of the user.
     *
     * @return "true" when user was created. "false" when name already taken (by a user or chatroom) or is simply
     * invalid.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendCreateLogin(String username, String password) throws IOException {
        sendCommand(new String[]{"CreateLogin", username, password});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Login to the server.
     *
     * @param username A string containing the name of the user.
     * @param password A string containing the password of the user.
     *
     * @return A string containing the token when logged in, "null" when username/password to match existing user.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public String sendLogin(String username, String password) throws IOException {
        sendCommand(new String[]{"Login", username, password});

        waitForResultResponse();
        if (lastMessage.get(1).equals("true")) {
            String token = lastMessage.get(2);
            lastMessage.clear();
            return token;
        }
        lastMessage.clear();
        return null;
    }

    /**
     * Overwrite the password of currently logged in user.
     *
     * @param token       A string containing a token given by the server.
     * @param newPassword A string containing the new password to overwrite.
     *
     * @return "true" by default, "false" when the token is invalid.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendChangePassword(String token, String newPassword) throws IOException {
        sendCommand(new String[]{"ChangePassword", token, newPassword});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Delete the currently logged in user from the server. After successful deletion, token becomes invalid
     *
     * @param token A string containing a token given by the server.
     *
     * @return "true" by default, "false" when token is invalid.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendDeleteLogin(String token) throws IOException {
        sendCommand(new String[]{"DeleteLogin", token});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Logs the current user out from the server. After successful logout, token becomes invalid.
     *
     * @return "true" by default. Impossible to fail.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendLogout() throws IOException {
        sendCommand(new String[]{"Logout"});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Creates a new chatroom, where people can send messages. After creation, users still have to added (also the
     * currently logged in user). Rooms can be made private by setting the third variable false.
     *
     * @param token    A string containing a token given by the server.
     * @param name     A string containing the name for the room to be created.
     * @param isPublic A boolean defining whether the room should be public (true) or private (false)
     *
     * @return "true" by default, "false" when name is already taken (by a user or other chatroom), or simply invalid.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendCreateChatroom(String token, String name, boolean isPublic) throws IOException {
        sendCommand(new String[]{"CreateChatroom", token, name, (isPublic ? "true" : "false")});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Adds a given user to a given chatroom. For public chatroom, people can add themselves. For private chatrooms, the
     * creator of it has to add them.
     *
     * @param token    A string containing a token given by the server.
     * @param chatroom A string containing the name for the room to be created.
     * @param username A string containing the name of the user to be added.
     *
     * @return "true" by default, "false" if not added
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendJoinChatroom(String token, String chatroom, String username) throws IOException {
        sendCommand(new String[]{"JoinChatroom", token, chatroom, username});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Adds a given user to a given chatroom. For public chatroom, people can add themselves. For private chatrooms, the
     * creator of it has to add them.
     *
     * @param token    A string containing a token given by the server.
     * @param chatroom A string containing the name of the room.
     * @param user     A UserModel object containing at least the name of the user to be added.
     *
     * @return "true" by default, "false" if not added
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendJoinChatroom(String token, String chatroom, UserModel user) throws IOException {
        return sendJoinChatroom(token, chatroom, user.getUsername());
    }

    /**
     * Removes a user from a given chatroom.
     *
     * @param token    A string containing a token given by the server.
     * @param chatroom A string containing the name of the room.
     * @param username A string containing the name of the user to be removed.
     *
     * @return "true" when removed successfully, "false" if not. (Hint) Logged in user can always remove himself, admin
     * can remove anyone.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendLeaveChatroom(String token, String chatroom, String username) throws IOException {
        sendCommand(new String[]{"LeaveChatroom", token, chatroom, username});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Removes a user from a given chatroom.
     *
     * @param token    A string containing a token given by the server.
     * @param chatroom A string containing the name of the room.
     * @param user     A UserModel object containing at least the name of the user to be added.
     *
     * @return "true" when removed successfully, "false" if not. (Hint) Logged in user can always remove himself, admin
     * can remove anyone.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendLeaveChatroom(String token, String chatroom, UserModel user) throws IOException {
        return sendLeaveChatroom(token, chatroom, user.getUsername());
    }

    /**
     * @param token    A string containing a token given by the server.
     * @param chatroom A string containing the name of the room.
     *
     * @return "true" if removed successfully, "false" if not. (Hint) Only the creator can delete a room.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendDeleteChatroom(String token, String chatroom) throws IOException {
        sendCommand(new String[]{"DeleteChatroom", token, chatroom});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Returns a list of all public chatrooms.
     *
     * @param token A string containing a token given by the server.
     *
     * @return "ArrayList" if 0 or more chatrooms available, "null" if failed
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public ArrayList<String> sendListChatrooms(String token) throws IOException {
        sendCommand(new String[]{"ListChatrooms", token});

        waitForResultResponse();
        if (lastMessage.get(1).equals("true")) {
            lastMessage.remove("Result");
            lastMessage.remove("true");
            ArrayList<String> chatroomList = new ArrayList<>(lastMessage);

            lastMessage.clear();
            return chatroomList;
        }
        lastMessage.clear();
        return null;
    }

    /**
     * Sends a ping to the server
     *
     * @return "true" if succeeds (basically always), "false" if not
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendPing() throws IOException {
        sendCommand(new String[]{"Ping"});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * @param token A string containing a token given by the server.
     *
     * @return "true" if succeeds and token is valid, "false" if one or both cases are not the case.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendPing(String token) throws IOException {
        sendCommand(new String[]{"Ping", token});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Sends a message to a given user or chatroom
     *
     * @param token   A string containing a token given by the server.
     * @param target  A string containing the name of a chatroom or a user.
     * @param message A string containing the message (max length of 1024 characters).
     *
     * @return "true" if message was sent, "false" if 1) target user not online 2) current logged in user is not member
     * of the chatroom 3) if he message is over 1024 characters
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendSendMessage(String token, String target, String message) throws IOException {
        if (message.length() > 1024) {
            return false;
        }
        sendCommand(new String[]{"SendMessage", token, target, message});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Checks whether the user is currently logged in.
     *
     * @param token    A string containing a token given by the server.
     * @param username A string containing the username of the wanted user.
     *
     * @return "true" if user is currently logged in, "false" if not.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendUserOnline(String token, String username) throws IOException {
        sendCommand(new String[]{"UserOnline", token, username});

        waitForResultResponse();
        boolean result = Boolean.parseBoolean(lastMessage.get(1));
        lastMessage.clear();
        return result;
    }

    /**
     * Checks whether the user is currently logged in.
     *
     * @param token A string containing a token given by the server.
     * @param user  A UserModel object containing at least the name of the user.
     *
     * @return "true" if user is currently logged in, "false" if not.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public boolean sendUserOnline(String token, UserModel user) throws IOException {
        return sendUserOnline(token, user.getUsername());
    }

    /**
     * Get a list of all members inside a chatroom.
     *
     * @param token    A string containing a token given by the server.
     * @param chatroom A string containing the name of the chatroom.
     *
     * @return "ArrayList" of 0 or more users inside the chat, "null" if failed or currently logged in user is not a
     * member
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public ArrayList<String> sendListChatroomUsers(String token, String chatroom) throws IOException {
        sendCommand(new String[]{"ListChatroomUsers", token, chatroom});

        waitForResultResponse();
        if (lastMessage.get(1).equals("true")) {
            lastMessage.remove("Result");
            lastMessage.remove("true");
            ArrayList<String> usersList = new ArrayList<>(lastMessage);

            lastMessage.clear();
            return usersList;
        }
        lastMessage.clear();
        return null;
    }

    /**
     * Creates a normal socket to the server.
     *
     * @param ipAddress A String containing the ip address to reach the server.
     * @param port      An integer containing the port which the server uses.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public void createStandardSocket(String ipAddress, int port) throws IOException {
        logger.info("Connecting to server at: " + ipAddress + ":" + port);
        socket = new Socket(ipAddress, port);
    }

    /**
     * Creates a secure socket (SSL) to the server.
     *
     * @param ipAddress A String containing the ip address to reach the server.
     * @param port      An integer containing the port which the server uses.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    public void createSecureSocket(String ipAddress, int port) throws IOException {
        logger.info("Connecting to server at: " + ipAddress + ":" + port + " (with SSL)");

        // TODO: SSL is not properly setup
        // Check out: https://gitlab.fhnw.ch/bradley.richards/java-projects/blob/master/src/chatroom/Howto_SSL_Certificates_in_Java.odt

        // Registering the JSSE provider
        Security.addProvider(new Provider());

        // Specifying the Truststore details. This is needed if you have created a
        // truststore, for example, for self-signed certificates
        System.setProperty("javax.net.ssl.trustStore", "truststore.ts");
        System.setProperty("javax.net.ssl.trustStorePassword", "trustme");

        // Creating Client Sockets
        SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        socket = sslsocketfactory.createSocket(ipAddress, port);

        // The next line is entirely optional !!
        // The SSL handshake would happen automatically, the first time we send data.
        // Or we can immediately force the handshaking with this method:
        ((SSLSocket) socket).startHandshake();
    }

    /**
     * Verifies that the string is a valid ip address.
     *
     * @param ipAddress A String containing the ip address.
     *
     * @return "true" if valid, "false" if not.
     *
     * @since 0.0.1
     */
    /*
     * Reference: https://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
     */
    public static boolean isValidIpAddress(String ipAddress) {
        return ipAddress.matches("^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$");
    }

    /**
     * Verifies that the string is in the valid range of open ports.
     *
     * @param port An integer containing the port.
     *
     * @return "true" if valid, "false" if not.
     *
     * @since 0.0.1
     */
    public static boolean isValidPortNumber(int port) {
        return port >= 1024 && port <= 65535;
    }

    /**
     * Closes the socket.
     *
     * @throws IOException If an I/O error occurs.
     * @since 0.0.1
     */
    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
        }
    }
}
