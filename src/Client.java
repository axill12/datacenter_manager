import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    public static void main (String args []) {
        new Thread(new Worker()).start();
        new Thread(new Worker()).start();
    }

    private static class Worker implements Runnable {

        public void run () {
            try (Socket socket = new Socket("localhost", 7169)) {
                PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
                writer.println("8888");
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
