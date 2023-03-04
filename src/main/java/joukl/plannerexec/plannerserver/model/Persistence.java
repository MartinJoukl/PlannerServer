package joukl.plannerexec.plannerserver.model;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    public static Configuration readApplicationConfiguration() {

        File configFile = new File("config.json");
        if (configFile.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            try {
                return mapper
                        .readerFor(Configuration.class)
                        .readValue(configFile);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    // Source:
    // https://www.baeldung.com/java-compress-and-uncompress
    public static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException, IllegalBlockSizeException, BadPaddingException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.closeEntry();
            } else {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    public static void cleanUp(Task task) throws IOException {
        //Delete input data
        Files.delete(Path.of(Scheduler.PATH_TO_TASK_STORAGE + task.getId() + ".zip"));
    }
}
