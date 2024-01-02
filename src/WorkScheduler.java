import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WorkScheduler {

    /*The first cell of array represents the current number of tokens Server1's bucket has.
      The second cell represents the current number of tokens Server2's bucket has.
      The third is for Server2.
    */
    private static int buckets [] = new int [3];

    /*Here are stored the arrival times of last packets which arrived for each server.
    The first cell hold times for Server1, the second for Server2 and the third for Server3.
    When WorkScheduler starts assigns -1 to all cells in main method,
    so if scheduler in run method notice a cell contains -1 it knows no packet for the corresponding server has written its arrival time.
     */
    private static long timesOfArrivalOfPackets [] = new long [3];

    /*Counts the packets arrived per server because if subtraction is zero,
    it may be because no packet arrived and the last request subtract its arrival time with its arrival time.
     */
    private static int packetsCounter[] = new int [3];

    /*If it is the first packet is true. Without the first two threads may enter both at if (timesOfArrivalOfPackets[0] == -1)
      because timesOfArrivalOfPackets[0] didn't have time to change.
    */
    private static boolean isFirstPacket = true;

    private static volatile boolean isInPacketsCounterLock = false;

    private static ReentrantLock packetsCounterLockIf = new ReentrantLock();

    private static ReentrantLock packetsCounterLockElse = new ReentrantLock();

    private static Condition packetsCounterCondIf = packetsCounterLockIf.newCondition();

    private static Condition packetsCounterCondElse = packetsCounterLockElse.newCondition();

    //Its first value is zero because I did not assign it a value.
    private static int totalWorkOfTwoRequests;

    public static void main (String args []) {
        buckets[0] = 10;
        buckets[1] = 10;
        buckets[2] = 10;

        timesOfArrivalOfPackets[0] = -1;
        timesOfArrivalOfPackets[1] = -1;
        timesOfArrivalOfPackets[2] = -1;

        packetsCounter[0] = 0;
        packetsCounter[1] = 0;
        packetsCounter[2] = 0;

        try (ServerSocket server = new ServerSocket((7169))) {
            while (true) {
                //Listens to requests for connection from client.
                Socket client = server.accept();
                Worker worker = new Worker(client);
                new Thread(worker).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Worker implements Runnable {

        private final Socket client;

        public Worker (Socket client) {
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
                setTotalWorkOfTwoRequests(totalWorkOfTwoRequests + work);
                //It's the server's name that is going to pass the argument it needs.
                String nameOfClassOfServer = reader.readLine();
                if (nameOfClassOfServer.equals("Server1")) {
                    //If there aren't tokens waits till some are free.
                    if (buckets[0] == 0) {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            System.out.println ("Another thread interrupted this.");
                        }
                    }
                    int counterForThisPacket;
                    long timeOfArrivalOfThisPacket;
                    /*If two packets arrive at same moment it is true, if it is false may one packet arrived and the last which wrote in timesOfArrivalOfPackets[cell] is itself.
                      In this case it would subtract its arrival time with timesOfArrivalOfPackets[cell] and it would calculate zero.
                      Thus, it would execute wrong piece of code without this boolean variable.
                     */
                    boolean isFP;
                    int tokensWillBeUsed;

                    synchronized (Worker.class) {
                        isFP = isFirstPacket;
                        //It changes it, so the next packet will know it is not the first packet.
                        if (isFP) {
                            isFirstPacket = false;
                        }
                    }

                    //If it is the first packet it came to execute
                    if (isFP) {
                        System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[0] == -1");
                        timeOfArrivalOfThisPacket =  generateRandomNumber();
                        writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                        System.out.println (Thread.currentThread().threadId() + " " + timeOfArrivalOfThisPacket);
                        counterForThisPacket = increasePacketCounter();
                        System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);

                        //Without this block the thread of the other request could have counterForThisPacket == 1 and this would cause problems.
                        try {
                            packetsCounterCondElse.signal();
                            System.out.println (Thread.currentThread().threadId() + " just after isPacketsCounterZero.signal()");
                        } catch (IllegalMonitorStateException e) {
                            System.out.println (Thread.currentThread().threadId() + " the other thread is not in its lock yet. This is the first calling of packetsCounterCondElse.signal().");
                        }
                        packetsCounterLockIf.lock();
                        try {
                            System.out.println(Thread.currentThread().threadId() + " is in packetsCounterLockIf");
                            packetsCounterCondIf.await(2000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            System.out.println(Thread.currentThread().threadId() + " is interrupted. It returns.");
                            return;
                        } finally {
                            packetsCounterLockIf.unlock();
                        }
                        try {
                            packetsCounterCondElse.signal();
                            System.out.println (Thread.currentThread().threadId() + " just after isPacketsCounterZero.signal()");
                        } catch (IllegalMonitorStateException e) {
                            System.out.println(Thread.currentThread().threadId() + " the other thread is not in its lock yet. This is the second calling of packetsCounterCondElse.signal().");
                        }

                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ie) {
                            ie.printStackTrace();
                        }
                        /*Without it sometimes the thread of the first request of a pair whose requests arrive at the same moment
                          enters if (counterForThisPacket == packetsCounter[0]) and takes all tokens.
                        */
                        if (packetsCounter[0] == counterForThisPacket) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket, work);
                        sendRequest(6834, argumentForServer);
                        waitServerToFinishThisRequest();
                        changeNumberOfAvailableTokens( tokensWillBeUsed);
                    } else {
                        System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[0] > -1");
                        timeOfArrivalOfThisPacket = generateRandomNumber();
                        System.out.println (Thread.currentThread().threadId() + " " + timeOfArrivalOfThisPacket);

                        ////Without this block the thread of the request in if (isFP) could have counterForThisPacket == 2 and this would cause problems.
                        if (packetsCounter[0] == 0) {
                            try {
                                packetsCounterCondIf.signal();
                            } catch (IllegalMonitorStateException e) {
                                System.out.println (Thread.currentThread().threadId() + " the other thread is not in its lock yet. This is the first calling of packetsCounterCondIf.signal().");
                            }
                            packetsCounterLockElse.lock();
                            try {
                                System.out.println(Thread.currentThread().threadId() + " is in packetsCounterLockElse");
                                packetsCounterCondElse.await(2000, TimeUnit.MILLISECONDS);
                            } catch (InterruptedException e) {
                                System.out.println(Thread.currentThread().threadId() + " is interrupted. It returns.");
                                return;
                            } finally {
                                packetsCounterLockElse.unlock();
                            }
                            try {
                                packetsCounterCondIf.signal();
                                System.out.println (Thread.currentThread().threadId() + " just after packetsCounterCondIf.signal()");
                            } catch (IllegalMonitorStateException e) {
                                System.out.println(Thread.currentThread().threadId() + " the other thread is not in its lock yet. This is the second calling of packetsCounterCondIf.signal().");
                            }
                        }
                        counterForThisPacket = increasePacketCounter();
                        System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);
                        writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        /*Without it sometimes the thread of the first request of a pair whose requests arrive at the same moment
                          enters if (counterForThisPacket == packetsCounter[0]) and takes all tokens.
                        */
                        if (packetsCounter[0] == counterForThisPacket) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        int i = 0;
                        do {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            i++;
                            if (i == 1) {
                                System.out.println (Thread.currentThread().threadId() + " in do while (tokens which will be used == 0)");
                            }
                            tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket, work);
                        } while (tokensWillBeUsed == 0);
                        sendRequest(6834, argumentForServer);
                        waitServerToFinishThisRequest();
                        changeNumberOfAvailableTokens(tokensWillBeUsed);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static synchronized int assignTokens (long timeOfArrivalOfThisPacket, int counterForThisPacket, int work) {
            long tap = timesOfArrivalOfPackets[0];
            System.out.println(Thread.currentThread().threadId() + " timesOfArrivalOfPackets[0]: " + timesOfArrivalOfPackets[0] + " timeOfArrivalOfThisPacket: " + timeOfArrivalOfThisPacket);
            int tokensWillBeUsed;
            if (timeOfArrivalOfThisPacket == tap) {
                if (counterForThisPacket == packetsCounter[0]) {
                    tokensWillBeUsed = buckets[0];
                    /*If two requests arrive at the same time, the second enters this if.
                      If it does not assign 0 to variable totalWorkOfTwoRequests may the requests of the next pair of requests
                      that will arrive at the same moment will not add their work to 0, because variable totalWorkOfTwoRequests is not 0.
                     */
                    setTotalWorkOfTwoRequests (0);
                    System.out.println(Thread.currentThread().threadId() + " in else if (counterForThisPacket == packetsCounter[0]) tokens assigned: " + tokensWillBeUsed);
                } //if counterForThisPacket + 1 == packetsCounter[0]
                else {
                    if ((work / (double) totalWorkOfTwoRequests) > 0.5) {
                        tokensWillBeUsed = (int) ((work / (double) totalWorkOfTwoRequests) * 10);
                        System.out.println(Thread.currentThread().threadId() + " in if ((work / (double) totalWorkOfTwoRequests) > 0.5) tokens assigned: " + tokensWillBeUsed);
                    } /*If work / (double) totalWorkOfTwoRequests < 0.1, (work / (double) totalWorkOfTwoRequests) * 10 < 1,
                        thus (int) (work / (double) totalWorkOfTwoRequests) is going to be 0 and this request is going to get zero 0.
                        So is not going to be executed. Thence if (work / (double) totalWorkOfTwoRequests) < 0.5,
                        (int) (work / (double) totalWorkOfTwoRequests) + 1 is assigned to tokensWillBeUsed.
                      */
                    else {
                        tokensWillBeUsed = (int) ((work / (double) totalWorkOfTwoRequests) * 10) + 1;
                        System.out.println(Thread.currentThread().threadId() + " in if (work / (double) totalWorkOfTwoRequests) <= 0.5 tokens assigned: " + tokensWillBeUsed);
                    }
                    setTotalWorkOfTwoRequests (0);
                }
            } // else if timeOfArrivalOfThisPacket != tap
            else {
                tokensWillBeUsed = buckets[0];
                System.out.println(Thread.currentThread().threadId() + " in else tokens assigned: " + tokensWillBeUsed);
            }
            changeNumberOfAvailableTokens(-1 * tokensWillBeUsed);
            return tokensWillBeUsed;
        }

        /*Return packetsCounter[index] a method variable can store this value.
         If it didn't return it, packetsCounter[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private static synchronized int increasePacketCounter () {
            return ++packetsCounter[0];
        }

        /*Return timesOfArrivalOfPackets[index] a method variable can store this value.
         If it didn't return it, timesOfArrivalOfPackets[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private static synchronized void writeTimeOfArrivalOfNewPacket (long timeOfArrivalOfThisPacket) {
            timesOfArrivalOfPackets[0] = timeOfArrivalOfThisPacket;
        }

        private static void sendRequest (int port, String argumentForServer) {
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
        private static synchronized void changeNumberOfAvailableTokens (int tokens) {
            buckets[0] += tokens;
        }

        //Wait the interval server needs to finish the task this request asked server to do. I suppose arbitrarily this interval is 2500 ms for all requests in all servers.
        private static void waitServerToFinishThisRequest () {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println ("Another thread interrupted this.");
            }
        }

        private static long generateRandomNumber () {
            Random random = new Random();
            random.setSeed(System.currentTimeMillis());
            return Math.abs(random.nextLong()) % 2 + 1703873804597L;
        }

        public static synchronized void setTotalWorkOfTwoRequests(int totalWorkOfTwoRequests) {
            WorkScheduler.totalWorkOfTwoRequests = totalWorkOfTwoRequests;
        }
    }

}