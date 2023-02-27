package joukl.plannerexec.plannerserver.model;

import javafx.application.Platform;
import joukl.plannerexec.plannerserver.viewModel.ApplicationController;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.io.File.separator;

public class Scheduler {
    private static final Scheduler SCHEDULER = new Scheduler();

    public static final String PATH_TO_TASK_STORAGE = "storage" + separator + "tasks" + separator;

    public static final String PATH_TO_TASK_RESULTS_STORAGE = "storage" + separator + "results" + separator;
    private static final int NUMBER_OF_RETRY = 3;


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

    public void stopListening() throws IOException {
        serverSocket.close();
    }

    public void startListening(ApplicationController guiToRefresh) throws IOException {
        serverSocket = new ServerSocket(6660);
        //listen on new thread

        pool.submit(() -> {

            while (!serverSocket.isClosed()) {
                final Socket socket;
                try {
                    socket = serverSocket.accept();
                    socket.setSoTimeout(200000); //2 s
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                // start processing on another thread, accept more

                pool.submit(() -> {

                    try {
                        socket.setTcpNoDelay(true);
                    } catch (SocketException e) {
                        throw new RuntimeException(e);
                    }
                    Cipher decrypting = null;
                    Cipher encrypting = null;
                    try {
                        //decrypting
                        decrypting = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        decrypting.init(Cipher.DECRYPT_MODE, authorization.getServerPrivateKey());

                        //encrypting
                        encrypting = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        encrypting.init(Cipher.ENCRYPT_MODE, authorization.getClientPublicKey());
                    } catch (Exception ignored) {
                        //... can't happen
                    }


                    try (DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
                        InputStream in = socket.getInputStream();

                        if (!validateClient(socket, decrypting, encrypting, out, in)) {
                            return;
                        }

                        //read symmetric key
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

                        if (message.contains("NEW")) {
                            client = registerNewClient(out, in, aesDecrypting, aesEncrypting, message);
                        } else if (message.contains("RESULT")) {
                            client = getExistingClientOrCreateNew(out, in, aesDecrypting, aesEncrypting, message);
                            //retrieve task if we found client (meaning it has tasks)
                            if (client.getNumberOfTasks() > 0) {
                                String taskId = readEncryptedString(aesDecrypting, in);
                                Optional<Task> optTask = client.getWorkingOnTasks().stream()
                                        .filter(t -> t.getId().equals(taskId))
                                        .findAny();
                                receiveResultWithRetry(socket, out, in, aesDecrypting, aesEncrypting, optTask, NUMBER_OF_RETRY);
                                socket.close();
                                return;
                            }
                        } else {
                            client = getExistingClientOrCreateNew(out, in, aesDecrypting, aesEncrypting, message);
                        }
                        Task givenTask = null;
                        try {
                            //mutex??
                            givenTask = selectTaskAndRegisterIt(client, guiToRefresh);
                            //no task? return
                            if (givenTask == null) {
                                sendEncryptedMessage(aesEncrypting, out, "NO_TASK".getBytes(StandardCharsets.UTF_8));
                                return;
                            } else {
                                sendEncryptedMessage(aesEncrypting, out, String.format("%s;%d", givenTask.getId(), givenTask.getCost()).getBytes(StandardCharsets.UTF_8));
                                //check response for cost status
                                String response = readEncryptedString(aesDecrypting, in);
                                if (response.equals("COST_TOO_HIGH")) {
                                    client.getWorkingOnTasks().remove(givenTask);
                                    givenTask.setStatus(TaskStatus.SCHEDULED);
                                    socket.close();
                                    return;
                                } else {
                                    client.setAvailableResources(Integer.parseInt(response));
                                }
                            }

                            sendZipOfTask(out, aesEncrypting, givenTask);

                        } catch (Exception e) {
                            if (givenTask != null) {
                                //reschedule it
                                System.out.println("err1 " + e.getMessage());
                                givenTask.setStatus(TaskStatus.SCHEDULED);
                            }
                        }

                    } catch (IOException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                             NoSuchAlgorithmException | BadPaddingException e) {
                        System.out.println("err2 " + e.getMessage());
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    private void receiveResultWithRetry(Socket socket, DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, Optional<Task> optTask, int numberOfRetry) throws IllegalBlockSizeException, BadPaddingException, IOException {
        if (optTask.isPresent()) {
            //TODO check status
            Task relatedTask = optTask.get();
            sendEncryptedMessage(aesEncrypting, out, "FOUND".getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < numberOfRetry; i++) {
                boolean gotResults = receiveTaskResults(aesDecrypting, in, relatedTask);
                if (gotResults) {
                    relatedTask.setStatus(TaskStatus.FINISHED);
                    //send message to client
                    sendEncryptedMessage(aesEncrypting, out, "FINISHED".getBytes(StandardCharsets.UTF_8));
                    return;
                } else if (i < numberOfRetry - 1) {
                    sendEncryptedMessage(aesEncrypting, out, "FAILED".getBytes(StandardCharsets.UTF_8));
                } else {
                    sendEncryptedMessage(aesEncrypting, out, "FAILED_FINAL".getBytes(StandardCharsets.UTF_8));
                }
            }
        } else {
            //we did not find the task - return
            System.out.println("no task found");
            sendEncryptedMessage(aesEncrypting, out, "NOT_FOUND".getBytes(StandardCharsets.UTF_8));
            socket.close();
        }
    }

    private void sendZipOfTask(DataOutputStream out, Cipher aesEncrypting, Task givenTask) throws IOException, IllegalBlockSizeException, BadPaddingException {
        File zipFile = new File(givenTask.getPathToZipFile());
        long remainingLength = zipFile.length();
        int readLength = 2048;
        try (FileInputStream fis = new FileInputStream(zipFile)) {
            while (remainingLength > 0) {

                if (readLength > remainingLength) {
                    readLength = (int) remainingLength;
                }
                //encrypt
                byte[] encrypted = aesEncrypting.doFinal(fis.readNBytes(readLength));

                long encryptedLength = encrypted.length;
                //send size
                sendEncryptedMessage(aesEncrypting, out, Long.toString(encryptedLength).getBytes(StandardCharsets.UTF_8));
                //send bytes
                out.write(encrypted);

                remainingLength -= readLength;
            }
            //send 0 to signalize we are done
            sendEncryptedMessage(aesEncrypting, out, Long.toString(0).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static boolean validateClient(Socket socket, Cipher decrypting, Cipher encrypting, DataOutputStream out, InputStream in) throws IllegalBlockSizeException, BadPaddingException, IOException {
        //respond to challenge
        byte[] challenge = decrypting.doFinal(in.readNBytes(256));
        out.write(encrypting.doFinal(challenge));
        //send challenge
        String uuId = UUID.randomUUID().toString();
        out.write(encrypting.doFinal(uuId.getBytes(StandardCharsets.UTF_8)));
        String response = new String(decrypting.doFinal(in.readNBytes(256)));
        //validate response
        if (!uuId.equals(response)) {
            socket.close();
            return false;
        }
        return true;
    }

    private Client getExistingClientOrCreateNew(DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, String message) throws IllegalBlockSizeException, BadPaddingException, IOException {
        Client client;
        String[] parsedMessage = message.split(";");
        String id = parsedMessage[0];
        int availableResources = Integer.parseInt(parsedMessage[1]);

        client = clients.get(id);
        if (client == null) {
            id = String.valueOf(UUID.randomUUID());
            //send id
            sendEncryptedMessage(aesEncrypting, out, id.getBytes(StandardCharsets.UTF_8));

            Agent agent = Agent.valueOf(parsedMessage[2]);
            //wait for queues
            List<String> queues = readEncryptedList(aesDecrypting, in);

            client = new Client(id, agent, availableResources, ClientStatus.ACTIVE, new Date(), new ArrayList<>(), queues);
            clients.put(id, client);
        } else {
            sendEncryptedMessage(aesEncrypting, out, "ACK".getBytes(StandardCharsets.UTF_8));
            client.setAvailableResources(availableResources);
        }
        return client;
    }

    private Client registerNewClient(DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, String message) throws IOException, IllegalBlockSizeException, BadPaddingException {
        Client client;
        String[] parsedMessage = message.split(";");

        Agent agent = Agent.valueOf(parsedMessage[1]);
        int availableResources = Integer.parseInt(parsedMessage[2]);
        String id = String.valueOf(UUID.randomUUID());
        List<String> queues = readEncryptedList(aesDecrypting, in);

        client = new Client(id, agent, availableResources, ClientStatus.ACTIVE, new Date(), Collections.synchronizedList(new ArrayList<>()), queues);
        clients.put(id, client);
        //send id to client
        sendEncryptedMessage(aesEncrypting, out, client.getId().getBytes(StandardCharsets.UTF_8));
        return client;
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

    public void sendEncryptedMessage(Cipher aesEncrypting, DataOutputStream out, byte[] messageToSend) throws IllegalBlockSizeException, BadPaddingException, IOException {
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

    private synchronized Task selectTaskAndRegisterIt(Client client, ApplicationController guiToRefresh) {
        List<String> subscribedQueues = client.getSubscribedQueues();
        //if client does not specify queues, subscribe to all
        if (subscribedQueues == null || subscribedQueues.isEmpty() || subscribedQueues.get(0).equals("")) {
            subscribedQueues = queueMap.keySet().stream().toList();
        }

        List<String> subscribedQueuesFinal = subscribedQueues;

        Collection<Queue> queues = queueMap.values();
        //get only queues that client contains, are of his agent, order them by priority
        List<Queue> subscribedNotIteratedQueues = new ArrayList<>(queues.stream().
                filter((q) -> subscribedQueuesFinal.contains(q.getName()) && q.getAgents().contains(client.getAgent()))
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
            task.setStatus(TaskStatus.RUNNING);
            client.getWorkingOnTasks().add(task);
            Platform.runLater(guiToRefresh::refreshTaskList);

            return task;
        }
        //we have no task
        return null;
    }

    private boolean receiveTaskResults(Cipher aesDecrypting, InputStream in, Task task) throws IOException, IllegalBlockSizeException, BadPaddingException {
        File resDir = new File(PATH_TO_TASK_RESULTS_STORAGE + task.getName());
        resDir.mkdirs();
        // we will get task - message it is id
        try (FileOutputStream fos = new FileOutputStream(resDir.getPath() + separator + task.getId() + ".zip")) {
            int receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
            while (receivedChunkSize > 0) {
                //decrypt and write
                fos.write(aesDecrypting.doFinal(in.readNBytes(receivedChunkSize)));
                receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
            }
        }
        return true;
    }
}
