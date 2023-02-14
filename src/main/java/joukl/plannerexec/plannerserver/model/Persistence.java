package joukl.plannerexec.plannerserver.model;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

public class Persistence {

    public static boolean saveBytesToFile(Path pathWithFileName, byte[] bytes) {
        try (FileOutputStream outputStream = new FileOutputStream(pathWithFileName.toString())) {
            outputStream.write(bytes);
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public static Task readTaskConfiguration(File configFile) throws IOException {

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper
                    .readerFor(Task.class)
                    .readValue(configFile);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
