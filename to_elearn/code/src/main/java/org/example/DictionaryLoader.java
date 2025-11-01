package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryLoader implements Loader<Map<String, String>> {

    public final AtomicInteger hashesComputed;

    public DictionaryLoader(AtomicInteger hashesComputed) {
        this.hashesComputed = hashesComputed;
    }

    @Override
    public Map<String, String> load(String filePath) throws IOException {

        Set<String> uniqueLines = new HashSet<>(Files.readAllLines(Paths.get(filePath)));

        Map<String, String> hashToPassword = new HashMap<>();
        // c1ed2cccec2ce4484c12507a13dedef31e4bf46cd9dd74506780487a062f74dd ->
        // Secret3865689

        for (String line : uniqueLines) {
            try {
                String hash = Utils.sha256(line);
                hashToPassword.put(hash, line);
                hashesComputed.incrementAndGet();

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        return hashToPassword;

    }
}
