package org.example;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;

public class DictionaryAttack {

    // Adds
    static Map<String, User> users;
    static List<String> cracked = new ArrayList<>();
    static Map<String, String> hashToPassword;
    static Map<String, String> reverseLookupCache = new HashMap<>();
    static AtomicInteger passwordsFound = new AtomicInteger(0);

    static Loader<Map<String, String>> dictionaryLoader = new DictionaryLoader();
    static Loader<Map<String, User>> userLoader = new UserLoader();

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

        // Precompute the hashes of every single line in the dictionary
        hashToPassword = dictionaryLoader.load(dictionaryPath);

        // Loads file, Creates user object & adds to a Map<String, User>
        users = userLoader.load(usersPath);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<Future<?>> futures = new ArrayList<>();

        for (User user : users.values()) {
            CrackTask task = new CrackTask(user, hashToPassword, passwordsFound);
            futures.add(executor.submit(task));
        }

        long totalTasks = users.size();
        System.out.println("Starting attack with " + totalTasks + " total tasks...");

        System.out.println("");
        System.out.println("Total passwords found: " + passwordsFound.get());
        System.out.println("Total hashes computed: " + DictionaryLoader.getHashesComputed());
        System.out.println("Total time spent (milliseconds): " + (System.currentTimeMillis() - start));

        if (passwordsFound.get() > 0) {
            ReportWriter.write(users, passwordsPath);
        }
    }

}
