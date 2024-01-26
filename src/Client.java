import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

public class Client {

    //User should write in same line first the id of the server , to which desires to reach his request and then the parameter for server.
    public static void main (String args []) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Random random = new Random();
        random.setSeed(System.currentTimeMillis());
        int randomInt;
        String userInput [];
        while (true) {
            try {
                userInput = new String [2];
                userInput = reader.readLine().split(" ");

                try {
                    int numOfRequests = Integer.parseInt(userInput[1]);
                    for (int i=0; i < numOfRequests; i++) {
                        randomInt = Math.abs(random.nextInt());
                        //Creates a thread with the id of the server, to which this requests must reach, and a number from 1 to 10000.
                        new Thread(new Worker(userInput[0], String.valueOf(1 + randomInt % 10000))).start();
                    }
                } catch (NumberFormatException e) {
                    if (userInput.equals("ex")) {
                        return;
                    } //If userInput is not ex next iteration starts because there are no other commands (e.g. if it is not number and ex).
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Worker implements Runnable {

        private String idOfServer;

        private String argumentForServer;

        private Worker (String idOfServer, String argumentForServer) {
            this.idOfServer = idOfServer;
            this.argumentForServer = argumentForServer;
        }

        public void run () {
            try (Socket socket = new Socket("localhost", 7169)) {
                PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
                writer.println(argumentForServer);
                writer.println(idOfServer);
                writer.close();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
