package pso.filesystem;
import pso.filesystem.shell.Shell;

public class Main {

    public static void main(String[] args) {
        System.out.println("Welcome bro. :D");

        if (args.length > 1) {
            System.out.println("usage: java pso.filesystem.Main [disk.fs]");
            return;
        }

        Shell shell = new Shell();

        if (args.length == 1) {
            try {
                shell.mountDisk(args[0]);
                System.out.println("mounted disk '" + args[0] + "'");
            } catch (Exception ex) {
                System.out.println("mount failed: " + ex.getMessage());
                return;
            }
        }

        shell.start();
    }
}