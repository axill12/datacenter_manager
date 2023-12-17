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

    /*If it is the first packet is true. Without the first two threads may enter both at if (timesOfArrivalOfPackets[0] == -1)
      because timesOfArrivalOfPackets[0] didn't have time to change.
    */
    private static boolean isFirstPacket = true;

    private static volatile boolean isInPacketsCounterLock = false;

    private static ReentrantLock packetsCounterLock = new ReentrantLock();

    private static Condition isPacketsCounterZero = packetsCounterLock.newCondition();

    /*These are the tokens are going to be used totally by two packets when they arrive with at most 10 millisecond difference.
    Without knowing the initial token's number the two packets should use totally, it would assign the half of tokens remained after first packet took the initial half tokens.
    Thus second packet would use the half of the half of initial tokens, while we want to take the half.
     */
    private static int tokensForTwoPackets;

    //This variable declares if it is the first of two requests that arrived with at most 10 milliseconds difference.
    private static boolean isFirstOfTwoPackets = true;

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
                        System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);

                        /*If isPacketsCounterZero.signal() exists without this loop sometimes this command is executed before isPacketsCounterZero.await().
                          When is executed this thread just continues, but the other stays stuck in lock, specifically in packetsCounterLock.lock().
                          This loop executes until packetsCounter[0] reach 2, which means the other thread executed increasePacketCounter().
                         */
                        while (isInPacketsCounterLock == false && packetsCounter[0] <= 2) {
                            System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock);
                            try {
                                isPacketsCounterZero.signal();
                                System.out.println (Thread.currentThread().threadId() + " just after isPacketsCounterZero.signal()");
                            } catch (IllegalMonitorStateException e) {
                                System.out.println ("isPacketsCounterZero.signal() was executed before a thread acquires the packetsCounterLock, but this thread can continue execute normally. The packetsCounterLock it is never going to be acquired.");
                            }
                            //Without break for some reason does not exit
                            if (packetsCounter[0] == 2) {
                                System.out.println(Thread.currentThread().threadId() + " in if (packetsCounter[0] == 2) just before break");
                                break;
                            }
                            System.out.println(Thread.currentThread().threadId() + " packetsCounter[0]: " + packetsCounter[0]);
                        }

                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                        sendRequest(6834, argumentForServer);
                        waitServerToFinishThisRequest();
                        changeNumberOfAvailableTokens(tokensWillBeUsed);
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
                        System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                        writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                        if (buckets[0] > 0) {
                            System.out.println (Thread.currentThread().threadId() + " in if if (buckets[0] > 0)");
                            sendRequest(6834, argumentForServer);
                            waitServerToFinishThisRequest();
                            changeNumberOfAvailableTokens(tokensWillBeUsed);
                            return;
                        }
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } //Some tokens should be free. Thus, they are assigned.
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                        sendRequest(6834, argumentForServer);
                        waitServerToFinishThisRequest();
                        changeNumberOfAvailableTokens(tokensWillBeUsed);
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
                    changeNumberOfAvailableTokens(-1 * tokensWillBeUsed);
                    System.out.println (Thread.currentThread().threadId() + " in if if " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
                } //If new packet arrived within 10 milliseconds assign half tokens to this packet.
                else {
                    tokensWillBeUsed = buckets[0] / 2;
                    tokensForTwoPackets = buckets[0];
                    changeIsFirstOfTwoPackets(true);
                    changeNumberOfAvailableTokens(-1 * tokensWillBeUsed);
                    System.out.println (Thread.currentThread().threadId() + " in if else " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
                }
            }
            else if (counterForThisPacket == packetsCounter[0]) {
                if (Math.abs(timesOfArrivalOfPackets[0] - timeOfArrivalOfThisPacket) > 10) {
                    System.out.println (Thread.currentThread().threadId() + " in else if (counterForThisPacket == packetsCounter[0]) in if");
                    tokensWillBeUsed = buckets[0];
                } else {
                    /*If no value is assigned to an int class variable is 0.
                          If it is the first ever packet that arrived if (tokensForTwoPackets == 0) is executed even if to tokensForTwoPackets never is assigned value.
                    */
                    if (tokensForTwoPackets == 0) {
                        System.out.println (Thread.currentThread().threadId() + " in if (tokensForTwoPackets == 0)");
                        tokensWillBeUsed = buckets[0] / 2;
                        tokensForTwoPackets = buckets[0];
                            /*It is assigned false,
                              so when next packet which will arrive at most 10 millisecond after this will check this variable it is going to have the proper value.
                            */
                        changeIsFirstOfTwoPackets(false);
                    } //If tokensForTwoPackets > 0
                    else {
                        if (isFirstOfTwoPackets) {
                            System.out.println (Thread.currentThread().threadId() + " in if (isFirstOfTwoPackets)");
                            tokensWillBeUsed = buckets[0] / 2;
                            tokensForTwoPackets = buckets[0];
                                /*It is assigned false,
                                  so when next packet which will arrive at most 10 millisecond after this will check this variable it is going to have the proper value.
                                */
                            changeIsFirstOfTwoPackets(false);
                        } //If isFirstOfTwoPackets is false
                        else {
                            //This if else is necessary because I do not know if tokensForTwoPackets is even or odd number.
                            if (tokensForTwoPackets % 2 == 1) {
                                tokensWillBeUsed = tokensForTwoPackets / 2 + 1;
                                System.out.println (Thread.currentThread().threadId() + " in if (tokensForTwoPackets % 2 == 1)");
                            } else {
                                tokensWillBeUsed = tokensForTwoPackets / 2;
                                System.out.println (Thread.currentThread().threadId() + " in else (if tokensForTwoPackets % 2 == 0)");
                            }
                                /*It is assigned true,
                                  so when next packet which will arrive will check this variable it is going to have the proper value.
                                */
                            changeIsFirstOfTwoPackets(true);
                        }
                    }
                }
                changeNumberOfAvailableTokens(-1 * tokensWillBeUsed);
                System.out.println (Thread.currentThread().threadId() + " in else if " + "tokens that are assigned: " + tokensWillBeUsed + " time: " + System.currentTimeMillis());
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

        //If it is used for binding tokens to a request tokensWillBeUsed should be the number of tokens that are necessary to bind with minus sign to subtract tokens.
        private static synchronized void changeNumberOfAvailableTokens (int tokensWillBeUsed) {
            buckets[0] += tokensWillBeUsed;
        }

        //Wait the interval server needs to finish the task this request asked server to do. I suppose arbitrarily this interval is 2500 ms for all requests in all servers.
        private static void waitServerToFinishThisRequest () {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                System.out.println ("Another thread interrupted this.");
            }
        }

        private static synchronized void changeIsFirstOfTwoPackets (boolean flag) {
            isFirstOfTwoPackets = flag;
        }

    }

}
