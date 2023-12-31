import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    public static void main (String args []) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                String userInput = reader.readLine();
                if (userInput.equals("1")) {
                    new Thread(new Worker("8888")).start();
                } else if (userInput.equals("2")) {
                    new Thread(new Worker("4444")).start();
                    new Thread(new Worker("9999")).start();
                } else if (userInput.equals("ex")) {
                    break;
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
