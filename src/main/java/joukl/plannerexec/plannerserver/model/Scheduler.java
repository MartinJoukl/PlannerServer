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
import java.util.concurrent.*;

import static java.io.File.separator;

public class Scheduler {
    private static final Scheduler SCHEDULER = new Scheduler();

    public static final String PATH_TO_TASK_STORAGE = "storage" + separator + "tasks" + separator;

    public static final String PATH_TO_TASK_RESULTS_STORAGE = "storage" + separator + "results" + separator;
    private static final int NUMBER_OF_RETRY = 3;
    private long clientTimeoutDeadline = 20000; //20s
    private long clientNoResponseTime = 2000; //2s
    private long timeoutDelay = 2000; //2 s delay

    private int port = 6660;


    private Scheduler() {
        Configuration config = null;
        try {
            config = Persistence.readApplicationConfiguration();
        } catch (Exception e) {
            System.out.println("Unexpected error during loading of configuration, default values will be used...");
        }
        if (config != null) {
            timeoutDelay = config.getTaskTimeoutDelay();
            clientTimeoutDeadline = config.getClientTimeoutDeadline();
            clientNoResponseTime = config.getClientNoResponseTime();
            port = config.getPort();
            config.getQueues().forEach((q) -> queueMap.put(q.getName(), q));
        } else {
            System.out.println("No valid configuration found... using default values...\ntimeoutDelay: " + this.timeoutDelay + ", clientTimeoutDeadline: " + this.clientTimeoutDeadline + ", clientNoResponseTime: " + clientNoResponseTime + ", listening port: " + port);
        }
    }

    public void startObserver() {
        ScheduledExecutorService observer = Executors.newScheduledThreadPool(1);
        //periodically create new pooling threads
        //refresh every 200 ms
        //get gui controller to refresh - dirty
        ApplicationController controller = ApplicationController.getGuiController();
        observer.scheduleAtFixedRate(() -> {
            try {
                checkForClientResponse(controller);

                //refresh gui - dirty
                Platform.runLater(controller::refreshClientList);

                List<Task> activeTasks = getRunningTasksAsList();

                for (Task activeTask : activeTasks
                ) {
                    boolean taskFailed = false;
                    // minus for some reason
                    if ((activeTask.getTimeoutDeadline() != null && new Date(System.currentTimeMillis() - timeoutDelay).after(activeTask.getTimeoutDeadline()))) {
                        failTask(activeTask.getClient(), activeTask);
                        //System.out.println("Task " + activeTask.getName() + ", id: " + activeTask.getId() + " has timed out!");
                        taskFailed = true;
                    }

                    if (taskFailed) {
                        Platform.runLater(controller::refreshTaskList);
                    }
                }
            } catch (Exception e) {
                System.out.println("Observer exception: "+e.getMessage());
            }

        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    private void checkForClientResponse(ApplicationController controller) {
        for (Client client : clients.values()) {
            if (new Date(client.getLastReply().getTime() + clientNoResponseTime).before(new Date())) {
                //check if final deadline passed
                if (new Date(client.getLastReply().getTime() + clientTimeoutDeadline).before(new Date())) {
                    //remove client and add tasks to failed list
                    for (Task toReAdd : client.getWorkingOnTasks()) {
                        toReAdd.setStatus(TaskStatus.FAILED);
                        historicalTasks.add(toReAdd);
                    }

                    client.getWorkingOnTasks().clear();
                    clients.remove(client.getId());
                    Platform.runLater(controller::refreshTaskList);
                } else {
                    //or mark client as not responding
                    client.setStatus(ClientStatus.NO_RESPONSE);
                }
            } else {
                if (client.getWorkingOnTasks().size() > 0) {
                    client.setStatus(ClientStatus.WORKING);
                } else {
                    client.setStatus(ClientStatus.ACTIVE);
                }
            }
        }
    }

    public static Scheduler getScheduler() {
        return SCHEDULER;
    }

    private Map<String, Client> clients = new HashMap<>();
    private Map<String, Queue> queueMap = new ConcurrentHashMap<>();
    private Authorization authorization = new Authorization();

    private final List<Task> historicalTasks = Collections.synchronizedList(new LinkedList<>());
    private ServerSocket serverSocket;

    private final char STOP_SYMBOL = 31;
    private final char ARR_STOP_SYMBOL = 30;

    private static ExecutorService pool = Executors.newCachedThreadPool();
    //internal for
    //private PriorityQueue queuesPriority = new PriorityQueue<>(Comparator.comparingInt(Queue::getPriority));

    public Map<String, Client> getClients() {
        return clients;
    }

    public Boolean isListening() {
        return this.serverSocket != null && !this.serverSocket.isClosed();
    }

    public Map<String, Queue> getQueueMap() {
        return queueMap;
    }

    public Authorization getAuthorization() {
        return authorization;
    }

    public void setAuthorization(Authorization authorization) {
        this.authorization = authorization;
    }


    public List<Task> getRunningTasksAsList() {
        List<Task> tasks = new ArrayList<>();
        queueMap.forEach((key, queue) -> tasks.addAll(queue.getNonScheduledTasks()));
        tasks.removeIf(task -> task.getStatus() != TaskStatus.RUNNING);
        return tasks;
    }

    //for gui - returns running and active tasks
    public List<Task> getActiveTasksAsList() {
        List<Task> tasks = new ArrayList<>();
        queueMap.forEach((key, queue) -> tasks.addAll(queue.getTaskSchedulingQueue()));
        queueMap.forEach((key, queue) -> tasks.addAll(queue.getNonScheduledTasks()));
        return tasks;
    }


    public List<Task> getAllTasksAsList() {
        List<Task> tasks = new ArrayList<>();
        queueMap.forEach((key, queue) -> tasks.addAll(queue.getTaskSchedulingQueue()));
        queueMap.forEach((key, queue) -> tasks.addAll(queue.getNonScheduledTasks()));
        tasks.addAll(historicalTasks);
        return tasks;
    }


    public synchronized void transferTaskToHistoryRecords(Task task) {
        task.getQueue().getNonScheduledTasks().remove(task);
        historicalTasks.add(task);
    }

    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    public void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    public void stopListening() throws IOException {
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    public List<Task> getHistoricalTasks() {
        return historicalTasks;
    }

    public void startListening(ApplicationController guiToRefresh) throws IOException {
        serverSocket = new ServerSocket(port);
        //listen on new thread

        pool.submit(() -> {

            while (!serverSocket.isClosed()) {
                final Socket acceptedSocket;
                try {
                    acceptedSocket = serverSocket.accept();
                    acceptedSocket.setSoTimeout(2000); //2 s
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                // start processing on another thread, accept more

                pool.submit(() -> {
                    try {
                        acceptedSocket.setTcpNoDelay(true);
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
                        //... can't happen... right?
                        System.out.println("Ignored exception happened ... " + ignored.getMessage());
                    }


                    try (DataOutputStream out = new DataOutputStream(acceptedSocket.getOutputStream())) {
                        InputStream in = acceptedSocket.getInputStream();

                        if (!validateClient(acceptedSocket, decrypting, encrypting, out, in)) {
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
                        String initialIdentification = readEncryptedString(aesDecrypting, in);

                        Client client = null;

                        //------------------ initial exchange finished ------------------

                        if (initialIdentification.contains("NEW")) {
                            client = registerNewClient(out, in, aesDecrypting, aesEncrypting, initialIdentification);
                        } else if (initialIdentification.contains("RESULT")) {
                            client = getExistingClientOrCreateNew(out, in, aesDecrypting, aesEncrypting, initialIdentification);
                            //retrieve task if we found client (meaning it has tasks)
                            if (client.getNumberOfTasks() > 0) {
                                findAndReceiveTask(guiToRefresh, acceptedSocket, out, in, aesDecrypting, aesEncrypting, client);
                                return;
                            }
                        } else if (initialIdentification.contains("EXCEPTION")) {
                            client = getExistingClientOrCreateNew(out, in, aesDecrypting, aesEncrypting, initialIdentification);
                            failTaskIfPresent(in, aesDecrypting, client);
                            Platform.runLater(guiToRefresh::refreshTaskList);
                            acceptedSocket.close();
                            return;
                        } else {
                            client = getExistingClientOrCreateNew(out, in, aesDecrypting, aesEncrypting, initialIdentification);
                        }
                        findTaskAndGiveItToClient(guiToRefresh, acceptedSocket, out, in, aesDecrypting, aesEncrypting, client);

                    } catch (IOException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                             NoSuchAlgorithmException | BadPaddingException e) {
                        System.out.println("Exception occured!! " + e.getMessage());
                        Platform.runLater(guiToRefresh::refreshTaskList);
                        throw new RuntimeException(e);
                    }
                });
            }
        });
    }

    private void failTaskIfPresent(InputStream in, Cipher aesDecrypting, Client client) throws IOException, IllegalBlockSizeException, BadPaddingException {
        String taskId = readEncryptedString(aesDecrypting, in);
        Optional<Task> optTask = client.getWorkingOnTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findAny();
        if (optTask.isPresent()) {
            Task task = optTask.get();
            failTask(client, task);
        }
    }

    private void failTask(Client client, Task task) {
        task.setStatus(TaskStatus.FAILED);
        transferTaskToHistoryRecords(task);
        client.getWorkingOnTasks().remove(task);
    }

    private void findTaskAndGiveItToClient(ApplicationController guiToRefresh, Socket acceptedSocket, DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, Client client) {
        Task givenTask = null;
        try {
            //mutex??
            givenTask = selectTaskAndRegisterIt(client, guiToRefresh);
            //no task? return
            if (givenTask == null) {
                sendEncryptedMessage(aesEncrypting, out, "NO_TASK".getBytes(StandardCharsets.UTF_8));
                client.setLastReply(new Date());
                return;
            } else {
                sendEncryptedMessage(aesEncrypting, out, String.format("%s;%d", givenTask.getId(), givenTask.getCost()).getBytes(StandardCharsets.UTF_8));
                //check response for cost status
                String response = readEncryptedString(aesDecrypting, in);
                if (response.equals("COST_TOO_HIGH")) {
                    rescheduleTask(acceptedSocket, client, givenTask);
                    return;
                } else {
                    removeFromFifo(givenTask);
                    client.setAvailableResources(Integer.parseInt(response));
                }
            }
            client.setLastReply(new Date());
            sendZipOfTask(out, aesEncrypting, givenTask);
            setTaskTimeout(givenTask);
            client.setLastReply(new Date());
            Platform.runLater(guiToRefresh::refreshTaskList);
            acceptedSocket.close();
        } catch (Exception e) {
            if (givenTask != null) {
                //Schedule task again
                givenTask.setStatus(TaskStatus.SCHEDULED);
                givenTask.getClient().getWorkingOnTasks().remove(givenTask);
                givenTask.setClient(null);
                if (!givenTask.getQueue().getTaskSchedulingQueue().contains(givenTask)) {
                    transferTaskToSchedulingQueue(givenTask);
                }
                Platform.runLater(guiToRefresh::refreshTaskList);
            }
        }
    }

    private static void removeFromFifo(Task givenTask) {
        if (givenTask.getQueue().getPlanningMode() == PlanningMode.FIFO) {
            givenTask.getQueue().getTaskFIFO().remove(givenTask);
            givenTask.getQueue().getNonScheduledTasks().add(givenTask);
        }
    }

    private static void setTaskTimeout(Task givenTask) {
        givenTask.setStartRunningTime(new Date());
        givenTask.setTimeoutDeadline(new Date(givenTask.getStartRunningTime().getTime() + givenTask.getTimeoutInMillis()));
    }

    private static void rescheduleTask(Socket acceptedSocket, Client client, Task givenTask) throws IOException {
        client.getWorkingOnTasks().remove(givenTask);
        givenTask.setClient(null);
        givenTask.setStatus(TaskStatus.SCHEDULED);
        givenTask.setStartRunningTime(null);
        givenTask.setTimeoutDeadline(null);
        if (givenTask.getQueue().getPlanningMode() == PlanningMode.PRIORITY_QUEUE) {
            transferTaskToSchedulingQueue(givenTask);
        }
        acceptedSocket.close();
        client.setLastReply(new Date());
    }

    public static void transferTaskToSchedulingQueue(Task givenTask) {
        try {
            givenTask.getQueue().getNonScheduledTasks().remove(givenTask);
            givenTask.getQueue().getTaskSchedulingQueue().add(givenTask);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private void findAndReceiveTask(ApplicationController guiToRefresh, Socket acceptedSocket, DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, Client client) throws IOException, IllegalBlockSizeException, BadPaddingException {
        String[] taskMessage = readEncryptedString(aesDecrypting, in).split(";");
        String taskId = taskMessage[0];
        TaskStatus status = TaskStatus.valueOf(taskMessage[1]);
        Optional<Task> optTask = client.getWorkingOnTasks().stream()
                .filter(t -> t.getId().equals(taskId))
                .findAny();
        //System.out.println("receiving task results... id: " + taskId);
        Task task = receiveResultWithRetry(acceptedSocket, out, in, aesDecrypting, aesEncrypting, optTask, status);
        if (task != null) {
            //  if (task.getStatus() != TaskStatus.FAILED) {
            Persistence.cleanUp(task);
            //  }
            transferTaskToHistoryRecords(task);
            client.getWorkingOnTasks().remove(task);
        }
        Platform.runLater(guiToRefresh::refreshTaskList);
        acceptedSocket.close();
        client.setLastReply(new Date());
    }

    private Task receiveResultWithRetry(Socket socket, DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, Optional<Task> optTask, TaskStatus statusOnClient) throws IllegalBlockSizeException, BadPaddingException, IOException {
        if (optTask.isPresent()) {

            Task relatedTask = optTask.get();
            sendEncryptedMessage(aesEncrypting, out, "FOUND".getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < Scheduler.NUMBER_OF_RETRY; i++) {
                boolean gotResults = receiveTaskResults(aesDecrypting, in, relatedTask);
                if (gotResults) {
                    if (statusOnClient == TaskStatus.WARNING) {
                        relatedTask.setStatus(TaskStatus.WARNING);
                    } else if (statusOnClient == TaskStatus.IN_TRANSFER) {
                        relatedTask.setStatus(TaskStatus.FINISHED);
                    }
                    //send message to client
                    sendEncryptedMessage(aesEncrypting, out, "FINISHED".getBytes(StandardCharsets.UTF_8));
                    return relatedTask;
                } else if (i < Scheduler.NUMBER_OF_RETRY - 1) {
                    sendEncryptedMessage(aesEncrypting, out, "FAILED".getBytes(StandardCharsets.UTF_8));
                } else {
                    sendEncryptedMessage(aesEncrypting, out, "FAILED_FINAL".getBytes(StandardCharsets.UTF_8));
                    relatedTask.setStatus(TaskStatus.FAILED);
                    return relatedTask;
                }
            }
        } else {
            //we did not find the task - return
            //System.out.println("no task found");
            sendEncryptedMessage(aesEncrypting, out, "NOT_FOUND".getBytes(StandardCharsets.UTF_8));
            socket.close();
            return null;
        }
        return null;
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

    private synchronized Client getExistingClientOrCreateNew(DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, String message) throws IllegalBlockSizeException, BadPaddingException, IOException {
        Client client;
        String[] parsedMessage = message.split(";");
        String id = parsedMessage[0];
        int availableResources = Integer.parseInt(parsedMessage[1]);

        client = clients.get(id);
        if (client == null) {
            client = reRegisterClient(out, in, aesDecrypting, aesEncrypting, parsedMessage, availableResources);
        } else {
            sendEncryptedMessage(aesEncrypting, out, "ACK".getBytes(StandardCharsets.UTF_8));
            client.setAvailableResources(availableResources);
        }
        client.setLastReply(new Date());
        return client;
    }

    private synchronized Client reRegisterClient(DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, String[] parsedMessage, int availableResources) throws IllegalBlockSizeException, BadPaddingException, IOException {
        String id;
        Client client;
        id = String.valueOf(UUID.randomUUID());
        //send id
        sendEncryptedMessage(aesEncrypting, out, id.getBytes(StandardCharsets.UTF_8));

        Agent agent = Agent.valueOf(parsedMessage[2]);
        //wait for queues
        List<String> queues = readEncryptedList(aesDecrypting, in);

        client = new Client(id, agent, availableResources, ClientStatus.ACTIVE, new Date(), new ArrayList<>(), queues);
        clients.put(id, client);
        return client;
    }

    private synchronized Client registerNewClient(DataOutputStream out, InputStream in, Cipher aesDecrypting, Cipher aesEncrypting, String message) throws IOException, IllegalBlockSizeException, BadPaddingException {
        Client client;
        String[] parsedMessage = message.split(";");

        Agent agent = Agent.valueOf(parsedMessage[0]);
        int availableResources = Integer.parseInt(parsedMessage[1]);
        String id = String.valueOf(UUID.randomUUID());
        List<String> queues = readEncryptedList(aesDecrypting, in);

        client = new Client(id, agent, availableResources, ClientStatus.ACTIVE, new Date(), Collections.synchronizedList(new ArrayList<>()), queues);
        clients.put(id, client);
        //send id to client
        sendEncryptedMessage(aesEncrypting, out, client.getId().getBytes(StandardCharsets.UTF_8));
        client.setLastReply(new Date());
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

    //NOTE - ITEMS FROM PRIORITY QUEUE ARE REMOVED FROM THE QUEUE, FROM FIFO, THEY ARE NOT!!! (because of replanning)
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
                filter((q) -> subscribedQueuesFinal.contains(q.getName()) && q.getAgents().contains(client.getAgent()) && !q.getTaskSchedulingQueue().isEmpty())
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
            List<Task> pickCandidates = new LinkedList<>();
            for (Queue queue : queuesWithSamePriority) {
                Task potencialTask = null;
                if (queue.getPlanningMode() == PlanningMode.PRIORITY_QUEUE) {
                    potencialTask = queue.getTaskPriorityQueue().peek();
                } else {
                    //else get task from fifo
                    int index = 0;
                    do {
                        Task iterated = queue.getTaskFIFO().get(index);
                        if (iterated.getStatus() != TaskStatus.RUNNING) {
                            potencialTask = iterated;
                        }
                        index++;
                    } while (potencialTask == null && index < queue.getTaskFIFO().size() && potencialTask.getStatus() == TaskStatus.RUNNING);

                }
                if (potencialTask != null && potencialTask.getCost() <= client.getAvailableResources()) {
                    pickCandidates.add(potencialTask);
                }
            }
            //if cost of items in these queues would be too high, continue with another queues
            if (pickCandidates.isEmpty()) {
                continue;
            }
            //get first element in sorted comparator according to comparator
            Task winningTask = pickCandidates.stream().sorted(Queue.TASK_COMPARATOR).findFirst().get();

            //remove candidate from priority queue
            Queue queueWithWinningTask = winningTask.getQueue();
            Task givenTask = null;
            if (queueWithWinningTask.getPlanningMode() == PlanningMode.PRIORITY_QUEUE) {
                givenTask = queueWithWinningTask.getTaskPriorityQueue().remove();
                winningTask.getQueue().getNonScheduledTasks().add(givenTask);
            } else {
                givenTask = queueWithWinningTask.getTaskFIFO().get(0);
            }

            givenTask.setStatus(TaskStatus.RUNNING);
            client.getWorkingOnTasks().add(givenTask);
            givenTask.setClient(client);
            Platform.runLater(guiToRefresh::refreshTaskList);

            return givenTask;
        }
        //we have no task
        return null;
    }

    private boolean receiveTaskResults(Cipher aesDecrypting, InputStream in, Task task) throws IOException, IllegalBlockSizeException, BadPaddingException {
        File resDir = new File(PATH_TO_TASK_RESULTS_STORAGE + task.getName());
        resDir.mkdirs();
        // we will get task - message it is id
        try (FileOutputStream fos = new FileOutputStream(resDir.getPath() + separator + task.getId() + ".zip")) {
            long receivedChunkSize = Long.parseLong(readEncryptedString(aesDecrypting, in));

            while (receivedChunkSize > 0) {
                //decrypt and write
                fos.write(aesDecrypting.doFinal(in.readNBytes((int) receivedChunkSize)));
                receivedChunkSize = Integer.parseInt(readEncryptedString(aesDecrypting, in));
            }
        }
        return true;
    }

    public void deleteQueueByName(String queueName) {
        Queue queue = queueMap.get(queueName);

        if (queue != null && queue.getTaskSchedulingQueue().isEmpty()) {
            queueMap.remove(queueName);
        }
    }

    public boolean retryTask(Task task) {
        if (task.getStatus() != TaskStatus.FAILED) {
            return false;
        }
        Queue queue = this.queueMap.get(task.getQueue().getName());
        if (queue == null) {
            return false;
        }
        int totalCount = queue.getTaskSchedulingQueue().size() + queue.getNonScheduledTasks().size();
        if (totalCount >= queue.getCapacity()) {
            //not enough space
            return false;
        }

        historicalTasks.remove(task);
        //in case queue got deleted while task was historical
        task.setQueue(queue);
        queue.getTaskSchedulingQueue().add(task);

        task.setStatus(TaskStatus.SCHEDULED);
        return true;
    }

    public long getClientTimeoutDeadline() {
        return clientTimeoutDeadline;
    }

    public void setClientTimeoutDeadline(long clientTimeoutDeadline) {
        this.clientTimeoutDeadline = clientTimeoutDeadline;
    }

    public long getClientNoResponseTime() {
        return clientNoResponseTime;
    }

    public void setClientNoResponseTime(long clientNoResponseTime) {
        this.clientNoResponseTime = clientNoResponseTime;
    }

    public long getTimeoutDelay() {
        return timeoutDelay;
    }

    public void setTimeoutDelay(long timeoutDelay) {
        this.timeoutDelay = timeoutDelay;
    }
}
