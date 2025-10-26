package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DictionaryAttack {

    // Further improvements:
    /**
     * 1. Look at replacing concurrent RW (Junyi)
     * 2. See where can use streams (KIV?)
     * 3. Futures: see how to check done concurrently (mok example) -
     * ExecutorCompletionService (Nashwyn) (Alex done it but see if can improve)
     * 4. Make the freq of the Reporter thread same as old version (Nashwyn)
     * 5. See how to load concurrently (Junyi)
     * 6. Test in VM (Alexander)
     * 7. Look at JVM Tuning (KIV)
     * 8. BONUS: Look at JDK25 (KIV)
     * 9. MainFlow and Execute Task flow (Alexander)
     * 
     * Additional Stuff Added:
     * - Object Pooling for CrackTask
     * - Used StringBuilder instaed of String during printing of Reporter
     */

    // Adds
    static Map<String, User> users;
    static Map<String, String> hashToPassword;

    // Replace with concurrent Read/write
    static AtomicInteger passwordsFound = new AtomicInteger(0);
    static AtomicInteger hashesComputed = new AtomicInteger(0);
    static AtomicInteger processedUsers = new AtomicInteger(0);

    static Loader<Map<String, String>> dictionaryLoader = new DictionaryLoader(hashesComputed);
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

        passwordsFound.set(0);
        hashesComputed.set(0);
        processedUsers.set(0);

        // Precompute the hashes of every single line in the dictionary
        hashToPassword = dictionaryLoader.load(dictionaryPath); // O(m)

        // Loads file, Creates user object & adds to a Map<String, User>
        users = userLoader.load(usersPath);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        long totalTasks = users.size();
        System.out.println("\nStarting attack with " + totalTasks + " total tasks...");

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Thread reporter = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int found = passwordsFound.get();
                int computed = hashesComputed.get();
                int processed = processedUsers.get();
                long elapsed = System.currentTimeMillis() - start;

                double progress = totalTasks == 0 ? 0.0 : ((double) processed / totalTasks) * 100;
                String timestamp = LocalDateTime.now().format(formatter);

                System.out.printf(
                        "\r[%s] %.2f%% complete | Tasks Processed: %d/%d | Passwords Found: %d | Hashes Computed: %d | Elapsed: %d ms",
                        timestamp, progress, processed, totalTasks, found, computed, elapsed);

                try {
                    Thread.sleep(1000); // update every second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        reporter.start();

        ArrayBlockingQueue<CrackTask> pool = new ArrayBlockingQueue<>(users.size());

        // Reusable Object Pool
        for (User user : users.values()) {
            CrackTask task = pool.poll();
            if (task == null) {
                task = new CrackTask();
            }
            task.setup(user, hashToPassword, passwordsFound, processedUsers);
            pool.offer(task);

            CompletableFuture<Void> future = CompletableFuture.runAsync(task, executor);
            futures.add(future);
        }

        // Waits for all to complete regardless of order
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        all.join();

        reporter.interrupt();
        try {
            reporter.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor.shutdown();

        double finalProgress = totalTasks == 0 ? 100.0 : ((double) processedUsers.get() / totalTasks) * 100;
        long totalMillis = System.currentTimeMillis() - start;
        String finalTimestamp = LocalDateTime.now().format(formatter);

        StringBuilder logBuilder = new StringBuilder(200); // replace with sb to reduce object allocation
        logBuilder.append("\r[")
                .append(finalTimestamp)
                .append("] ")
                .append(String.format("%.2f", finalProgress))
                .append("% complete | Tasks Processed: ")
                .append(processedUsers.get()).append("/")
                .append(totalTasks)
                .append(" | Passwords Found: ").append(passwordsFound.get())
                .append(" | Hashes Computed: ").append(hashesComputed.get())
                .append(" | Elapsed: ").append(totalMillis).append(" ms\n");

        System.out.print(logBuilder.toString());

        System.out.println("");
        System.out.println("Total passwords found: " + passwordsFound.get());
        System.out.println("Total hashes computed: " + hashesComputed.get());
        System.out.println("Total time spent (milliseconds): " + totalMillis);

        if (passwordsFound.get() > 0) {
            ReportWriter.write(users, passwordsPath);
        }
    }

}
