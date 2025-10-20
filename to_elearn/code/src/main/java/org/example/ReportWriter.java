package org.example;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class ReportWriter {
      public static void write(Map<String, User> users, String filePath) {
            File file = new File(filePath);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                  // Write the CSV header
                  writer.write("user_name,hashed_password,plain_password\n");

                  // Iterate through all users and write the details of the cracked ones
                  for (User user : users.values()) {
                        if (user.isFound()) {
                              String line = String.format("%s,%s,%s\n",
                                          user.getUsername(),
                                          user.getHashedPassword(),
                                          user.getFoundPassword());
                              writer.write(line);
                        }
                  }
                  System.out.println("\nCracked password details have been written to " + filePath);
            } catch (IOException e) {
                  System.err.println("Error: Could not write to CSV file: " + e.getMessage());
            }
      }
}
