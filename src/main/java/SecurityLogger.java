import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import java.security.SecureRandom;

public class SecurityLogger implements AutoCloseable {
    private static final String LOG_DIRECTORY = "security_logs";
    private static final String LOG_FILE_PREFIX = "bdl_security_";
    private final BlockingQueue<String> logQueue;
    private final Thread loggingThread;
    private final Encryptor encryptor;
    private final SecretKey logKey;
    private volatile boolean running;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    public SecurityLogger(Encryptor encryptor) throws Exception {
        this.encryptor = encryptor;
        this.logQueue = new LinkedBlockingQueue<>();
        this.logKey = encryptor.generateAESKey();
        this.running = true;

        // Create log directory if it doesn't exist
        Path logDir = Paths.get(LOG_DIRECTORY);
        if (!Files.exists(logDir)) {
            Files.createDirectories(logDir);
        }

        // Start background logging thread with error handling
        this.loggingThread = new Thread(this::processLogQueue, "SecurityLogger-Thread");
        this.loggingThread.setDaemon(true);
        this.loggingThread.setUncaughtExceptionHandler((t, e) -> handleLoggingError(e));
        this.loggingThread.start();
    }

    private void handleLoggingError(Throwable error) {
        System.err.println("Security logging error: " + error.getMessage());
        // Attempt to write error to a separate error log file
        try {
            Path errorLogPath = Paths.get(LOG_DIRECTORY, "security_logger_errors.log");
            String errorEntry = String.format("[%s] Error: %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                error.getMessage());
            Files.write(errorLogPath, errorEntry.getBytes(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // If we can't even write to error log, print to stderr as last resort
            System.err.println("Failed to write to error log: " + e.getMessage());
        }
    }

    public void logSecurityEvent(String username, String action, String details) {
        if (!running) {
            throw new IllegalStateException("Security logger has been shut down");
        }

        try {
            LocalDateTime timestamp = LocalDateTime.now();
            String logEntry = formatLogEntry(timestamp, username, action, details);
            String encryptedEntry = encryptor.encryptGCM(logEntry, logKey);

            boolean offered = false;
            for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS && !offered; attempt++) {
                offered = logQueue.offer(encryptedEntry);
                if (!offered) {
                    Thread.sleep(RETRY_DELAY_MS);
                }
            }

            if (!offered) {
                handleLoggingError(new IOException("Failed to queue log entry after " + MAX_RETRY_ATTEMPTS + " attempts"));
            }
        } catch (Exception e) {
            handleLoggingError(e);
        }
    }

    private String formatLogEntry(LocalDateTime timestamp, String username, String action, String details) {
        return String.format("[%s] User: %s, Action: %s, Details: %s",
            timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            username,
            action,
            details
        );
    }

    private void processLogQueue() {
        while (running || !logQueue.isEmpty()) {
            try {
                String encryptedEntry = logQueue.poll();
                if (encryptedEntry != null) {
                    writeToLogFile(encryptedEntry);
                } else {
                    Thread.sleep(100); // Prevent CPU spinning
                }
            } catch (Exception e) {
                handleLoggingError(e);
            }
        }
    }

    private void writeToLogFile(String encryptedEntry) throws IOException {
        String filename = LOG_FILE_PREFIX +
            LocalDateTime.now().format(DateTimeFormatter.ISO_DATE) + ".log";
        Path logFile = Paths.get(LOG_DIRECTORY, filename);

        int attempts = 0;
        boolean written = false;

        while (!written && attempts < MAX_RETRY_ATTEMPTS) {
            try {
                Files.write(logFile,
                    (encryptedEntry + System.lineSeparator()).getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                    StandardOpenOption.SYNC // Ensure immediate write to disk
                );
                written = true;
            } catch (IOException e) {
                attempts++;
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while retrying log write", ie);
                }
            }
        }
    }

    public String[] readSecurityLogs(LocalDateTime startDate, LocalDateTime endDate) throws Exception {
        if (!running) {
            throw new IllegalStateException("Security logger has been shut down");
        }

        return Files.list(Paths.get(LOG_DIRECTORY))
            .filter(path -> {
                String filename = path.getFileName().toString();
                if (!filename.startsWith(LOG_FILE_PREFIX) || !filename.endsWith(".log")) {
                    return false;
                }
                try {
                    LocalDateTime fileDate = LocalDateTime.parse(
                        filename.replace(LOG_FILE_PREFIX, "").replace(".log", ""),
                        DateTimeFormatter.ISO_DATE
                    );
                    return !fileDate.toLocalDate().isBefore(startDate.toLocalDate()) &&
                           !fileDate.toLocalDate().isAfter(endDate.toLocalDate());
                } catch (Exception e) {
                    return false;
                }
            })
            .flatMap(path -> {
                try {
                    return Files.lines(path)
                        .map(encryptedLine -> {
                            try {
                                return encryptor.decryptGCM(encryptedLine, logKey);
                            } catch (Exception e) {
                                handleLoggingError(e);
                                return "Error decrypting log entry: " + e.getMessage();
                            }
                        });
                } catch (IOException e) {
                    handleLoggingError(e);
                    return Stream.empty();
                }
            })
            .toArray(String[]::new);
    }

    @Override
    public void close() {
        shutdown();
    }

    public void shutdown() {
        running = false;
        try {
            // Give the logging thread some time to process remaining entries
            loggingThread.join(5000);

            // If there are still entries in the queue, process them
            while (!logQueue.isEmpty()) {
                String encryptedEntry = logQueue.poll();
                if (encryptedEntry != null) {
                    writeToLogFile(encryptedEntry);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleLoggingError(new IOException("Interrupted during shutdown", e));
        } catch (IOException e) {
            handleLoggingError(e);
        } finally {
            if (loggingThread.isAlive()) {
                loggingThread.interrupt();
            }
        }
    }
}
