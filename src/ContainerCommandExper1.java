import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ContainerCommandExper1 {

    public static void main (String args []) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String userInput = null;
        try {
            userInput = reader.readLine();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        for (int i=1; i <= 10; i++) {
            try (Socket socket = new Socket("localhost", 7170);
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
                writer.println(String.valueOf(i));
                writer.println(userInput);
            } catch (UnknownHostException e) {
                System.out.println("It cannot find the host.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
