package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class UserLoader implements Loader<Map<String, User>> {
      @Override
      public Map<String, User> load(String fileName) throws IOException {
            long start = System.currentTimeMillis();

            List<String> lines = Files.readAllLines(Paths.get(fileName));
            Map<String, User> users = new HashMap<>();

            for (String line : lines) {
                  String[] parts = line.split(",");
                  if (parts.length >= 2) {
                        String username = parts[0];
                        String hashedPassword = parts[1];
                        users.put(username, new User(username, hashedPassword));
                  }
            }
            long totalMillis = System.currentTimeMillis() - start;
            System.out.println("time to load users: " + totalMillis);
            return users;
      }
}
