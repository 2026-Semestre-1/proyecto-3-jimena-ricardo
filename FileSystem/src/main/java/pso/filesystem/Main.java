package pso.filesystem;

import pso.ui.MainFrame;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) {
        /* Sorry Ricardo, soon you can use the UI
        System.out.println("Welcome bro. :D");
        Shell shell = new Shell();
        shell.start();*/
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}