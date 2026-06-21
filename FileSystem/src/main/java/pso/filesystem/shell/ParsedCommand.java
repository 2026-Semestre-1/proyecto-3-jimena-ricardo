package pso.filesystem.shell;

import java.util.Arrays;

public record ParsedCommand(String name, String[] options, String[] operands) {
    public void debugPrint() {
        System.out.println("name: " + name + ", options: " + Arrays.toString(options) + ", operands: " + Arrays.toString(operands));
    }
}
