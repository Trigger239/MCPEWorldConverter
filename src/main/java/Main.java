import converter.Converter;

import java.io.File;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        String PATH = System.getProperty("user.dir");
        //String levelDBWorldPath = PATH + "/" + args[0];
        Scanner s = new Scanner(System.in);
        System.out.println("Enter LevelDB world folder name:");
        String levelDBWorldPath = PATH + "/" + s.nextLine();

        File levelDBWorldFolder = new File(levelDBWorldPath);
        levelDBWorldFolder.mkdir();

        File anvilWorldFolder = new File(levelDBWorldPath + "_Anvil");
        anvilWorldFolder.mkdir();

        Converter.convertWorld(levelDBWorldFolder, anvilWorldFolder);

    }
}

