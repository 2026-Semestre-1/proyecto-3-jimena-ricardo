package pso.filesystem.terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class TerminalRawMode implements AutoCloseable {

    private final String originalSettings;

    private TerminalRawMode(String originalSettings) {
        this.originalSettings = originalSettings;
    }

    public static TerminalRawMode enter() throws IOException {
        String originalSettings = runAndCapture("stty -g < /dev/tty").trim();
        run("stty raw -echo < /dev/tty");
        return new TerminalRawMode(originalSettings);
    }

    @Override
    public void close() throws IOException {
        run("stty " + originalSettings + " < /dev/tty");
    }

    private static void run(String command) throws IOException {
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("command failed with exit code " + exitCode + ": " + command);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running command: " + command, ex);
        }
    }

    private static String runAndCapture(String command) throws IOException {
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("command failed with exit code " + exitCode + ": " + command);
            }

            return output.toString();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while running command: " + command, ex);
        }
    }
}
