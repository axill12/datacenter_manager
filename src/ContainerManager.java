import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ContainerManager  {

    private static int capacityOfBuckets [] = new int [7];

    public static void main (String args[]) {
        Socket receiver;
        BufferedReader reader;

        String[] command = new String[3];
        command[0] = "sh";
        command[1] = "ContainerInstaller.sh";

        //If I do not initialize idOfServer shows error in try.
        int idOfServer = -1;
        Pattern pattern = Pattern.compile("[^0-9]+");
        Matcher matcher;
        boolean notContainsOnlyDigits;

        String secondParameter;
        int SLO;
        int necessaryTokens;

        ArrayList<ServerInformations> listOfServers = new ArrayList<>();
        for (int i=0; i < 7; i++) {
            capacityOfBuckets[i] = -1;
        }
        //Position of server in capacityOfBuckets and buckets of WorkScheduler.
        int positionOfServer;

        try (ServerSocket server = new ServerSocket(7170)) {
            while (true) {
                receiver = server.accept();
                reader = new BufferedReader(new InputStreamReader(receiver.getInputStream()));
                //command[2] that user sends is the id of server, which wish to be installed.
                command[2] = reader.readLine();
                matcher = pattern.matcher(command[2]);
                notContainsOnlyDigits = matcher.find();
                if (notContainsOnlyDigits || command[2].isEmpty()) {
                    System.out.println("The id of server user provided does not contains only digits or contains nothing. His demand cannot be served.");
                    continue;
                }
                try {
                    idOfServer = Integer.parseInt(command[2]);
                }  catch (NumberFormatException nfe) {
                    System.out.println("User provided id of server, which is not in number format.");
                    continue;
                }
                secondParameter = reader.readLine();
                matcher = pattern.matcher(secondParameter);
                notContainsOnlyDigits = matcher.find();
                if (secondParameter.equals("r")) {
                    //TODO remove function
                } else if (!notContainsOnlyDigits) {
                    SLO = Integer.parseInt(secondParameter);
                    initiateServer(SLO, command, listOfServers, idOfServer);
                } else {
                  System.out.println ("The second parameter user wrote does not contain only digits and it is not r. Thus is wrong. His request cannot be satisfied.");
                  continue;
                }

                reader.close();
                receiver.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private static void initiateServer (int SLO, String command [], ArrayList<ServerInformations> listOfServers, int idOfServer) {
        int necessaryTokens = computeNecessaryTokens(SLO);
        int positionOfServer;

        //It is better applications to be able to serve 7 requests simultaneously. That is the reason WorkScheduler.getTotalAvailableTokens() >= 7 is applied.
        if (necessaryTokens <= WorkScheduler.getTotalAvailableTokens() && WorkScheduler.getTotalAvailableTokens() >= 7) {
            try {
                Runtime.getRuntime().exec(command, null, new File("/home/rtds/IdeaProjects/WorkloadCompactor_improvement"));
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
            //Assigns at least 7 tokens. So that, can serve simultaneously at least 7 tokens.
            if (necessaryTokens > 7) {
                WorkScheduler.setTotalAvailableTokens(WorkScheduler.getTotalAvailableTokens() - necessaryTokens);
            } else {
                WorkScheduler.setTotalAvailableTokens(WorkScheduler.getTotalAvailableTokens() - 7);
            }
            System.out.println("totalAvailableTokens: " + WorkScheduler.getTotalAvailableTokens());

            positionOfServer = placeContainerInCapacityOfBuckets(necessaryTokens);
            listOfServers.add(new ServerInformations(idOfServer, positionOfServer));

            File serverFile = new File ("bucketOfServer" + idOfServer + ".txt");
            try {
                serverFile.createNewFile();
                RandomAccessFile writer = new RandomAccessFile("bucketOfServer" + idOfServer + ".txt", "rw");
                writer.writeInt(necessaryTokens);
                writer.close();
            } catch (FileNotFoundException fnfe) {
                fnfe.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } else {
            System.out.println("This server does not have enough tokens to serve this container suitably.");
        }
    }

    private static int computeNecessaryTokens(int SLO) {
        int necessaryTokens;
        /*For a few values of SLO (10.0 / SLO) * 50 computes the equivalent of (int) ((10.0 / SLO) * 50) * 1.0,
          meaning (10.0 / SLO) * 50 does not have values like 25.04 or 25.3.
          Though if necessaryTokens are assigned always the value of (int) ((10.0 / SLO) * 50) + 1 in above case it takes value higher by 1 than it should have
          e.g. for SLO = 20 instead of 25 it is assigned 26, because of + 1.
        */
        if ((10.0 / SLO) * 50 == (int) ((10.0 / SLO) * 50) * 1.0) {
            necessaryTokens = (int) ((10.0 / SLO) * 50);
        } else {
            necessaryTokens = (int) ((10.0 / SLO) * 50) + 1;
        }
        return necessaryTokens;
    }

    /*Assigns the total tokens every application, which is going to be installed, is going to have available in the first empty cell of capacityOfBuckets it discovers
      and returns the index of cell where number of total tokens is stored for the purpose of using it with buckets of WorkScheduler.
    */
    private static int placeContainerInCapacityOfBuckets (int necessaryTokens) {
        int i = 0;
        do {
            i++;
        } while (i < 7 && capacityOfBuckets[i - 1] != -1);
        capacityOfBuckets[i - 1] = necessaryTokens;
        return i - 1;
    }

    //Holds the id of each server, which is placed in real computer and its position in buckets array of WorkScheduler.
    private static class ServerInformations {
        private int idOfServer;

        private int positionOfServer;

        private ServerInformations (int idOfServer, int positionOfServer) {
            this.idOfServer = idOfServer;
            this.positionOfServer = positionOfServer;
        }
    }

}