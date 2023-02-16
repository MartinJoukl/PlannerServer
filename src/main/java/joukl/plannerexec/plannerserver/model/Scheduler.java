package joukl.plannerexec.plannerserver.model;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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

    private final char STOP_SYMBOL = (char) 1f;

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
            final Socket socket = serverSocket.accept();
            // start processing on another thread, accept more
                        /*
                pool.submit(() -> {

                         */
            socket.setTcpNoDelay(true);
            try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                InputStream in = socket.getInputStream();
                byte[] encryptedKey = in.readNBytes(256);

                Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.PRIVATE_KEY, authorization.getServerPrivateKey());
                byte[] decryptedKey = cipher.doFinal(encryptedKey);

                //decrypting
                SecretKey originalKey = new SecretKeySpec(decryptedKey, 0, decryptedKey.length, "AES");
                Cipher aesDecrypting = Cipher.getInstance("AES");
                aesDecrypting.init(Cipher.DECRYPT_MODE, originalKey);
                //encrypting
                Cipher aesEncrypting = Cipher.getInstance("AES");
                aesEncrypting.init(Cipher.ENCRYPT_MODE, originalKey);
                //finish initial read

                for (int i = 0; i < 1000; i--) {
                    byte[] messageToSend = ("Ahoj"+i).getBytes(StandardCharsets.UTF_8);

                    sendEncryptedMessage(aesEncrypting, out, messageToSend);
                    String message = readEncryptedString(aesDecrypting, in);
                    System.out.println(message);
                }

                //*    CipherInputStream aesCipherInputStream = new CipherInputStream(socket.getInputStream(), aesDecrypting);


                //fake wrapper for output stream - as cipher stream won't actually send when flushed.
                //  NotClosingOutputStream notClosingOutputStream = new NotClosingOutputStream(socket.getOutputStream());
                //*    CipherOutputStream AESoutStream = new CipherOutputStream(socket.getOutputStream(), aesEncrypting);

                //end of symetric key exchange

                byte[] bytes = readBytesUntilStop(in);
                String message = new String(bytes, StandardCharsets.UTF_8);
                System.out.println(message + "<--msg1");
                System.out.println("----");
                //send response
                //  AESoutStream.write("navázáno spojení".getBytes(StandardCharsets.UTF_8));
                //  AESoutStream.flush();
                //  AESoutStream.close();


                byte[] bytes2 = readBytesUntilStop(in);
                message = new String(bytes2, StandardCharsets.UTF_8);
                System.out.println(message + "<--- msg2");

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

    private byte[] readBytesUntilStop(InputStream cipherInputStream) throws IOException {
        List<Byte> bytes = new ArrayList<>();
        int byteAsInt = cipherInputStream.read();
        while (!(byteAsInt == -1 || (char) byteAsInt == STOP_SYMBOL)) {
            bytes.add((byte) byteAsInt);
            byteAsInt = cipherInputStream.read();
        }
        byte[] bytesArr = new byte[bytes.size()];
        for (int i = 0; i < bytesArr.length; i++) {
            bytesArr[i] = bytes.get(i);
        }

        return bytesArr;
    }

    public String decrypt(byte[] encrypted, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] plainText = cipher.doFinal(Base64.getDecoder().decode(encrypted));
        return new String(plainText);
    }

    public String encrypt(byte[] decrypted, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        byte[] cipherText = cipher.doFinal(decrypted);
        return Base64.getEncoder().encodeToString(cipherText);
    }

    private void sendEncryptedMessage(Cipher aesEncrypting, DataOutputStream out, byte[] messageToSend) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String encrypted = encrypt(messageToSend, aesEncrypting);
        String withStop = encrypted+STOP_SYMBOL;

        out.write(withStop.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String readEncryptedString(Cipher aesDecrypting, InputStream in) throws IOException, IllegalBlockSizeException, BadPaddingException {
        byte[] bytes = readBytesUntilStop(in);
        String message = decrypt(bytes, aesDecrypting);
        return message;
    }
}
