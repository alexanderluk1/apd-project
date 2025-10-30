package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DictionaryLoader implements Loader<Map<String, String>> {

      public final AtomicInteger hashesComputed;
      private static final Map<String, String> reverseLookupCache = new HashMap<>();

      public DictionaryLoader(AtomicInteger hashesComputed) {
            this.hashesComputed = hashesComputed;
      }

      @Override
      public Map<String, String> load(String filePath) throws IOException {

            Set<String> uniqueLines = new HashSet<>(Files.readAllLines(Paths.get(filePath)));

            Map<String, String> hashToPassword = new HashMap<>();

            for (String line : uniqueLines) {
                try {
                    String hash = Utils.sha256(line);
                    hashToPassword.put(hash, line);
                    hashesComputed.incrementAndGet();
                    reverseLookupCache.put(line, hash);
                } catch (NoSuchAlgorithmException e){
                    e.printStackTrace();
                }
            }

            return hashToPassword;

      }
}
