package joukl.plannerexec.plannerserver.model;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Timestamp;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Scheduler {
    private static final Scheduler SCHEDULER = new Scheduler();

    private Scheduler() {
    }

    public static Scheduler getScheduler() {
        return SCHEDULER;
    }

    private Map<String, Client> clients = new HashMap<>();
    private Map<String, Queue> queueMap = new TreeMap<>();
    private Authorization authorization = new Authorization();
    private ServerSocket serverSocket;

    private PrintWriter out;
    private BufferedInputStream in;

    private static ExecutorService pool = Executors.newCachedThreadPool();
    //internal for
    //private PriorityQueue queuesPriority = new PriorityQueue<>(Comparator.comparingInt(Queue::getPriority));

    //TODO metody plánování

    public Map<String, Client> getClients() {
        return clients;
    }

    public Boolean isListening() {
        return this.serverSocket != null && !this.serverSocket.isClosed();
    }

    public void setClients(Map<String, Client> clients) {
        this.clients = clients;
    }

    public Map<String, Queue> getQueueMap() {
        return queueMap;
    }

    public void setQueueMap(Map<String, Queue> queueMap) {
        this.queueMap = queueMap;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }

    //for gui
    public List<Task> getTasksAsList() {
        List<Task> tasks = new ArrayList<>();
        queueMap.forEach((key, queue) -> tasks.addAll(queue.getTasks()));
        return tasks;
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    public void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void startListening() throws IOException {
        serverSocket = new ServerSocket(6660);
        //listen on new thread
        /*
        pool.submit(() -> {

         */
        while (true) {
            final Socket clientSocket = serverSocket.accept();
            // start processing on another thread, accept more
                        /*
                pool.submit(() -> {

                         */
            try {

                Cipher cipherIn = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipherIn.init(Cipher.DECRYPT_MODE, authorization.getServerPrivateKey());

                CipherInputStream inputStream = new CipherInputStream(clientSocket.getInputStream(), cipherIn);

                Cipher cipherOut = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipherIn.init(Cipher.ENCRYPT_MODE, authorization.getClientPublicKey());
                NotClosingOutputStream notClosingOutputStream = new NotClosingOutputStream(clientSocket.getOutputStream());
                CipherOutputStream outputStream = new CipherOutputStream(notClosingOutputStream, cipherOut);

                String message = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                if (message.contains("NEW")) {
                    String[] parsedMessage = message.split(";");

                    Agent agent = Agent.valueOf(parsedMessage[1]);
                    long resources = Long.parseLong(parsedMessage[2]);
                    String id = String.valueOf(UUID.randomUUID());

                }

                System.out.println(message);
                System.out.println("done :)");
                //ACK
                outputStream.write((char) 0x06);
                outputStream.flush();
                outputStream.close();
                //another message - queues
                message = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println(message);
                System.out.println("done again :)");


            } catch (IOException | NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
                System.out.println("oops");
                throw new RuntimeException(e);
            }
                    /*
                });

                     */
        }
            /*
        });

             */
    }
}
