import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

public class WorkScheduler {

    /*The first cell of array represents the current number of tokens Server1's bucket has.
      The second cell represents the current number of tokens Server2's bucket has.
      The third is for Server2.
    */
    private static int buckets [] = new int [3];

    /*Here are stored the arrival times of last packets which arrived for each server.
    The first cell hold times for Server1, the second for Server2 and the third for Server3.
     */
    private static long timesOfArrivalOfPackets [] = new long [3];

    /*If it is the first of two packets which arrived with at most 10 milliseconds difference it is true, if it is the second one is false.
     */
    private static boolean arrivedFirst = true;

    /*Counts the packets arrived per server because if subtraction is zero,
    it may be because no packet arrived and the last request subtract its arrival time with its arrival time.
     */
    private static long packetsCounter[] = new long [3];

    public static void main (String args []) {
        buckets[0] = 10;
        buckets[1] = 10;
        buckets[2] = 10;

        timesOfArrivalOfPackets[0] = -1;
        timesOfArrivalOfPackets[1] = -1;
        timesOfArrivalOfPackets[2] = -1;

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

    public static class Worker implements Runnable {

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
                    int tokensWillBeUsed;
                    if (timesOfArrivalOfPackets[0] == -1) {
                        synchronized(Worker.class) {
                            //Writes the arrival time of this packet
                            timesOfArrivalOfPackets[0] = System.currentTimeMillis();
                            //Saves timesOfArrivalOfPackets[0] because if a new packet come it is going to be changed.
                            timeOfArrivalOfThisPacket = timesOfArrivalOfPackets[0];
                            packetsCounter[0] = 1;
                            //It needs to save it because some other thread may be created and increase packetsCounter[0]
                            counterForThisPacket = packetsCounter[0];
                        }
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (counterForThisPacket > packetsCounter[0]) {
                            if (Math.abs(timesOfArrivalOfPackets[0] - timeOfArrivalOfThisPacket) > 10) {
                                tokensWillBeUsed = buckets[0];
                                synchronized (Worker.class) {
                                    buckets[0] -= buckets[0];
                                }
                                try (Socket socket = new Socket("localhost", 6834)) {
                                    PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
                                    writer.println(argumentForServer);
                                    writer.close();
                                } catch (UnknownHostException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                try {
                                    Thread.sleep(2500);
                                } catch (InterruptedException e) {
                                    System.out.println ("Another thread interrupted this.");
                                }
                                synchronized (Worker.class) {
                                    buckets[0] += tokensWillBeUsed;
                                }
                            } //If a new packet arrived within 10 seconds else is executed.
                            else {

                            }
                        } else if (counterForThisPacket == packetsCounter[0]) {
                            if (arrivedFirst) {
                                tokensWillBeUsed = buckets[0] / 2;
                            } /*If arrivedFirst is false it means program is in thread of second packet arrived at most 10 milliseconds after previous.
                                If in this case write tokensWillBeUsed = buckets[0] / 2 it would assign the half of the half of the tokens I should assign,
                                because the first half are assigned to first packet. Thus, the other half remain.
                                If I do tokensWillBeUsed = buckets[0] / 2 I am going to assign the half of the half.
                              */
                            else {
                                tokensWillBeUsed = buckets[0];
                            }
                            synchronized (Worker.class) {
                                buckets[0] -= tokensWillBeUsed;
                            }
                        }
                    }
                    tokensWillBeUsed = buckets[0];
                    synchronized (Worker.class) {
                        buckets[0] -= buckets[0];
                    }
                    try (Socket socket = new Socket("localhost", 6834)) {
                        PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
                        writer.println(argumentForServer);
                        writer.close();
                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    synchronized (Worker.class) {
                        buckets[0] += tokensWillBeUsed;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
