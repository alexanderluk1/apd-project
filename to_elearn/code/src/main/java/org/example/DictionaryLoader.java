package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryLoader implements Loader<Map<String, String>> {

      public final AtomicInteger hashesComputed;
      private static final Map<String, String> reverseLookupCache = new HashMap<>();

      public DictionaryLoader(AtomicInteger hashesComputed) {
            this.hashesComputed = hashesComputed;
      }

      @Override
      public Map<String, String> load(String filePath) throws IOException {
            Map<String, String> hashToPassword = new HashMap<>();
            // c1ed2cccec2ce4484c12507a13dedef31e4bf46cd9dd74506780487a062f74dd -> Secret3865689

            try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), StandardCharsets.UTF_8)) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                        String hash = Utils.sha256(line);
                        hashToPassword.put(hash, line);
                        hashesComputed.incrementAndGet();
                        reverseLookupCache.put(line, hash);
                  }
            } catch (IOException e) {
                  e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                  e.printStackTrace();
            }
            return hashToPassword;
      }
}
