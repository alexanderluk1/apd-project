package org.example;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class DictionaryAttack {

    // Adds
    static List<CrackTask> taskQueue = new LinkedList<>();
    static Map<String, User> users = new HashMap<>();
    static List<String> cracked = new ArrayList<>();
    static Map<String, String> reverseLookupCache = new HashMap<>();
    static int passwordsFound = 0;
    static int hashesComputed = 0;

    public static void main(String[] args) throws Exception {

        // Check if both file paths are provided as command-line arguments
        if (args.length < 2) {
            System.out.println("Usage: java -jar <jar-file-name>.jar <input_file> <dictionary_file> <output_file>");
            System.exit(1);
        }

        /**
         * This part sets the path to the data inputs
         * args[0] -> path to datasets/large/in.txt (which holds username + hashed pw)
         * args[1] -> path to datasets/large/dictionary.txt (list of plaintext common
         * passwords)
         * args[2] -> path to final output which matches hashed pw to plaintext
         */
        String usersPath = args[0];
        String dictionaryPath = args[1];
        String passwordsPath = args[2];

        // String datasetPath =
        // "/Users/kasung/Projects/se301/project/code/se301/src/datasets/large/";
        //
        // String dictionaryPath = datasetPath + "dictionary.txt";
        // String usersPath = datasetPath + "in.txt";
        // String passwordsPath = datasetPath + "out.txt";

        long start = System.currentTimeMillis();

        // Loads all plaintext password into a list of strings
        List<String> allPasswords = loadDictionary(dictionaryPath);

        // Loads file, Creates user object & adds to a Map<String, User>
        loadUsers(usersPath);

        /**
         * For each user in map
         * For every password
         * - Create a new CrackTask object (username, password)
         * - Each cracktask has a method execute()
         * - Adds this cracktask to the taskQueue
         * 
         */
        for (User user : users.values()) {
            for (String password : allPasswords) {
                taskQueue.add(new CrackTask(user.username, password));
            }
        }

        long totalTasks = taskQueue.size();
        System.out.println("Starting attack with " + totalTasks + " total tasks...");

        /**
         * While the taskQueue is not empty, run execute
         */
        while (!taskQueue.isEmpty()) {
            CrackTask task = taskQueue.poll();
            if (task != null)
                task.execute();

            if (taskQueue.size() % 1000 == 0) {

                long remainingTasks = taskQueue.size();
                long completedTasks = totalTasks - remainingTasks;
                double progressPercent = (double) completedTasks / totalTasks * 100;
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String timestamp = LocalDateTime.now().format(formatter);

                System.out.printf("\r[%s] %.2f%% complete | Passwords Found: %d | Tasks Remaining: %d",
                        timestamp, progressPercent, passwordsFound, remainingTasks);
            }
        }
        System.out.println("");
        System.out.println("Total passwords found: " + passwordsFound);
        System.out.println("Total hashes computed: " + hashesComputed);
        System.out.println("Total time spent (milliseconds): " + (System.currentTimeMillis() - start));

        if (passwordsFound > 0) {
            writeCrackedPasswordsToCSV(passwordsPath);
        }
    }

    /**
     * Writes the successfully cracked user credentials to a CSV file.
     * 
     * @param filePath The path of the CSV file to write to.
     */
    static void writeCrackedPasswordsToCSV(String filePath) {
        File file = new File(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // Write the CSV header
            writer.write("user_name,hashed_password,plain_password\n");

            // Iterate through all users and write the details of the cracked ones
            for (User user : users.values()) {
                if (user.isFound) {
                    String line = String.format("%s,%s,%s\n",
                            user.username,
                            user.hashedPassword,
                            user.foundPassword);
                    writer.write(line);
                }
            }
            System.out.println("\nCracked password details have been written to " + filePath);
        } catch (IOException e) {
            System.err.println("Error: Could not write to CSV file: " + e.getMessage());
        }
    }

    // Probably can't optimise this
    static List<String> loadDictionary(String filePath) throws IOException {
        List<String> allWords = new ArrayList<>();
        try {
            allWords.addAll(Files.readAllLines(Paths.get(filePath)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return allWords;
    }

    static void loadUsers(String filename) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filename));
        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length >= 2) {
                String username = parts[0];
                String hashedPassword = parts[1];
                users.put(username, new User(username, hashedPassword));
            }
        }
    }

    static String sha256(String input) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    static class CrackTask {
        String username;
        String password;

        CrackTask(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public void execute() {
            User user = users.get(username);
            if (user == null || user.isFound)
                return;

            try {
                String hash = sha256(password);
                hashesComputed++;
                reverseLookupCache.put(password, hash);

                if (hash.equals(user.hashedPassword)) {
                    cracked.add(username + ": " + password);
                    user.isFound = true;
                    user.foundPassword = password;
                    passwordsFound++;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class User {
        String username;
        String hashedPassword;
        boolean isFound = false;
        String foundPassword = null;

        public User(String username, String hashedPassword) {
            this.username = username;
            this.hashedPassword = hashedPassword;
        }
    }
}
