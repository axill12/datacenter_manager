import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

public class Client {

    public static void main (String args []) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        Random random = new Random();
        random.setSeed(System.currentTimeMillis());
        int randomInt;
        while (true) {
            try {
                String userInput = reader.readLine();

                try {
                    int input = Integer.parseInt(userInput);
                    for (int i=0; i <input; i++) {
                        randomInt = Math.abs(random.nextInt());
                        //Creates a thread with a number from 0 to 9999.
                        new Thread(new Worker(String.valueOf(randomInt % 10000))).start();
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

        private String argumentForServer;

        private Worker (String argumentForServer) {
            this.argumentForServer = argumentForServer;
        }

        public void run () {
            try (Socket socket = new Socket("localhost", 7169)) {
                PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
                writer.println(argumentForServer);
                writer.println("Server1");
                writer.close();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

}
