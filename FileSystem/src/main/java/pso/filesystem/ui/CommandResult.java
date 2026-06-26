/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package pso.filesystem.ui;

/**
 *
 * @author jimen
 */

public final class CommandResult {

    public enum Type {
        SUCCESS,
        ERROR,
        INFO,
        WARNING,
        DIRECTORY,
        FILE,
        DIM
    }

    private final String text;
    private final Type type;

    public CommandResult(String text, Type type) {
        this.text = text;
        this.type = type;
    }

    public static CommandResult success(String text)   { return new CommandResult(text, Type.SUCCESS); }
    public static CommandResult error(String text)     { return new CommandResult(text, Type.ERROR); }
    public static CommandResult info(String text)      { return new CommandResult(text, Type.INFO); }
    public static CommandResult warning(String text)   { return new CommandResult(text, Type.WARNING); }
    public static CommandResult dim(String text)       { return new CommandResult(text, Type.DIM); }
    public static CommandResult directory(String text) { return new CommandResult(text, Type.DIRECTORY); }
    public static CommandResult file(String text)      { return new CommandResult(text, Type.FILE); }

    public String text() { return text; }
    public Type type()   { return type; }
}