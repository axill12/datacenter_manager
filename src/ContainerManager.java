import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContainerManager {

    public static void main (String args[]) {
        Socket receiverOfCommands;
        BufferedReader readerOfCommands;
        PrintWriter writer;
        Socket receiverOfWorkScheduler;
        BufferedReader readerOfWorkScheduler;

        String[] command = new String[3];
        command[0] = "sh";
        command[1] = "installer_test.sh";

        Pattern pattern = Pattern.compile("[^0-9]+");
        Matcher matcher;
        boolean notContainsOnlyDigits;

        try (ServerSocket serverOfCommands = new ServerSocket(7170);
             ServerSocket serverOfWorkScheduler = new ServerSocket(7171)) {
            while (true) {
                receiverOfCommands = serverOfCommands.accept();
                readerOfCommands = new BufferedReader(new InputStreamReader(receiverOfCommands.getInputStream()));
                //command[2] should be the id of server which user wish to add or remove.
                command [2] = readerOfCommands.readLine();
                matcher = pattern.matcher(command [2]);
                notContainsOnlyDigits = matcher.find();
                if (notContainsOnlyDigits || command[2].isEmpty()) {
                    System.out.println ("The id of server user provided does not contains only digits or contains nothing. His demand cannot be served.");
                    continue;
                }
                System.out.println("command[2]: " + command[2]);

                //Sends the id of server to WorkScheduler.
                Socket senderOfCommands = new Socket ("localhost", 7168);
                writer = new PrintWriter(senderOfCommands.getOutputStream(), true);
                writer.println(command[2]);
                writer.close();
                senderOfCommands.close();

                receiverOfWorkScheduler = serverOfWorkScheduler.accept();
                System.out.println ("ContainerManager received connection request from WorkScheduler");
                readerOfWorkScheduler = new BufferedReader(new InputStreamReader(receiverOfWorkScheduler.getInputStream()));
                if (readerOfWorkScheduler.readLine().equals("yes")) {
                    Runtime.getRuntime().exec(command, null, new File("/home/rtds/IdeaProjects/WorkloadCompactor_improvement"));
                } else {
                    System.out.println("This server does not have enough tokens to serve this container suitably.");
                }

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
