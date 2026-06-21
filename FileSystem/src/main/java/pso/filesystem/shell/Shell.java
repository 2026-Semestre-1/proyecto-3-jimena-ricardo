package pso.filesystem.shell;

import java.util.Scanner;
import java.util.Arrays;

public class Shell {

    public void start() {
        Scanner sc = new Scanner(System.in);
        String command;
        do {
            System.out.print("ricardo@fs> ");
            command = sc.nextLine();
            ParsedCommand parsedCommand = parse(command);
            parsedCommand.debugPrint();
        } while (!command.equals("exit"));
    }

    private ParsedCommand parse(String input) {
        String trimmed = input == null ? "" : input.trim();

        if (trimmed.isEmpty()) {
            return new ParsedCommand("", null, null);
        }

        String[] parts = trimmed.split("\\s+");
        String name = parts[0];
        int lastOptionIndex = -1;
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].startsWith("-")) {
                lastOptionIndex = i;
            }
        }
        String[] options = lastOptionIndex == -1 ? null : Arrays.copyOfRange(parts, 1, lastOptionIndex + 1);
        String[] operands = lastOptionIndex == -1 ? Arrays.copyOfRange(parts, 1, parts.length) : Arrays.copyOfRange(parts, lastOptionIndex + 1, parts.length);
        return new ParsedCommand(name, options, operands);
    }
}
