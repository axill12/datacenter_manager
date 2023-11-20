import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class SSD_Work {

    public static void main (String args []) {

        String dockerRun [] = new String [6];
        dockerRun[0] = "docker";
        dockerRun[1] = "run";
        dockerRun[2] = "--name";
        dockerRun[3] = "writer";
        dockerRun[4] = "sha256:117cf7bb4b159c7c54bc82cdf6ae6d2227324f57c5c2e0b1300f282fdbcb4a03";
        dockerRun[5] = "2";

        try {
            Process runImage = Runtime.getRuntime().exec(dockerRun, null, new File("/var/lib/docker/image/overlay2/imagedb/content/sha256"));

            BufferedReader runImageReader = new BufferedReader (new InputStreamReader(runImage.getErrorStream()));
            String runErrorLine;
            while((runErrorLine = runImageReader.readLine()) != null) {
                System.out.println(runErrorLine);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        DockerClient client = DockerClientBuilder.getInstance().build();
        /*InspectContainerResponse containerDetails = client.inspectContainerCmd().exec();
        containerDetails.getId();*/

        String idOfContainer [] = new String [7];
        idOfContainer[0] = "docker";
        idOfContainer[1] = "container";
        idOfContainer[2] = "ls";
        idOfContainer[3] = "--all";
        idOfContainer[4] = "--quiet";
        idOfContainer[5] = "--filter";
        idOfContainer[6] = "name=writer";

        String id = null;

        /*try {
            Process processIdOfCont = Runtime.getRuntime().exec(idOfContainer);

            BufferedReader idOfContErrorReader = new BufferedReader(new InputStreamReader(processIdOfCont.getErrorStream()));
            String idLine;
            while ((idLine = idOfContErrorReader.readLine()) != null) {
                System.out.println(idLine);
            }

            BufferedReader idOfContReader = new BufferedReader(new InputStreamReader(processIdOfCont.getInputStream()));
            id = idOfContReader.readLine();
            System.out.println (id);
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        if (id == null) {
            System.out.println("It didn't find id.");
            return;
        }

        /*String dockerStats [] = new String [3];
        dockerStats[0] = "docker";
        dockerStats[1] = "stats";
        dockerStats[2] = id;

        try {
            Process statsOfImage = Runtime.getRuntime().exec(dockerStats, null, new File("/var/lib/docker/containers"));

            BufferedReader statsErrorReader = new BufferedReader(new InputStreamReader(statsOfImage.getErrorStream()));
            String errorLine;
            while ((errorLine = statsErrorReader.readLine()) != null) {
                System.out.println("inside errorLine's loop");
                System.out.println(errorLine);
            }
            statsErrorReader.close();

            BufferedReader reader = new BufferedReader(new InputStreamReader(statsOfImage.getInputStream()));
            String line;
            //Sum of B/s
            long sum = 0;
            //Count of numbers added in sum
            int count = 0;
            System.out.println("Right outside while for calculating average IO");
            while ((line = reader.readLine()) != null) {
                String [] tokens = line.split(" ");
                System.out.println(line);
                //sum += calculateIO (tokens[14]);
                //count++;
            }
            reader.close();
            //I turn sum in kBs because sum divided by count perhaps doesn't fit in double.
            long sumInkBs = sum / 1000;
            double averageIO = sumInkBs / (count * 1.0);
            double work = 1 / averageIO;
            System.out.println(work);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }

    //Returns IO per second in B/s. If isn't specified, if the input is in B, kB, MB or gigabytes then returns -1;
    private static long calculateIO (String stringIO) {
        int index = 0;
        String stringNumber = "";
        while (stringIO.charAt(index) >= 48 && stringIO.charAt(index) <= 57) {
            stringNumber += stringIO.charAt(index);
            index++;
        }
        int io = Integer.parseInt(stringNumber);
        if (stringIO.charAt(index) == 'B') return io;
        else if (stringIO.charAt(index) == 'k') return io * 1000;
        else if (stringIO.charAt(index) == 'M') return io * 1000000;
        else if (stringIO.charAt(index) == 'G' || stringIO.charAt(index) == 'g') return io * 1000000000;
        else return -1;
    }

}
