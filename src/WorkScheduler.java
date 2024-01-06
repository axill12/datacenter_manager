import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;
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

    /*If it is the first packet is true. Without it both the first two packets, if they arrive closely (if they are sent together) would enter in if (isFP),
      because timesOfArrivalOfPackets[serverCell] didn't have time to change and this could cause wrong results.
    */
    private static boolean isFirstPacket[] = new boolean [3];

    private static volatile boolean isInPacketsCounterLock[] = new boolean[3];

    private static ReentrantLock packetsCounterLock[] = new ReentrantLock[3];

    private static Condition isPacketsCounterZero[] = new Condition[3];

    public static void main (String args []) {
        for (int i=0; i<3; i++) {
            buckets[i] = 10;
            timesOfArrivalOfPackets[i] = -1;
            packetsCounter[i] = 0;
            isFirstPacket[i] = true;
            isInPacketsCounterLock[i] = false;
            packetsCounterLock[i] = new ReentrantLock();
            isPacketsCounterZero[i] = packetsCounterLock[i].newCondition();
        }

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

        private int serverCell;

        public Worker (Socket client) {
            this.client = client;
        }

        public void run () {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
                //It's the argument WorkScheduler is going to pass to one of servers that are in images.
                String argumentForServer = reader.readLine();
                //It's the server's name that is going to pass the argument it needs.
                String nameOfClassOfServer = reader.readLine();
                int port;
                if (nameOfClassOfServer.endsWith("1")) {
                    serverCell = 0;
                    port = 6834;
                } else if (nameOfClassOfServer.endsWith("2")) {
                    serverCell = 1;
                    port = 6835;
                } else {
                    serverCell = 2;
                    port = 6836;
                }

                //If there aren't tokens waits till some are free.
                /*if (buckets[serverCell] == 0) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        System.out.println ("Another thread interrupted this.");
                    }
                }*/
                int counterForThisPacket;
                long timeOfArrivalOfThisPacket;
                boolean isFP;
                int tokensWillBeUsed;

                synchronized (Worker.class) {
                    isFP = isFirstPacket[serverCell];
                    //It changes it, so the next packet will know it is not the first packet.
                    if (isFP) {
                        isFirstPacket[serverCell] = false;
                    }
                }

                //If it is the first packet it came to execute
                if (isFP) {
                    System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[serverCell] == -1");
                    timeOfArrivalOfThisPacket =  generateRandomNumber();
                    writeTimeOfArrivalOfNewPacket(timeOfArrivalOfThisPacket);
                    System.out.println (Thread.currentThread().threadId() + " " + timeOfArrivalOfThisPacket);
                    counterForThisPacket = increasePacketCounter();
                    System.out.println (Thread.currentThread().threadId() + " counterForThisPacket: " + counterForThisPacket);

                        /*If isPacketsCounterZero.signal() exists without this loop sometimes this command is executed before isPacketsCounterZero.await().
                          When is executed this thread just continues, but the other stays stuck in lock, specifically in packetsCounterLock.lock().
                          This loop executes until packetsCounter[serverCell] reach 2, which means the other thread executed increasePacketCounter().
                         */
                    while (isInPacketsCounterLock[serverCell] == false && packetsCounter[serverCell] <= 2) {
                        System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock[serverCell]);
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

                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (packetsCounter[serverCell] == counterForThisPacket && timeOfArrivalOfThisPacket == timesOfArrivalOfPackets[serverCell]) {
                        System.out.println (Thread.currentThread().threadId() + " in if (packetsCounter[serverCell] == counterForThisPacket && timeOfArrivalOfThisPacket == timesOfArrivalOfPackets[serverCell])");
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                    sendRequest(port, argumentForServer);
                    waitServerToFinishThisRequest();
                    changeNumberOfAvailableTokens( tokensWillBeUsed);
                } else {
                    System.out.println (Thread.currentThread().threadId() + " in timesOfArrivalOfPackets[serverCell] > -1");
                    timeOfArrivalOfThisPacket = generateRandomNumber();
                    System.out.println (Thread.currentThread().threadId() + " " + timeOfArrivalOfThisPacket);
                        /*If lock and condition are not used, then the second thread that serves the second request,
                          reaches first the line counterForThisPacket = increasePacketCounter();, packetsCounter is still 0 and increases to 1.
                          Thus second thread's counterForThisPacket equals 1 and first's counterForThisPacket equals 2,
                          because it increases after second thread's, which is abnormal.
                        */
                    if (packetsCounter[serverCell] == 0) {
                        try {
                            isInPacketsCounterLock[serverCell] = true;
                            System.out.println (Thread.currentThread().threadId() + " isInPacketsCounterLock == " + isInPacketsCounterLock[serverCell]);
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
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (packetsCounter[serverCell] == counterForThisPacket && timeOfArrivalOfThisPacket == timesOfArrivalOfPackets[serverCell]) {
                        System.out.println (Thread.currentThread().threadId() + " in if (packetsCounter[serverCell] == counterForThisPacket && timeOfArrivalOfThisPacket == timesOfArrivalOfPackets[serverCell])");
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
                        tokensWillBeUsed = assignTokens(timeOfArrivalOfThisPacket, counterForThisPacket);
                    } while (tokensWillBeUsed == 0);
                    sendRequest(port, argumentForServer);
                    waitServerToFinishThisRequest();
                    changeNumberOfAvailableTokens(tokensWillBeUsed);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private int assignTokens (long timeOfArrivalOfThisPacket, int counterForThisPacket) {
            synchronized (Worker.class) {
                long tap = timesOfArrivalOfPackets[serverCell];
                System.out.println(Thread.currentThread().threadId() + " timesOfArrivalOfPackets[serverCell]: " + timesOfArrivalOfPackets[serverCell] + " timeOfArrivalOfThisPacket: " + timeOfArrivalOfThisPacket);
                int tokensWillBeUsed;
                if (timeOfArrivalOfThisPacket == tap) {
                    if (counterForThisPacket == packetsCounter[serverCell]) {
                        tokensWillBeUsed = buckets[serverCell];
                        System.out.println(Thread.currentThread().threadId() + " in else if (counterForThisPacket == packetsCounter[serverCell]) tokens assigned: " + tokensWillBeUsed);
                    } //if counterForThisPacket + 1 == packetsCounter[serverCell]
                    else {
                        tokensWillBeUsed = buckets[serverCell] / 2;
                        System.out.println(Thread.currentThread().threadId() + " in else (counterForThisPacket + 1 == packetsCounter[serverCell]) tokens assigned: " + tokensWillBeUsed);
                    }
                } // else if timeOfArrivalOfThisPacket != tap
                else {
                    tokensWillBeUsed = buckets[serverCell];
                    System.out.println(Thread.currentThread().threadId() + " in else tokens assigned: " + tokensWillBeUsed);
                }
                changeNumberOfAvailableTokens(-1 * tokensWillBeUsed);
                return tokensWillBeUsed;
            }
        }

        /*Return packetsCounter[index] a method variable can store this value.
         If it didn't return it, packetsCounter[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private int increasePacketCounter () {
            synchronized (Worker.class) {
                return ++packetsCounter[serverCell];
            }
        }

        /*Return timesOfArrivalOfPackets[index] a method variable can store this value.
         If it didn't return it, timesOfArrivalOfPackets[cell] could be changed by another thread and method variable wouldn't store wright value.
         */
        private void writeTimeOfArrivalOfNewPacket (long timeOfArrivalOfThisPacket) {
            synchronized (Worker.class) {
                timesOfArrivalOfPackets[serverCell] = timeOfArrivalOfThisPacket;
            }
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
        private void changeNumberOfAvailableTokens (int tokens) {
            synchronized (Worker.class) {
                buckets[serverCell] += tokens;
            }
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
    }

}