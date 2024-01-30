import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WorkScheduler {

    /*The first cell of array represents the current number of tokens Server1's bucket has.
      The second cell represents the current number of tokens Server2's bucket has.
      The third is for Server2.
    */
    private static int buckets [] = new int [7];

    /*Here are stored the arrival times of last packets which arrived for each server.
    The first cell hold times for Server1, the second for Server2 and the third for Server3.
    When WorkScheduler starts assigns -1 to all cells in main method,
    so if scheduler in run method notice a cell contains -1 it knows no packet for the corresponding server has written its arrival time.
     */
    private static long timesOfArrivalOfPackets [] = new long [7];

    /*Counts the packets arrived per server because if subtraction is zero,
    it may be because no packet arrived and the last request subtract its arrival time with its arrival time.
     */
    private static int packetsCounter[] = new int [7];

    /*If it is the first packet is true. Without the first two threads may enter both at if (timesOfArrivalOfPackets[serverCell] == -1)
      because timesOfArrivalOfPackets[serverCell] didn't have time to change.
    */
    private static boolean isFirstPacket[] = new boolean [7];

    private static volatile boolean isInPacketsCounterLock[] = new boolean[7];

    private static ReentrantLock packetsCounterLock[] = new ReentrantLock[7];

    private static Condition isPacketsCounterZero[] = new Condition[7];

    //Its first value is zero because I did not assign it a value.
    private static int totalWorkOfRequests[] = new int [7];

    /*This variable contains the last time method generateRandomNumber executed.
      If the previous bundle's last request had the same arrival time with the first request of the next bundle
      the thread of first request of new bundle would assume the lats request of previous bundle belongs to his bundle,
      because they would have the same arrival time. Thus, this variable is necessary only because of the dummy way arrival times are produced.
    */
    private static long execTimeOfGenerator[] = new long [7];

    /*Each server in datacenter, which hosts applications, can distribute a specific number of tokens to applications it houses.
          When a new application is hosted totalAvailableTokens are reduced, depending on its Service Level Objective (SLO).
        */
    private static int totalAvailableTokens = 50;

    public static int getTotalAvailableTokens() {
        return totalAvailableTokens;
    }

    public static void setTotalAvailableTokens(int totalAvailableTokens) {
        WorkScheduler.totalAvailableTokens = totalAvailableTokens;
    }

    private static File bucketsFile = new File ("buckets.txt");

    public static void main (String args []) {

        for (int i=0; i<7; i++) {
            buckets[i] = -1;
            timesOfArrivalOfPackets[i] = 0;
            packetsCounter[i] = 0;
            isFirstPacket[i] = true;
            isInPacketsCounterLock[i] = false;
            packetsCounterLock[i] = new ReentrantLock();
            isPacketsCounterZero[i] = packetsCounterLock[i].newCondition();
            execTimeOfGenerator[i] = 0;
        }

        WorkerForServers workerForServers = new WorkerForServers();
        new Thread(workerForServers).start();

        try (ServerSocket server = new ServerSocket((7169))) {
            while (true) {
                //Listens to requests for connection from client.
                Socket client = server.accept();
                WorkerForRequests workerForRequests = new WorkerForRequests(client);
                new Thread(workerForRequests).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class WorkerForRequests implements Runnable {

        private final Socket client;

        private int serverCell;

        public WorkerForRequests (Socket client) {
            this.client = client;
        }

        public void run () {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                //It's the argument WorkScheduler is going to pass to one of servers that are in images.
                String argumentForServer = reader.readLine();
                int work;
                try {
                    work = Integer.parseInt (argumentForServer);
                } catch (NumberFormatException e) {
                    System.err.println("The argument for server is not valid, because is not number. The request will not be sent to server. Thread " + Thread.currentThread().threadId() + " terminates.");
                    return;
                }

                String idOfServer = reader.readLine();
                try {
                    serverCell = Integer.parseInt(idOfServer) - 1;
                } catch (NumberFormatException nfe) {
                    System.out.println("The id of the server you provided, it is not number. This server does not exist. This request is not going to be sent to any server.");
                    return;
                }

                Scanner fileReader = new Scanner(bucketsFile);
                String line;
                while (fileReader.hasNextLine()) {
                    line = fileReader.nextLine();
                    if (line.startsWith(idOfServer)) {
                        break;
                    }
                }

                System.out.println ("buckets[" + serverCell + "]: " + buckets[serverCell]);
                setTotalWorkOfRequests (work);

                int counterForThisPacket;
                long timeOfArrivalOfThisPacket;
                    /*If two packets arrive at same moment it is true, if it is false may one packet arrived and the last which wrote in timesOfArrivalOfPackets[cell] is itself.
                      In this case it would subtract its arrival time with timesOfArrivalOfPackets[cell] and it would calculate zero.
                      Thus, it would execute wrong piece of code without this boolean variable.
                     */
                boolean isFP;
                int tokensWillBeUsed;

                synchronized (WorkerForRequests.class) {
                    isFP = isFirstPacket[serverCell];
                    //It changes it, so the next packet will know it is not the first packet.
                    if (isFP) {
                        isFirstPacket[serverCell] = false;
                    }
                }

                //If it is the first packet it came to execute
                if (isFP) {
                    System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[serverCell] == 0");
                    timeOfArrivalOfThisPacket =  4596L;
                    writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                    System.out.println (Thread.currentThread().threadId() + " timeOfArrivalOfThisPacket: " + timeOfArrivalOfThisPacket);
                    counterForThisPacket = increasePacketCounter();
                    System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);

                        /*If isPacketsCounterZero.signal() exists without this loop sometimes this command is executed before isPacketsCounterZero.await().
                          When is executed this thread just continues, but the other stays stuck in lock, specifically in packetsCounterLock.lock().
                          This loop executes until packetsCounter[serverCell] reach 2, which means the other thread executed increasePacketCounter().
                         */
                    while (isInPacketsCounterLock[serverCell] == false && packetsCounter[serverCell] <= 2) {
                        System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock);
                        try {
                            isPacketsCounterZero[serverCell].signal();
                            System.out.println (Thread.currentThread().threadId() + " just after isPacketsCounterZero.signal()");
                        } catch (IllegalMonitorStateException e) {
                            System.out.println ("isPacketsCounterZero.signal() was executed before a thread acquires the packetsCounterLock, but this thread can continue execute normally. The packetsCounterLock it is never going to be acquired.");
                        }
                        //Without break for some reason does not exit
                        if (packetsCounter[serverCell] == 2) {
                            System.out.println(Thread.currentThread().threadId() + " in if (packetsCounter[serverCell] == 2) just before break");
                            break;
                        }
                        System.out.println(Thread.currentThread().threadId() + " packetsCounter[serverCell]: " + packetsCounter[serverCell]);
                    }

                    waitIfNecessary(counterForThisPacket, timeOfArrivalOfThisPacket);
                    //Invalid value is passed for arrivalTimeOfPreviousRequest, because no request came before.
                    tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, -1, work);
                    sendRequest(6834 + serverCell, argumentForServer);
                    waitServerToFinishThisRequest(work);
                    changeNumberOfAvailableTokens (tokensWillBeUsed);
                } else {
                    System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[serverCell] > 0");
                    timeOfArrivalOfThisPacket = 4596L;
                    System.out.println (Thread.currentThread().threadId() + " timeOfArrivalOfThisPacket: " + timeOfArrivalOfThisPacket);
                    long arrivalTimeOfPreviousRequest = timesOfArrivalOfPackets[serverCell];
                    /*If lock and condition are not used, then the second thread that serves the second request,
                      reaches first the line counterForThisPacket = increasePacketCounter();, packetsCounter is still 0 and increases to 1.
                      Thus second thread's counterForThisPacket equals 1 and first's counterForThisPacket equals 2,
                      because it increases after second thread's, which is abnormal.
                    */
                    if (packetsCounter[serverCell] == 0) {
                        try {
                            isInPacketsCounterLock[serverCell] = true;
                            System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock);
                            packetsCounterLock[serverCell].lock();
                            while (packetsCounter[serverCell] == 0) {
                                System.out.println (Thread.currentThread().threadId() + " In while in lock");
                                isPacketsCounterZero[serverCell].await();
                            }
                        } catch(InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                packetsCounterLock[serverCell].unlock();
                            } catch (IllegalMonitorStateException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    counterForThisPacket = increasePacketCounter();
                    System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);
                    writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                    waitIfNecessary(counterForThisPacket, timeOfArrivalOfThisPacket);

                    int i = 0;
                    while (buckets[serverCell] == 0) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        i++;
                        if (i == 1) {
                            System.out.println (Thread.currentThread().threadId() + " in while (buckets[serverCell] == 0)");
                        }
                        System.out.println(Thread.currentThread().threadId() + " in while (buckets[serverCell] == 0) buckets[serverCell]: " + buckets[serverCell]);
                    }
                    tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, arrivalTimeOfPreviousRequest, work);
                    sendRequest(6834 + serverCell, argumentForServer);
                    waitServerToFinishThisRequest(work);
                    changeNumberOfAvailableTokens(tokensWillBeUsed);
                }

            } catch (FileNotFoundException fnfe) {
                System.out.println ("Could not find buckets.txt.");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    client.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }

        private int assignTokens (long timeOfArrivalOfThisPacket, long arrivalTimeOfPreviousRequest, int work) {
            synchronized (WorkerForRequests.class) {
                long tap = timesOfArrivalOfPackets[serverCell];
                System.out.println(Thread.currentThread().threadId() + " timesOfArrivalOfPackets[serverCell]: " + timesOfArrivalOfPackets[serverCell] + " timeOfArrivalOfThisPacket: " + timeOfArrivalOfThisPacket);
                int tokensWillBeUsed;
                if (timeOfArrivalOfThisPacket == tap || timeOfArrivalOfThisPacket == arrivalTimeOfPreviousRequest) {
                    //If (work / (double) totalWorkOfTwoRequests[serverCell]) < 0.1 it would assign 0 tokens, due to use of (int) to calculation of tokensWillBeUsed.
                    if ((work / (double) totalWorkOfRequests[serverCell]) < 0.1) {
                        tokensWillBeUsed = 1;
                        System.out.println(Thread.currentThread().threadId() + " in if ((work / (double) totalWorkOfTwoRequests) < 0.1) tokens assigned: " + tokensWillBeUsed + " work: " + work + " totalWorkOfTwoRequests[serverCell]: " + totalWorkOfRequests[serverCell]);
                    } else {
                        //Multiplication is done with buckets[serverCell] instead of 10 for the purpose of assigning the right amount of tokens if buckets[serverCell] < 10.
                        tokensWillBeUsed = (int) ((work / (double) totalWorkOfRequests[serverCell]) * buckets[serverCell]);
                        /*Sometimes when a thread (not the first) executes (work / (double) totalWorkOfRequests[serverCell]) * buckets[serverCell] is less than 1,
                          because work is subtracted from totalWorkOfRequests[serverCell] by the previous threads,
                          so (work / (double) totalWorkOfRequests[serverCell]) * buckets[serverCell] is not completely accurate.
                          In this block value 1 is given to tokensWillBeUsed.
                        */
                        if (tokensWillBeUsed == 0) {
                            tokensWillBeUsed = 1;
                            System.out.println(Thread.currentThread().threadId() + "in in if ((work / (double) totalWorkOfTwoRequests) >= 0.1) in if (tokensWillBeUsed == 0)");
                        }
                        System.out.println(Thread.currentThread().threadId() + " in if ((work / (double) totalWorkOfTwoRequests) >= 0.1) tokens assigned: " + tokensWillBeUsed + " work: " + work + " totalWorkOfTwoRequests[serverCell]: " + totalWorkOfRequests[serverCell]);
                    }
                    /*If (tokensWillBeUsed > buckets[serverCell]) correct tokensWillBeUsed.
                      This exists because (work / (double) totalWorkOfRequests[serverCell]) * buckets[serverCell] becomes slightly different
                      when another thread subtracts its work from totalWorkOfRequests[serverCell]
                      than (work / (double) totalWorkOfRequests[serverCell]) * 10 when work of other thread is not subtracted.
                    */
                    if (tokensWillBeUsed > buckets[serverCell]) {
                        while (buckets[serverCell] == 0) {
                            tokensWillBeUsed = buckets[serverCell];
                        }
                        System.out.println(Thread.currentThread().threadId() + " in if (tokensWillBeUsed > buckets[serverCell]) tokens assigned: " + tokensWillBeUsed + " work: " + work + " totalWorkOfTwoRequests[serverCell]: " + totalWorkOfRequests[serverCell]);
                    }
                } // else if timeOfArrivalOfThisPacket != tap
                else {
                    tokensWillBeUsed = buckets[serverCell];
                    System.out.println(Thread.currentThread().threadId() + " in else tokens assigned: " + tokensWillBeUsed);
                }
                setTotalWorkOfRequests(-1 * work);
                changeNumberOfAvailableTokens(-1 * tokensWillBeUsed);
                return tokensWillBeUsed;
            }

        }

        /*Return packetsCounter[index] a method variable can store this value.
         If it didn't return it, packetsCounter[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private int increasePacketCounter () {
            synchronized (WorkerForRequests.class) {
                return ++packetsCounter[serverCell];
            }
        }

        /*Return timesOfArrivalOfPackets[index] a method variable can store this value.
         If it didn't return it, timesOfArrivalOfPackets[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private void writeTimeOfArrivalOfNewPacket (long timeOfArrivalOfThisPacket) {
            synchronized (WorkerForRequests.class) {
                timesOfArrivalOfPackets[serverCell] = timeOfArrivalOfThisPacket;
            }
        }

        private static void sendRequest (int port, String argumentForServer) {
            System.out.println(Thread.currentThread().threadId() + " port: " + port);
            try (Socket socket = new Socket("localhost", port)) {
                PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
                writer.println(argumentForServer);
                writer.close();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //If it is used for binding tokens to a request tokensWillBeUsed should be the number of tokens that are necessary to bind with minus sign to subtract tokens.
        private void changeNumberOfAvailableTokens (int tokens) {
            synchronized (WorkerForRequests.class) {
                buckets[serverCell] += tokens;
            }
        }

        //Wait the interval server needs to finish the task this request asked server to do. I suppose arbitrarily this interval is 2500 ms for all requests in all servers.
        private static void waitServerToFinishThisRequest (int work) {
            try {
                Thread.sleep(work);
            } catch (InterruptedException e) {
                System.out.println ("Another thread interrupted this.");
            }
        }

        private long generateRandomNumber () {
            long executionTime = System.currentTimeMillis();
            if (executionTime - execTimeOfGenerator[serverCell] < 2000) {
                execTimeOfGenerator[serverCell] = executionTime;
                Random random = new Random();
                random.setSeed(System.currentTimeMillis());
                return Math.abs(random.nextLong()) % 2 + timesOfArrivalOfPackets[serverCell];
            } else {
                execTimeOfGenerator[serverCell] = executionTime;
                Random random = new Random();
                random.setSeed(System.currentTimeMillis());
                return Math.abs(random.nextLong()) % 2 + timesOfArrivalOfPackets[serverCell] + 1;
            }
        }

        public void setTotalWorkOfRequests(int work) {
            synchronized (WorkerForRequests.class) {
                if (work == 0) {
                    totalWorkOfRequests[serverCell] = 0;
                    return;
                }
                totalWorkOfRequests[serverCell] += work;
            }
        }

        private void waitIfNecessary (int counterForThisPacket, long timeOfArrivalOfThisPacket) {
            int availableTokens = buckets[serverCell];
            /*Many requests maybe come at the same moment.
              It is necessary their threads to add their work to totalWorkOfTwoRequests[serverCell] before this one assigns tokens to itself.
            */
            while (packetsCounter[serverCell] == counterForThisPacket || timeOfArrivalOfThisPacket == timesOfArrivalOfPackets[serverCell]) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                        /*Without availableTokens > buckets[serverCell] does not enter this if because packetsCounter[serverCell] becomes bigger than counterForThisPacket.
                          availableTokens > buckets[serverCell] means another thread used some tokens.
                        */
                if ((packetsCounter[serverCell] == counterForThisPacket || availableTokens > buckets[serverCell]) && timeOfArrivalOfThisPacket == timesOfArrivalOfPackets[serverCell]) {
                    try {
                        Thread.sleep(50);
                        //Without this the thread of the last request of a bundle and requests which come solo would stick to this loop.
                        break;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println (Thread.currentThread().threadId() + " in while in waitIfNecessary packetsCounter[serverCell]: " + packetsCounter[serverCell]);
            }
        }
    }

    private static class WorkerForServers implements Runnable {

        public void run () {
            Socket client;
            BufferedReader reader;
            int necessaryTokens, positionOfServer;
            try (ServerSocket server = new ServerSocket(7168)) {
                while (true) {
                    client = server.accept();
                    reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                    //Here should receive the number of initial tokens this bucket is going to have.
                    necessaryTokens = Integer.parseInt(reader.readLine());
                    //Here should receive the position of server in buckets array.
                    positionOfServer = Integer.parseInt(reader.readLine());
                    /*Server is set up.
                      Thus, no request should come yet and this thread is not going to alter the same cell concurrently with a request which needs to be served from this server.
                    */
                    buckets[positionOfServer] = necessaryTokens;
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (SecurityException se) {
                se.printStackTrace();
            } catch (IllegalArgumentException iae) {
                iae.printStackTrace();
            }
        }

    }

}