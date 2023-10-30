package WriteDirectory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Write {
    private static final int MB_SIZE = 1048576;

    public static void main (String args []) {

        File file = new File("Text_files/" + args[0] +".txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int file_size = Integer.parseInt(args[0]) * MB_SIZE;
        try {
            FileWriter writer = new FileWriter("Text_files/" + args[0] + ".txt");
            for (int i=0; i<file_size; i++) {
                writer.write(1);
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
