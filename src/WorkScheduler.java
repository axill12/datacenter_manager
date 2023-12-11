import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
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
    private static long packetsCounter[] = new long [3];

    /*If all tokens of a server are assigned to this request this variable is true.
          If all tokens are assigned and run's thread didn't wait to check if another request will come in short interval,
          thread should wait this short interval and then send request to server. That is why is necessary to know if all tokens are assigned.
        */
    private static boolean areAllTokensAssigned;

    /*If it is the first packet is true. Without the first two threads may enter both at if (timesOfArrivalOfPackets[0] == -1)
      because timesOfArrivalOfPackets[0] didn't have time to change.
    */
    private static boolean isFirstPacket = true;

    private static boolean isInPacketsCounterLock = false;

    private static ReentrantLock packetsCounterLock = new ReentrantLock();

    private static Condition isPacketsCounterZero = packetsCounterLock.newCondition();

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
                    long counterForThisPacket;
                    long timeOfArrivalOfThisPacket;
                    int tokensWillBeUsed = 0;
                    boolean isFP;

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
                        timeOfArrivalOfThisPacket = writeTimeOfArrivalOfNewPacket(System.currentTimeMillis());
                        System.out.println (Thread.currentThread().threadId() + " " + timeOfArrivalOfThisPacket);
                        counterForThisPacket = increasePacketCounter();
                        System.out.println (Thread.currentThread().threadId() + " counter for this packet: " + counterForThisPacket);

                        /*If isPacketsCounterZero.signal() exists without this loop sometimes this command is executed before isPacketsCounterZero.await().
                          When is executed this thread just continues, but the other stays stuck in lock, specifically in packetsCounterLock.lock().
                          This loop executes until packetsCounter[0] reach 2, which means the other thread executed increasePacketCounter().
                         */
                        while (isInPacketsCounterLock == false && packetsCounter[0] == 2) {
                            System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock);
                            try {
                                isPacketsCounterZero.signal();
                                System.out.println (Thread.currentThread().threadId() + " just after isPacketsCounterZero.signal()");
                            } catch (IllegalMonitorStateException e) {
                                System.out.println ("isPacketsCounterZero.signal() was executed before a thread acquires the packetsCounterLock, but this thread can continue execute normally. The packetsCounterLock it is never going to be acquired.");
                            }
                        }

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                        sendRequest(6834, argumentForServer);
                        waitServerToFinishThisRequest();
                        releaseTokens(tokensWillBeUsed);
                    } else {
                        System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[0] > -1");
                        timeOfArrivalOfThisPacket = System.currentTimeMillis();
                        System.out.println (Thread.currentThread().threadId() + " " + timeOfArrivalOfThisPacket);
                        /*If lock and condition are not used, then the second thread that serves the second request,
                          reaches first the line counterForThisPacket = increasePacketCounter();, packetsCounter is still 0 and increases to 1.
                          Thus second thread's counterForThisPacket equals 1 and first's counterForThisPacket equals 2,
                          because it increases after second thread's, which is abnormal.
                        */
                        if (packetsCounter[0] == 0) {
                            try {
                                isInPacketsCounterLock = true;
                                System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock);
                                packetsCounterLock.lock();
                                while (packetsCounter[0] == 0) {
                                    System.out.println (Thread.currentThread().threadId() + " In while in lock");
                                    isPacketsCounterZero.await();
                                }
                            } catch(InterruptedException e) {
                                e.printStackTrace();
                            } finally {
                                try {
                                    packetsCounterLock.unlock();
                                } catch (IllegalMonitorStateException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        counterForThisPacket = increasePacketCounter();
                        System.out.println (Thread.currentThread().threadId() + " counter for this packet: " + counterForThisPacket);
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                        writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                        if (!areAllTokensAssigned) {
                            System.out.println (Thread.currentThread().threadId() + " in if (!areAllTokensAssigned)");
                            sendRequest(6834, argumentForServer);
                            waitServerToFinishThisRequest();
                            releaseTokens(tokensWillBeUsed);
                            return;
                        }
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                        sendRequest(6834, argumentForServer);
                        waitServerToFinishThisRequest();
                        releaseTokens(tokensWillBeUsed);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private int assignTokens (long timeOfArrivalOfThisPacket, long counterForThisPacket) {
            int tokensWillBeUsed;
            if (counterForThisPacket < packetsCounter[0]) {
                if (Math.abs(timesOfArrivalOfPackets[0] - timeOfArrivalOfThisPacket) > 10) {
                    tokensWillBeUsed = buckets[0];
                    reduceTokens(tokensWillBeUsed);
                    areAllTokensAssigned = true;
                    System.out.println (Thread.currentThread().threadId() + " in if if " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
                } //If new packet arrived within 10 milliseconds assign half tokens to this packet.
                else {
                    tokensWillBeUsed = buckets[0] / 2;
                    reduceTokens(tokensWillBeUsed);
                    areAllTokensAssigned = false;
                    System.out.println (Thread.currentThread().threadId() + " in if else " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
                }
            } /*If no new packet came this block is executed.
                It doesn't matter if the previous packet arrived within 10 milliseconds before this packet arrived,
                anyway it is going to assign to buckets[0] all available tokens,
                because if previous packet arrived within 10 milliseconds before this packet arrived it took already its tokens.
                */
            else if (counterForThisPacket == packetsCounter[0]) {
                if (Math.abs(timesOfArrivalOfPackets[0] - timeOfArrivalOfThisPacket) > 10) {
                    tokensWillBeUsed = buckets[0];
                    reduceTokens(tokensWillBeUsed);
                    areAllTokensAssigned = true;
                    System.out.println (Thread.currentThread().threadId() + " in else if " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
                } else {
                    tokensWillBeUsed = buckets[0] / 2;
                    reduceTokens(tokensWillBeUsed);
                    areAllTokensAssigned = true;
                    System.out.println (Thread.currentThread().threadId() + " in else if " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
                }

            } //This block can not be reached. I just write it because otherwise return statement prompts the error; "might not have been initialized".
            else {
                System.out.println(Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket + " packetsCounter[0]: " + packetsCounter[0]);
                tokensWillBeUsed = 0;
                System.out.println(Thread.currentThread().threadId() + " in else");
            }
            return tokensWillBeUsed;
        }

        /*Return packetsCounter[index] a method variable can store this value.
         If it didn't return it, packetsCounter[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private static synchronized long increasePacketCounter () {
            return ++packetsCounter[0];
        }

        /*Return timesOfArrivalOfPackets[index] a method variable can store this value.
         If it didn't return it, timesOfArrivalOfPackets[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private static synchronized long writeTimeOfArrivalOfNewPacket (long timeOfArrivalOfThisPacket) {
            timesOfArrivalOfPackets[0] = timeOfArrivalOfThisPacket;
            return timesOfArrivalOfPackets[0];
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

        private static void releaseTokens (int tokensWillBeUsed) {
            synchronized (Worker.class) {
                buckets[0] += tokensWillBeUsed;
            }
            areAllTokensAssigned = false;
        }

        //Wait the interval server needs to finish the task this request asked server to do. I suppose arbitrarily this interval is 2500 ms for all requests in all servers.
        private static void waitServerToFinishThisRequest () {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                System.out.println ("Another thread interrupted this.");
            }
        }

        private static synchronized void reduceTokens (int tokensWillBeUsed) {
            buckets[0] -= tokensWillBeUsed;
        }

    }

}
