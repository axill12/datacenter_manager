import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server1 {

    public static void main (String args []) {
        try (ServerSocket server = new ServerSocket((6834))) {
            while (true) {
                Socket client = server.accept();
                Worker worker = new Worker (client);
                new Thread(worker).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static class Worker implements Runnable {

        private Socket client;

        public Worker (Socket client) {
            this.client = client;
        }

        public void run () {

        }

    }

}
