package ReadDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Read {

    public static void main (String args []) {
        File file = new File ("Text_files/" + args[0] + ".txt");
        try {
            Scanner scanner = new Scanner(file);

            while (scanner.hasNext()) {
                scanner.next();
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

}
