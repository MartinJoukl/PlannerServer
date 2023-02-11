package joukl.plannerexec.plannerserver.model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

import static joukl.plannerexec.plannerserver.model.KeyType.*;

public class Authorization {
    private static final String PATH_TO_KEYS = "storage/keys";
    private PrivateKey serverPrivateKey;
    private PublicKey serverPublicKey;
    private PublicKey clientPublicKey;

    public boolean loadPrivateKeyFromRoot() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        try {
            byte[] key = Files.readAllBytes(Paths.get(PATH_TO_KEYS + "/" + SERVER_PRIVATE.getKeyName() + ".key"));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
            serverPrivateKey = keyFactory.generatePrivate(keySpec);

            RSAPrivateCrtKey privk = (RSAPrivateCrtKey) serverPrivateKey;
            RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(privk.getModulus(), privk.getPublicExponent());

            serverPublicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (IOException ignored) {
            return false;
        }
        //refresh public key
        saveKeyToStorage(SERVER_PUBLIC);
        return true;
    }
    public boolean loadClientKeyFromRoot() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        try {
            byte[] key = Files.readAllBytes(Paths.get(PATH_TO_KEYS + "/" + CLIENT_PUBLIC.getKeyName() + ".key"));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
            clientPublicKey = keyFactory.generatePublic(keySpec);
        } catch (IOException ignored) {
            return false;
        }
        return true;
    }

    public boolean changeServerKeys(File file) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        byte[] key = Files.readAllBytes(Paths.get(file.getPath()));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(key);
        try {
            serverPrivateKey = keyFactory.generatePrivate(keySpec);

            RSAPrivateCrtKey privk = (RSAPrivateCrtKey) serverPrivateKey;
            RSAPublicKeySpec publicKeySpec = new java.security.spec.RSAPublicKeySpec(privk.getModulus(), privk.getPublicExponent());

            serverPublicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (InvalidKeySpecException ex1) {
            return false;
        }
        saveKeyToStorage(SERVER_PRIVATE);
        saveKeyToStorage(SERVER_PUBLIC);

        System.out.println("Private key format: " + serverPrivateKey.getFormat());

        System.out.println("Public key format: " + serverPublicKey.getFormat());
        return true;
    }

    public boolean changeClientKey(File file) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        try {
            byte[] key = Files.readAllBytes(Paths.get(file.getPath()));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
            clientPublicKey = keyFactory.generatePublic(keySpec);
        } catch (InvalidKeySpecException ex1) {
            return false;
        }
        saveKeyToStorage(CLIENT_PUBLIC);

        System.out.println("Client public key format: " + clientPublicKey.getFormat());
        return true;
    }

    public boolean generateServerKeys() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keys = kpg.generateKeyPair();
        serverPrivateKey = keys.getPrivate();
        serverPublicKey = keys.getPublic();
        saveKeyToStorage(SERVER_PRIVATE);
        saveKeyToStorage(SERVER_PUBLIC);


        System.out.println("Private key format: " + serverPrivateKey.getFormat());
// prints "Private key format: PKCS#8" on my machine

        System.out.println("Public key format: " + serverPublicKey.getFormat());
// prints "Public key format: X.509" on my machine
        return true;
    }

    public boolean loadClientPublicKey(String path) throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        byte[] key = Files.readAllBytes(Paths.get(path));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(key);
        try {
            clientPublicKey = keyFactory.generatePublic(keySpec);
            saveKeyToStorage(CLIENT_PUBLIC);
        } catch (InvalidKeySpecException ex1) {
            return false;
        }
        return true;
    }

    public void saveKeyToStorage(KeyType keyType) {
        switch (keyType) {
            case SERVER_PUBLIC -> {
                Path path = Paths.get(PATH_TO_KEYS + "/" + SERVER_PUBLIC.getKeyName() + ".key");
                Persistence.saveBytesToFile(path, serverPublicKey.getEncoded());
            }
            case SERVER_PRIVATE -> {
                Path path = Paths.get(PATH_TO_KEYS + "/" + SERVER_PRIVATE.getKeyName() + ".key");
                Persistence.saveBytesToFile(path, serverPrivateKey.getEncoded());
            }
            case CLIENT_PUBLIC -> {
                Path path = Paths.get(PATH_TO_KEYS + "/" + CLIENT_PUBLIC.getKeyName() + ".key");
                Persistence.saveBytesToFile(path, clientPublicKey.getEncoded());
            }
        }
    }

    private PublicKey generatePublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA"); //RSA
        PKCS8EncodedKeySpec keyspec = keyFactory.getKeySpec(serverPrivateKey, PKCS8EncodedKeySpec.class);
        return keyFactory.generatePublic(keyspec);
    }

    public PublicKey getServerPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (serverPublicKey == null) {
            return generatePublicKey();
        }
        return serverPublicKey;
    }

    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }

    public void setServerPrivateKey(PrivateKey serverPrivateKey) {
        this.serverPrivateKey = serverPrivateKey;
    }

    public void setServerPublicKey(PublicKey serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
    }

    public PublicKey getClientPublicKey() {
        return clientPublicKey;
    }

    public void setClientPublicKey(PublicKey clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
    }

}
