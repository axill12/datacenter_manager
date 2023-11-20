import java.io.*;

public class SSD_Work2 {

    public static void main (String args []) {
        String writerPID = runWriter ((short) 30);
        System.out.println(writerPID);

        String statCommand [] = new String [4];
        statCommand[0] = "pidstat";
        statCommand[1] = "-d";
        statCommand[2] = "-p";
        statCommand[3] = "ALL";
        try {
            Process stats = Runtime.getRuntime().exec(statCommand);
            BufferedReader statsReader = new BufferedReader(new InputStreamReader (stats.getInputStream()));
            String line;
            String [] tokens;
            int sum = 0; //Sum KB per second
            while ((line = statsReader.readLine()) != null) {
                tokens = line.split("[ ]+");
                if (tokens.length == 9) {
                    if (tokens[3].equals(writerPID)) {
                        sum += Integer.parseInt(tokens[5]); //For a class that reads it should be tokens[4].
                    }
                }

            }
            System.out.println(sum);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Creates and run a container of image of Write class. It returns the pid of its process.
    If runImage process was created pid is going to be bigger than zero, if not pid is going to be -1.*/
    private static String runWriter (short sizeInMB) {
        String writeCommand [] = new String [3];
        writeCommand[0] = "java";
        writeCommand[1] = "Write";
        writeCommand[2] = Short.toString(sizeInMB);

        Process writeProcess;
        long pid = -1;

        try {
            writeProcess = Runtime.getRuntime().exec(writeCommand, null, new File ("out/production/WorkloadCompactor_improvement"));
            pid = writeProcess.pid();
            BufferedReader reader = new BufferedReader (new InputStreamReader(writeProcess.getErrorStream()));
            String runErrorLine;
            while((runErrorLine = reader.readLine()) != null) {
                System.out.println(runErrorLine);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return Long.toString(pid);
    }

}
