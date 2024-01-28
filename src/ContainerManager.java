import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContainerManager  {

    public static void main (String args[]) {
        Socket receiver;
        BufferedReader reader;

        String[] command = new String[3];
        command[0] = "sh";
        command[1] = "installer_test.sh";

        Pattern pattern = Pattern.compile("[^0-9]+");
        Matcher matcher;
        boolean notContainsOnlyDigits;

        int SLO;
        int necessaryTokens;

        try (ServerSocket server = new ServerSocket(7170)) {
            while (true) {
                receiver = server.accept();
                reader = new BufferedReader (new InputStreamReader(receiver.getInputStream()));
                command[2] = reader.readLine();
                matcher = pattern.matcher(command [2]);
                notContainsOnlyDigits = matcher.find();
                if (notContainsOnlyDigits || command[2].isEmpty()) {
                    System.out.println ("The id of server user provided does not contains only digits or contains nothing. His demand cannot be served.");
                    continue;
                }
                SLO = Integer.parseInt(reader.readLine());
                necessaryTokens = (int) ((10.0 / SLO) * 50) + 1;

                //TODO na luso to mikro problhma logo toy opoioy anatithetai ena token parapano otan (10.0 / SLO) * 50 tha htan akeraios an anti na ton ekteleso sthn java ektelousa ton upologismo se kompiouteraki. p.x. gia SLO = 20

                //It is better applications to be able to serve 7 requests simultaneously. That is the reason WorkScheduler.getTotalAvailableTokens() >= 7 is applied.
                if (necessaryTokens <= WorkScheduler.getTotalAvailableTokens() && WorkScheduler.getTotalAvailableTokens() >= 7) {
                    Runtime.getRuntime().exec(command, null, new File("/home/rtds/IdeaProjects/WorkloadCompactor_improvement"));
                    //Assigns at least 7 tokens. So that, can serve simultaneously at least 7 tokens.
                    if (necessaryTokens > 7) {
                        WorkScheduler.setTotalAvailableTokens(WorkScheduler.getTotalAvailableTokens() - necessaryTokens);
                    } else {
                        WorkScheduler.setTotalAvailableTokens(WorkScheduler.getTotalAvailableTokens() - 7);
                    }
                    System.out.println("totalAvailableTokens: " + WorkScheduler.getTotalAvailableTokens());

                    //TODO na topothetei thn efarmogh sto buckets[serverCell]

                } else {
                    System.out.println("This server does not have enough tokens to serve this container suitably.");
                }
                reader.close();
                receiver.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NumberFormatException nfe) {
            System.out.println("User provided SLO, which is not in number format.");
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

}