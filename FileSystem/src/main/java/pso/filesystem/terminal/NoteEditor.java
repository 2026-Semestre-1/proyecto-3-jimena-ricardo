package pso.filesystem.terminal;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public final class NoteEditor {

    private static final int CTRL_X = 24;
    private static final int ESC = 27;
    private static final int BACKSPACE = 127;
    private static final int CTRL_H = 8;
    private static final int ENTER_CR = 13;
    private static final int ENTER_LF = 10;

    private static final String ALT_SCREEN_ON = "\033[?1049h";
    private static final String ALT_SCREEN_OFF = "\033[?1049l";
    private static final String CLEAR_SCREEN = "\033[2J";
    private static final String CURSOR_HOME = "\033[H";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";
    private static final String CLEAR_TO_END_OF_LINE = "\033[K";

    private final String filePath;
    private final List<StringBuilder> lines = new ArrayList<>();

    private int cursorRow;
    private int cursorColumn;
    private int rowOffset;
    private int columnOffset;

    public NoteEditor(String filePath) {
        this.filePath = filePath;
    }

    public EditResult edit(byte[] initialContent) throws IOException {
        loadAscii(initialContent);

        try (TerminalRawMode ignored = TerminalRawMode.enter()) {
            PrintStream out = System.out;
            out.print(ALT_SCREEN_ON);
            out.print(HIDE_CURSOR);
            out.flush();

            try {
                TerminalSize terminalSize = TerminalSize.detect();
                while (true) {
                    render(out, terminalSize);

                    int key = System.in.read();
                    if (key == CTRL_X) {
                        Boolean save = askSave(out, terminalSize);
                        if (save != null) {
                            return new EditResult(save, toAsciiBytes());
                        }
                    } else {
                        handleKey(key);
                    }
                }
            } finally {
                out.print(SHOW_CURSOR);
                out.print(ALT_SCREEN_OFF);
                out.flush();
            }
        }
    }

    public record EditResult(boolean save, byte[] content) {
    }

    private void loadAscii(byte[] bytes) {
        lines.clear();
        lines.add(new StringBuilder());
        cursorRow = 0;
        cursorColumn = 0;
        rowOffset = 0;
        columnOffset = 0;

        if (bytes == null) {
            return;
        }

        for (byte raw : bytes) {
            int value = raw & 0xFF;

            if (value == '\r') {
                continue;
            }
            if (value == '\n') {
                lines.add(new StringBuilder());
            } else if (value >= 32 && value <= 126) {
                lines.get(lines.size() - 1).append((char) value);
            } else {
                lines.get(lines.size() - 1).append('?');
            }
        }
    }

    private Boolean askSave(PrintStream out, TerminalSize terminalSize) throws IOException {
        int promptRow = terminalSize.rows();

        out.printf("\033[%d;1H", promptRow);
        out.print(CLEAR_TO_END_OF_LINE);
        out.print("Save changes? y/n: ");
        out.flush();

        while (true) {
            int key = System.in.read();
            if (key == 'y' || key == 'Y') {
                return true;
            }
            if (key == 'n' || key == 'N') {
                return false;
            }
            if (key == ESC) {
                return null;
            }
        }
    }

    private void handleKey(int key) throws IOException {
        if (key == ENTER_CR || key == ENTER_LF) {
            insertNewLine();
        } else if (key == BACKSPACE || key == CTRL_H) {
            backspace();
        } else if (key == ESC) {
            handleEscapeSequence();
        } else if (key >= 32 && key <= 126) {
            insertCharacter((char) key);
        }
    }

    private void handleEscapeSequence() throws IOException {
        int second = System.in.read();
        if (second != '[') {
            return;
        }

        int third = System.in.read();
        switch (third) {
            case 'A' -> moveUp();
            case 'B' -> moveDown();
            case 'C' -> moveRight();
            case 'D' -> moveLeft();
            default -> {
            }
        }
    }

    private void insertCharacter(char character) {
        lines.get(cursorRow).insert(cursorColumn, character);
        cursorColumn++;
    }

    private void insertNewLine() {
        StringBuilder currentLine = lines.get(cursorRow);
        String remaining = currentLine.substring(cursorColumn);
        currentLine.delete(cursorColumn, currentLine.length());

        lines.add(cursorRow + 1, new StringBuilder(remaining));
        cursorRow++;
        cursorColumn = 0;
    }

    private void backspace() {
        if (cursorColumn > 0) {
            lines.get(cursorRow).deleteCharAt(cursorColumn - 1);
            cursorColumn--;
            return;
        }

        if (cursorRow > 0) {
            int previousLength = lines.get(cursorRow - 1).length();
            lines.get(cursorRow - 1).append(lines.get(cursorRow));
            lines.remove(cursorRow);
            cursorRow--;
            cursorColumn = previousLength;
        }
    }

    private void moveUp() {
        if (cursorRow > 0) {
            cursorRow--;
            clampCursorColumn();
        }
    }

    private void moveDown() {
        if (cursorRow < lines.size() - 1) {
            cursorRow++;
            clampCursorColumn();
        }
    }

    private void moveLeft() {
        if (cursorColumn > 0) {
            cursorColumn--;
        } else if (cursorRow > 0) {
            cursorRow--;
            cursorColumn = lines.get(cursorRow).length();
        }
    }

    private void moveRight() {
        if (cursorColumn < lines.get(cursorRow).length()) {
            cursorColumn++;
        } else if (cursorRow < lines.size() - 1) {
            cursorRow++;
            cursorColumn = 0;
        }
    }

    private void clampCursorColumn() {
        cursorColumn = Math.min(cursorColumn, lines.get(cursorRow).length());
    }

    private void render(PrintStream out, TerminalSize size) {
        int rows = size.rows();
        int columns = size.columns();

        updateScroll(rows, columns);

        out.print(HIDE_CURSOR);
        out.print(CURSOR_HOME);
        out.print(CLEAR_SCREEN);

        for (int screenRow = 0; screenRow < rows; screenRow++) {
            int fileRow = rowOffset + screenRow;
            out.printf("\033[%d;1H", screenRow + 1);

            if (fileRow < lines.size()) {
                String line = lines.get(fileRow).toString();
                if (columnOffset < line.length()) {
                    int end = Math.min(line.length(), columnOffset + columns);
                    out.print(line.substring(columnOffset, end));
                }
            }

            out.print(CLEAR_TO_END_OF_LINE);
        }

        int terminalCursorRow = cursorRow - rowOffset + 1;
        int terminalCursorColumn = cursorColumn - columnOffset + 1;

        out.printf("\033[%d;%dH", terminalCursorRow, terminalCursorColumn);
        out.print(SHOW_CURSOR);
        out.flush();
    }

    private void updateScroll(int rows, int columns) {
        if (cursorRow < rowOffset) {
            rowOffset = cursorRow;
        } else if (cursorRow >= rowOffset + rows) {
            rowOffset = cursorRow - rows + 1;
        }

        if (cursorColumn < columnOffset) {
            columnOffset = cursorColumn;
        } else if (cursorColumn >= columnOffset + columns) {
            columnOffset = cursorColumn - columns + 1;
        }
    }

    private byte[] toAsciiBytes() {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                result.append('\n');
            }
            result.append(lines.get(i));
        }

        byte[] bytes = new byte[result.length()];
        for (int i = 0; i < result.length(); i++) {
            char character = result.charAt(i);
            bytes[i] = character <= 127 ? (byte) character : (byte) '?';
        }

        return bytes;
    }

    private record TerminalSize(int rows, int columns) {

        static TerminalSize detect() {
            try {
                Process process = new ProcessBuilder("sh", "-c", "stty size < /dev/tty")
                        .redirectErrorStream(true)
                        .start();

                String output = new String(process.getInputStream().readAllBytes()).trim();
                int exitCode = process.waitFor();

                if (exitCode == 0 && !output.isBlank()) {
                    String[] parts = output.split("\\s+");
                    return new TerminalSize(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            } catch (Exception ignored) {
            }

            return new TerminalSize(24, 80);
        }
    }
}
