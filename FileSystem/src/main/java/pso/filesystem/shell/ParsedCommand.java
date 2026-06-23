package pso.filesystem.shell;

public record ParsedCommand(String name, String[] options, String[] operands) {
}
