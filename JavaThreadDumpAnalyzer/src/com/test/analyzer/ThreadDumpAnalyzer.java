package com.test.analyzer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThreadDumpAnalyzer {

    // Regex patterns
    private static final Pattern THREAD_NAME_PATTERN = Pattern.compile("^\"(.*)\".*");
    private static final Pattern STATE_PATTERN = Pattern.compile("\\s*java.lang.Thread.State: (\\w+).*");
    private static final Pattern WAITING_LOCK_PATTERN = Pattern.compile(".*waiting on <(.*)>.*");
    private static final Pattern BLOCKED_BY_PATTERN = Pattern.compile(".*parking to wait for  <(.*)>.*");
    private static final Pattern LOCKED_OBJECT_PATTERN = Pattern.compile(".*locked <(.*)>.*");
    private static final Pattern CLASS_CAUSE_PATTERN = Pattern.compile("\\s+at (.+)\\((.+)\\)");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(".*(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}).*");

    public static void main(String[] args) {
        String filePath = "C:\\Users\\KRISHANA\\Documents\\Interview-Practice-KRISHNA\\JavaThreadDumpAnalyzer\\src\\com\\test\\analyzer\\threaddump.txt"; // Path to your thread dump file
        analyzeThreadDump(filePath);
        analyzeThreadDumpgraph(filePath);
    }

    private static void analyzeThreadDump(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            Map<String, Integer> stateCount = new HashMap<>();
            Map<String, String> waitingThreads = new LinkedHashMap<>(); // To maintain order for oldest thread
            Map<String, String> lockedObjects = new HashMap<>();
            Map<String, String> threadStates = new HashMap<>();
            Map<String, List<String>> blockingMethods = new HashMap<>();
            Map<String, String> threadTimestamps = new HashMap<>();

            String currentThread = "";
            String currentState = "";
            String waitingOn = "";

            String line;
            while ((line = reader.readLine()) != null) {
                Matcher threadMatcher = THREAD_NAME_PATTERN.matcher(line);
                Matcher stateMatcher = STATE_PATTERN.matcher(line);
                Matcher waitingMatcher = WAITING_LOCK_PATTERN.matcher(line);
                Matcher blockedByMatcher = BLOCKED_BY_PATTERN.matcher(line);
                Matcher lockedObjectMatcher = LOCKED_OBJECT_PATTERN.matcher(line);
                Matcher classCauseMatcher = CLASS_CAUSE_PATTERN.matcher(line);
                Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);

                // Capture Timestamp
                if (timestampMatcher.matches()) {
                    threadTimestamps.put(currentThread, timestampMatcher.group(1));
                }
                // Detect Thread Name
                if (threadMatcher.matches()) {
                    currentThread = threadMatcher.group(1);
                    blockingMethods.put(currentThread, new ArrayList<>());
                }
                // Detect Thread State
                else if (stateMatcher.matches()) {
                    currentState = stateMatcher.group(1);
                    stateCount.put(currentState, stateCount.getOrDefault(currentState, 0) + 1);
                    threadStates.put(currentThread, currentState);
                }
                // Detect Waiting on Lock
                else if (waitingMatcher.matches()) {
                    waitingOn = waitingMatcher.group(1);
                    waitingThreads.put(currentThread, waitingOn);
                }
                // Detect Parking to Wait for Lock
                else if (blockedByMatcher.matches()) {
                    waitingOn = blockedByMatcher.group(1);
                    waitingThreads.put(currentThread, waitingOn);
                }
                // Detect Locked Object
                else if (lockedObjectMatcher.matches()) {
                    lockedObjects.put(currentThread, lockedObjectMatcher.group(1));
                }
                // Collect Full Stack Trace for Root Cause
                else if (classCauseMatcher.matches()) {
                    blockingMethods.get(currentThread).add(classCauseMatcher.group(1));
                }
            }

            // Generate Final Report
            generateReport(stateCount, waitingThreads, lockedObjects, threadStates, blockingMethods, threadTimestamps);
            //generateGraph(waitingThreads, lockedObjects);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void analyzeThreadDumpgraph(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            Map<String, String> waitingThreads = new LinkedHashMap<>();
            Map<String, String> lockedObjects = new HashMap<>();
            Map<String, String> timestamps = new HashMap<>();
            String currentThread = "";
            String waitingOn = "";
            String currentTimestamp = "";
            String line;

            while ((line = reader.readLine()) != null) {
                Matcher threadMatcher = THREAD_NAME_PATTERN.matcher(line);
                Matcher waitingMatcher = WAITING_LOCK_PATTERN.matcher(line);
                Matcher blockedByMatcher = BLOCKED_BY_PATTERN.matcher(line);
                Matcher lockedObjectMatcher = LOCKED_OBJECT_PATTERN.matcher(line);
                Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(line);

                if (timestampMatcher.matches()) {
                    currentTimestamp = timestampMatcher.group(1);
                }

                if (threadMatcher.matches()) {
                    currentThread = threadMatcher.group(1);
                } else if (waitingMatcher.matches() || blockedByMatcher.matches()) {
                    waitingOn = waitingMatcher.matches() ? waitingMatcher.group(1) : blockedByMatcher.group(1);
                    waitingThreads.put(currentThread, waitingOn);
                    timestamps.put(currentThread, currentTimestamp);
                } else if (lockedObjectMatcher.matches()) {
                    lockedObjects.put(currentThread, lockedObjectMatcher.group(1));
                }
            }

            generateGraph(waitingThreads, lockedObjects, timestamps);
            generateReport(waitingThreads, lockedObjects);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generateGraph(Map<String, String> waitingThreads, Map<String, String> lockedObjects, Map<String, String> timestamps) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("thread_dependency_graph.dot"))) {
            writer.write("digraph ThreadGraph {\n");

            for (Map.Entry<String, String> entry : waitingThreads.entrySet()) {
                String from = entry.getKey();
                String lock = entry.getValue();
                String to = lockedObjects.entrySet().stream()
                        .filter(e -> e.getValue().equals(lock))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("UNKNOWN");
                String timestamp = timestamps.getOrDefault(from, "N/A");

                writer.write(String.format("\"%s\" -> \"%s\" [label=\"lock: %s\nTime: %s\"];\n", from, to, lock, timestamp));
            }

            writer.write("}\n");
            System.out.println("✅ Dependency graph saved as thread_dependency_graph.dot");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void generateReport(Map<String, Integer> stateCount,
                                       Map<String, String> waitingThreads,
                                       Map<String, String> lockedObjects,
                                       Map<String, String> threadStates,
                                       Map<String, List<String>> blockingMethods,
                                       Map<String, String> threadTimestamps) {

        System.out.println("=============================================");
        System.out.println("       📊 Complete Thread Dump Analysis      ");
        System.out.println("=============================================");

        // 1. Thread State Summary
        System.out.println("==== Thread State Summary ====");
        stateCount.forEach((state, count) ->
                System.out.println("• " + state + ": " + count));

        // 2. Waiting Thread Analysis
        System.out.println("\n==== Waiting Thread Analysis ====");
        if (waitingThreads.isEmpty()) {
            System.out.println("✅ No waiting threads detected.");
        } else {
            String oldestThread = waitingThreads.keySet().iterator().next();
            for (Map.Entry<String, String> entry : waitingThreads.entrySet()) {
                String thread = entry.getKey();
                String lock = entry.getValue();
                String state = threadStates.getOrDefault(thread, "UNKNOWN");
                List<String> stackTrace = blockingMethods.getOrDefault(thread, Collections.emptyList());
                String rootCause = stackTrace.isEmpty() ? "Unknown" : stackTrace.get(stackTrace.size() - 1);
                String timestamp = threadTimestamps.getOrDefault(thread, "Unknown");

                System.out.println("⚠️ Thread: " + thread);
                System.out.println("   State: " + state);
                System.out.println("   Waiting on Lock: " + lock);
                System.out.println("   Root Blocking Class/Method: " + rootCause);
                System.out.println("   Timestamp: " + timestamp);

                if (thread.equals(oldestThread)) {
                    System.out.println("   🌟 Oldest Waiting Thread");
                }

                System.out.println("   ➡️ Suggested Solutions:");
                System.out.println("      - Review method: " + rootCause);
                System.out.println("      - Consider optimizing synchronization.");
                System.out.println("      - Check for I/O or long wait periods.");
                System.out.println("--------------------------------------");
            }
        }

        System.out.println("\n=============================================");
        System.out.println("          🏁 Analysis Completed              ");
        System.out.println("=============================================");
    }
    
    private static void generateReport(Map<String, String> waitingThreads, Map<String, String> lockedObjects) {
        System.out.println("\n==============================");
        System.out.println("      Thread Dump Report       ");
        System.out.println("==============================\n");

        if (waitingThreads.isEmpty()) {
            System.out.println("✅ No threads are in waiting state.");
        } else {
            System.out.println("⚠️ Threads currently waiting:\n");
            for (Map.Entry<String, String> entry : waitingThreads.entrySet()) {
                String thread = entry.getKey();
                String lock = entry.getValue();
                String cause = lockedObjects.entrySet().stream()
                        .filter(e -> e.getValue().equals(lock))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse("UNKNOWN");

                System.out.printf("- Thread: %s\n  Waiting on Lock: %s\n  Cause: %s\n", thread, lock, cause);

                if (!cause.equals("UNKNOWN")) {
                    System.out.println("  ➡️ Suggested Solution: Check synchronization in " + cause);
                } else {
                    System.out.println("  ➡️ Suggested Solution: Investigate the lock holder.");
                }
            }
        }
    }
}
