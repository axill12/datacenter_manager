import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerInstaller {

    public static void main (String args[]) {
        Socket client;
        BufferedReader reader;
        String line;
        RandomAccessFile writer new RandomAccessFile("ContainerInstaller.sh", );

        String[] command = new String[2];
        command[0] = "sh";
        command[1] = "ContainerInstaller.sh";

        try (ServerSocket server = new ServerSocket(7170)) {
            while (true) {
                client = server.accept();
                reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                line = reader.readLine();

                Runtime.getRuntime().exec(command, null, new File("/home/rtds/IdeaProjects/WorkloadCompactor_improvement"));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
