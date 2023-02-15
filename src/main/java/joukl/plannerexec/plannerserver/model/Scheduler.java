package joukl.plannerexec.plannerserver.model;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
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

                InputStream serverInputStream = clientSocket.getInputStream();
                byte[] encryptedKey = serverInputStream.readAllBytes();
                System.out.println("here");

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.PRIVATE_KEY, authorization.getServerPrivateKey());
                byte[] decryptedKey = cipher.doFinal(encryptedKey);
                System.out.println("and here");

                SecretKey originalKey = new SecretKeySpec(decryptedKey, 0, decryptedKey.length, "AES");
                Cipher aesCipher = Cipher.getInstance("AES");
                aesCipher.init(Cipher.DECRYPT_MODE, originalKey);

                CipherInputStream cipherInputStream = new CipherInputStream(clientSocket.getInputStream(), aesCipher);

                Cipher aesOutCipher = Cipher.getInstance("AES");
                aesCipher.init(Cipher.ENCRYPT_MODE, originalKey);
                CipherOutputStream AESoutStream = new CipherOutputStream(clientSocket.getOutputStream(), aesOutCipher);
                AESoutStream.write("Pokus 123 pokus ...".getBytes(StandardCharsets.UTF_8));


                System.out.println("at end");
                cipherInputStream.readAllBytes();
                String message = new String(cipherInputStream.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println(message + " mesič");

                cipherInputStream.close();

            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (NoSuchPaddingException | InvalidKeyException e) {
                throw new RuntimeException(e);
                    /*
                });

                     */
            } catch (IllegalBlockSizeException e) {
                throw new RuntimeException(e);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } catch (BadPaddingException e) {
                throw new RuntimeException(e);
            }
            /*
        });

             */
        }
    }
}
