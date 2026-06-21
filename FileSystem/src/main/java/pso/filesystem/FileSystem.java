package pso.filesystem;

import pso.filesystem.shell.Shell;

public class FileSystem {

    public static void main(String[] args) {
        System.out.println("File system started.");
        Shell shell = new Shell();
        shell.start();
    }
}
