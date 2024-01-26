import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContainerManager {

    public static void main (String args[]) {
        Socket client;
        BufferedReader reader;
        int idOfServer;

        String[] command = new String[3];
        command[0] = "sh";
        command[1] = "installer_test.sh";

        Pattern pattern = Pattern.compile("[^0-9]+");
        Matcher matcher;
        boolean notContainsOnlyDigits;

        try (ServerSocket server = new ServerSocket(7170)) {
            while (true) {
                client = server.accept();
                reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                command [2] = reader.readLine();
                matcher = pattern.matcher(command [2]);
                notContainsOnlyDigits = matcher.find();
                if (notContainsOnlyDigits) {
                    System.out.println ("The id of server user provided does not contains only digits. His demand cannot be served.");
                    continue;
                }
                Runtime.getRuntime().exec(command, null, new File("/home/rtds/IdeaProjects/WorkloadCompactor_improvement"));
            }
        } catch (NumberFormatException nfe) {
            System.out.println ("Something else is provided instead of number through network.");
        } catch (FileNotFoundException e) {
            System.out.println("It could not find file.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
