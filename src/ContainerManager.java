import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ContainerManager {

    public static void main (String args[]) {
        Socket client;
        BufferedReader reader;
        String line [] = new String [1];

        String[] command = new String[2];
        command[0] = "sh";
        command[1] = "installer_test.sh";

        try (ServerSocket server = new ServerSocket(7170);
             RandomAccessFile writer = new RandomAccessFile("installer_test.sh", "rw")) {
            while (true) {
                client = server.accept();
                reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                line = reader.readLine().split(" ");
                int idOfServer = Integer.parseInt(line[0]);
                //Writes ContainerInstaller.sh the id of server, which is desired to be installed, to two points.
                writer.seek(53);
                writer.writeInt(idOfServer);
                writer.seek(93);
                writer.writeInt(idOfServer);
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
