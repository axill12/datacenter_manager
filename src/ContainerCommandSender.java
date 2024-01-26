import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ContainerCommandSender {

    /*User should give two arguments in the same line. The first should be the id of server, in numeral format, which wants to add or remove.
      If he wants to add server the second parameter should be the application's Service Level Objective (SLO).
      If he aims to remove server the second parameter should be r for remove.
     */
    public static void main (String args []) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String userInput [];
        try (Socket socket = new Socket ("localhost", 7170);
             PrintWriter writer = new PrintWriter (socket.getOutputStream(), true)){

            while (true) {
                userInput = new String [2];
                userInput = reader.readLine().split(" ");
                if (userInput[0].equals("ex")) {
                    return;
                }
                writer.println(userInput[0]);
            }
        } catch (UnknownHostException e) {
            System.out.println("It cannot find the host.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
