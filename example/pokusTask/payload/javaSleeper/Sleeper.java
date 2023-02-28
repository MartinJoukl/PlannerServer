import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Arrays;
import java.io.File;

public class Sleeper {
    public static void main(String[] args) throws IOException {
        String result = Arrays.toString(args);
        result += "  <------- args";
        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        File folder = new File("res");
        folder.mkdirs();
        BufferedWriter writer = new BufferedWriter(new FileWriter("res/testik.txt"));
        writer.write(result);

        writer.close();
    }
}