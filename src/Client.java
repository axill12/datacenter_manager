import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    public static void main (String args []) {
        try (Socket socket = new Socket("localhost", 6834)) {
            PrintWriter writer = new PrintWriter (socket.getOutputStream(), true);
            writer.printf("1000");
            writer.flush();
            writer.close();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
