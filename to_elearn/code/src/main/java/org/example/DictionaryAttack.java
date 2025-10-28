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

    // Shared state
    static Map<String, User> users;
    static Map<String, String> hashToPassword;

    // Counters
    static AtomicInteger passwordsFound = new AtomicInteger(0);
    static AtomicInteger hashesComputed = new AtomicInteger(0);
    static AtomicInteger processedUsers = new AtomicInteger(0);

    // Loaders
    static Loader<Map<String, String>> dictionaryLoader = new DictionaryLoader(hashesComputed);
    static Loader<Map<String, User>> userLoader = new UserLoader();

    // Reporter configuration
    private static final int REPORT_BATCH_SIZE = 1000;
    private static final Object REPORT_MONITOR = new Object();
    private static volatile long nextReportThreshold = REPORT_BATCH_SIZE;

    // Fields accessed by helper methods / reporter
    private static long start;
    private static long totalMillis;
    private static long totalTasks;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static Thread reporter;

    // Further improvements:
    /**
     * 1. Look at replacing concurrent RW (Junyi)
     * 4. Make the freq of the Reporter thread same as old version (Nashwyn)
     * 5. See how to load concurrently (Junyi)
     * 7. Look at JVM Tuning (KIV)
     * 8. BONUS: Look at JDK25 (KIV)
     */

    public static void main(String[] args) throws Exception {
        validateArgs(args);

        String usersPath = args[0];
        String dictionaryPath = args[1];
        String passwordsPath = args[2];

        // Warmup for JIT compiler
        runWarmup(usersPath, dictionaryPath);

        // Init the data from dataset
        initializeState(usersPath, dictionaryPath);

        ExecutorService executor = createExecutor();
        List<CompletableFuture<Void>> futures = submitTasks(executor);

        startReporter();

        waitForCompletion(futures, executor);

        stopReporterAndJoin();

        finalizeTimingAndReport(passwordsPath);
    }

    // --- Initialization and warm-up ---
    private static void initializeState(String usersPath, String dictionaryPath) throws Exception {
        start = System.currentTimeMillis();

        passwordsFound.set(0);
        hashesComputed.set(0);
        processedUsers.set(0);
        nextReportThreshold = REPORT_BATCH_SIZE;

        // Precompute dictionary hashes
        hashToPassword = dictionaryLoader.load(dictionaryPath);

        // Load users
        users = userLoader.load(usersPath);

        totalTasks = users.size();

        System.out.println("\nStarting attack with " + totalTasks + " total tasks...");
    }

    private static void runWarmup(String usersPath, String dictionaryPath) throws Exception {
        System.out.println("Warming up JIT...");
        // perform two warmup passes
        for (int i = 0; i < 2; i++) {
            Map<String, User> warmupUsers = userLoader.load(usersPath);
            Map<String, String> warmupDict = dictionaryLoader.load(dictionaryPath);

            ExecutorService warmupExec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<CompletableFuture<Void>> warmupFutures = new ArrayList<>();

            for (User user : warmupUsers.values()) {
                CrackTask task = new CrackTask();
                task.setup(user, warmupDict, new AtomicInteger(0), new AtomicInteger(0));
                warmupFutures.add(CompletableFuture.runAsync(task, warmupExec));
            }

            CompletableFuture.allOf(warmupFutures.toArray(new CompletableFuture[0])).join();
            warmupExec.shutdown();
            try {
                warmupExec.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Warm-up complete. Starting benchmark...\n");
    }

    // --- Executor & Task submission ---
    private static ExecutorService createExecutor() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    private static List<CompletableFuture<Void>> submitTasks(ExecutorService executor) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(Math.max(16, (int) totalTasks));

        // Optional simple pool — we keep allocation behavior normal but show intent
        ArrayBlockingQueue<CrackTask> pool = new ArrayBlockingQueue<>((int) Math.max(1, totalTasks));

        for (User user : users.values()) {
            CrackTask task = pool.poll();
            if (task == null) {
                task = new CrackTask();
            }
            // Prepare task with the current state (immutable per submission)
            task.setup(user, hashToPassword, passwordsFound, processedUsers);
            // Do NOT re-offer the task into pool here — avoid reusing a live task

            CompletableFuture<Void> future = CompletableFuture.runAsync(task, executor);
            futures.add(future);
        }

        return futures;
    }

    private static void waitForCompletion(List<CompletableFuture<Void>> futures, ExecutorService executor) {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        all.join();

        executor.shutdown();
    }

    // --- Reporter lifecycle ---
    private static void startReporter() {
        reporter = new Thread(() -> {
            StringBuilder sb = new StringBuilder(256);
            while (!Thread.currentThread().isInterrupted()) {
                if (!waitForNextBatch()) {
                    break;
                }
                int found = passwordsFound.get();
                int computed = hashesComputed.get();
                int processed = processedUsers.get();
                long elapsed = System.currentTimeMillis() - start;

                double progress = totalTasks == 0 ? 0.0 : ((double) processed / totalTasks) * 100;
                String timestamp = LocalDateTime.now().format(FORMATTER);

                sb.setLength(0);
                sb.append("\r[").append(timestamp).append("] ")
                        .append(String.format("%.2f", progress))
                        .append("% complete | Tasks Processed: ")
                        .append(processed).append("/").append(totalTasks)
                        .append(" | Passwords Found: ").append(found)
                        .append(" | Hashes Computed: ").append(computed)
                        .append(" | Elapsed: ").append(elapsed).append(" ms");

                System.out.print(sb.toString());

                if (processed >= totalTasks) {
                    break;
                }
            }
        }, "StatusReporter");
        reporter.setDaemon(true);
        reporter.start();
    }

    private static void stopReporterAndJoin() {
        if (reporter != null) {
            reporter.interrupt();
            synchronized (REPORT_MONITOR) {
                REPORT_MONITOR.notifyAll();
            }
            try {
                reporter.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // --- Final reporting and output ---
    private static void finalizeTimingAndReport(String passwordsPath) {
        totalMillis = System.currentTimeMillis() - start;
        double finalProgress = totalTasks == 0 ? 100.0 : ((double) processedUsers.get() / totalTasks) * 100;
        String finalTimestamp = LocalDateTime.now().format(FORMATTER);

        StringBuilder logBuilder = new StringBuilder(200);
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

        printFinalStats();
        writeOutputIfNeeded(passwordsPath);
    }

    private static void validateArgs(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java -jar <jar-file-name>.jar <input_file> <dictionary_file> <output_file>");
            System.exit(1);
        }
    }

    private static void printFinalStats() {
        System.out.println();
        System.out.println("Total passwords found: " + passwordsFound.get());
        System.out.println("Total hashes computed: " + hashesComputed.get());
        System.out.println("Total time spent (milliseconds): " + totalMillis);
    }

    private static void writeOutputIfNeeded(String passwordsPath) {
        if (passwordsFound.get() > 0) {
            ReportWriter.write(users, passwordsPath);
        }
    }

    static void notifyReporter(int processed) {
        synchronized (REPORT_MONITOR) {
            long target = Math.min(nextReportThreshold, totalTasks);
            if (processed >= target) {
                REPORT_MONITOR.notifyAll();
            }
        }
    }

    private static boolean waitForNextBatch() {
        synchronized (REPORT_MONITOR) {
            while (!Thread.currentThread().isInterrupted()) {
                long target = Math.min(nextReportThreshold, totalTasks);
                if (processedUsers.get() >= target) {
                    nextReportThreshold += REPORT_BATCH_SIZE;
                    return true;
                }
                try {
                    REPORT_MONITOR.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return false;
        }
    }
}
