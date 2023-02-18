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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipOutputStream;

public class Scheduler {
    private static final Scheduler SCHEDULER = new Scheduler();

    private Scheduler() {
    }

    public static Scheduler getScheduler() {
        return SCHEDULER;
    }

    private Map<String, Client> clients = new HashMap<>();
    private Map<String, Queue> queueMap = new ConcurrentHashMap<>();
    private Authorization authorization = new Authorization();
    private ServerSocket serverSocket;

    private final char STOP_SYMBOL = 31;
    private final char ARR_STOP_SYMBOL = 30;

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
                String message = readEncryptedString(aesDecrypting, in);

                Client client;
                //TODO metoda, registrace nového klienta
                if (message.contains("NEW")) {
                    String[] parsedMessage = message.split(";");

                    Agent agent = Agent.valueOf(parsedMessage[1]);
                    long availableResources = Long.parseLong(parsedMessage[2]);
                    String id = String.valueOf(UUID.randomUUID());
                    List<String> queues = readEncryptedList(aesDecrypting, in);

                    client = new Client(id, agent, availableResources, ClientStatus.ACTIVE, new Date(), new ArrayList<>(), queues);
                    clients.put(id, client);
                    //send id to client
                    sendEncryptedMessage(aesEncrypting, out, client.getId().getBytes(StandardCharsets.UTF_8));
                } else {
                    //ID;resources
                    //TODO na klientovy
                    //mesage contains id
                    message = readEncryptedString(aesDecrypting, in);
                    String[] parsedMessage = message.split(";");
                    String id = parsedMessage[0];
                    String availableResources = parsedMessage[1];

                    client = clients.get(id);
                    client.setAvailableResources(Long.parseLong(availableResources));
                }
                Task givenTask = null;
                try {
                    //mutex??
                    givenTask = selectTaskAndRegisterIt(client);
                    //zip file and send it through channel

                    FileOutputStream fos = new FileOutputStream(givenTask.getPathToSourceDirectory() + ".zip");
                    ZipOutputStream zipOut = new ZipOutputStream(fos);

                    File fileToZip = new File(givenTask.getPathToSourceDirectory());
                    Persistence.zipFile(fileToZip, fileToZip.getName(), zipOut);
                    zipOut.close();
                    fos.close();

                    //send task to client
                    // out.write();
                } catch (Exception e) {
                    if (givenTask != null) {
                        //reschedule it
                        givenTask.setStatus(TaskStatus.SCHEDULED);
                    }
                }

                //validate capacity

                /*
                for (int i = 0; i < 1000; i--) {
                    byte[] messageToSend = ("Ahoj"+i).getBytes(StandardCharsets.UTF_8);

                    sendEncryptedMessage(aesEncrypting, out, messageToSend);
                    System.out.println(message);
                }

                 */

                //*    CipherInputStream aesCipherInputStream = new CipherInputStream(socket.getInputStream(), aesDecrypting);


                //fake wrapper for output stream - as cipher stream won't actually send when flushed.
                //  NotClosingOutputStream notClosingOutputStream = new NotClosingOutputStream(socket.getOutputStream());
                //*    CipherOutputStream AESoutStream = new CipherOutputStream(socket.getOutputStream(), aesEncrypting);

                //end of symetric key exchange

                //send response
                //  AESoutStream.write("navázáno spojení".getBytes(StandardCharsets.UTF_8));
                //  AESoutStream.flush();
                //  AESoutStream.close();

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

    private byte[] readBytesUntilStop(InputStream cipherInputStream, char stopSymbol) throws IOException {
        List<Byte> bytes = new ArrayList<>();
        int byteAsInt = cipherInputStream.read();
        while (!(byteAsInt == -1 || (char) byteAsInt == stopSymbol)) {
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
        String withStop = encrypted + STOP_SYMBOL;

        out.write(withStop.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private String readEncryptedString(Cipher aesDecrypting, InputStream in) throws IOException, IllegalBlockSizeException, BadPaddingException {
        byte[] bytes = readBytesUntilStop(in, STOP_SYMBOL);
        String message = decrypt(bytes, aesDecrypting);
        return message;
    }

    public String encryptList(List<String> toEncrypt, Cipher cipher) throws IllegalBlockSizeException, BadPaddingException {
        StringBuilder encodedArrWithDeli = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            //encode each item
            String encodedItem = Base64.getEncoder().encodeToString(toEncrypt.get(i).getBytes(StandardCharsets.UTF_8));
            encodedArrWithDeli.append(encodedItem).append(STOP_SYMBOL);
        }

        //encrypt whole array
        String enryptedArr = encrypt(encodedArrWithDeli.toString().getBytes(StandardCharsets.UTF_8), cipher);
        //add delimiter
        enryptedArr += (ARR_STOP_SYMBOL);
        return enryptedArr;
    }

    public void sendEncryptedList(Cipher cipher, DataOutputStream out, List<String> toEncrypt) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String encrypted = encryptList(toEncrypt, cipher);
        out.write(encrypted.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private List<String> readEncryptedList(Cipher aesDecrypting, InputStream in) throws IOException, IllegalBlockSizeException, BadPaddingException {
        //read array bytes
        byte[] arrBase64 = readBytesUntilStop(in, ARR_STOP_SYMBOL);

        //decrypt message, so we have itemInBase64+stop_symbol
        String arrMessage = decrypt(arrBase64, aesDecrypting);
        //split items using deli
        List<String> base64Items = List.of(arrMessage.split(String.valueOf(STOP_SYMBOL)));

        return base64Items.stream()
                .map((encodedItem) -> new String(Base64.getDecoder().decode(encodedItem), StandardCharsets.UTF_8))
                .toList();
    }

    private synchronized Task selectTaskAndRegisterIt(Client client) {
        List<String> subscribedQueues = client.getSubscribedQueues();
        //if client does not specify queues, subscribe to all
        if (subscribedQueues == null || subscribedQueues.isEmpty()) {
            subscribedQueues = queueMap.keySet().stream().toList();
        }

        List<String> subscribedQueuesFinal = subscribedQueues;

        Collection<Queue> queues = queueMap.values();
        //get only queues that client contains, order them by priority
        List<Queue> subscribedNotIteratedQueues = new ArrayList<>(queues.stream().
                filter((q) -> subscribedQueuesFinal.contains(q.getName()))
                .sorted(Comparator.comparingInt(Queue::getPriority).reversed())
                .toList());

        //if we have no queue
        if (subscribedNotIteratedQueues.isEmpty()) {
            return null;
        }

        while (!subscribedNotIteratedQueues.isEmpty()) {
            // always get first task
            int priority = subscribedNotIteratedQueues.get(0).getPriority();
            List<Queue> queuesWithSamePriority = subscribedNotIteratedQueues.stream().filter((q -> q.getPriority() == priority)).toList();
            subscribedNotIteratedQueues.removeIf((q) -> q.getPriority() == priority);

            // create common pool for task from queues
            PriorityQueue<Task> tasks = new PriorityQueue<>(Comparator.comparingInt(Task::getPriority).reversed());
            for (Queue iteratedQueue : queuesWithSamePriority
            ) {
                tasks.addAll(iteratedQueue.getTasks());
            }
            //remove all task that are not in SCHEDULED state or the cost is higher than available resources
            tasks.removeIf((t) -> t.getStatus() != TaskStatus.SCHEDULED || t.getCost() > client.getAvailableResources());

            //now we have pool with task, retrieve task with the highest priority (first) or return if we have no task
            if (tasks.isEmpty()) {
                continue;
            }

            Task task = tasks.remove();
            task.setStatus(TaskStatus.SCHEDULED);
            client.getWorkingOnTasks().add(task);

            return task;
        }
        //we have no task
        return null;
    }
}
