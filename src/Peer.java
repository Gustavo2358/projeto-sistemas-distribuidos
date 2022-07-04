import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Peer {

    private static InetAddress ip;
    private static int port;
    private static Path directoryPath;
    private static List<String> filesList;

    public static void main(String[] args) {
        getInfo();

        //String com as infos
        StringBuilder stringBuilder = new StringBuilder();
        for(String f : filesList)
            stringBuilder.append(f).append(" ");
        String infos = String.format("Sou peer %s:%s com arquivos %s",
                ip.toString().substring(1), port, stringBuilder);
        System.out.println(infos);

    }

    private static void getInfo() {
        Scanner sc = new Scanner(System.in);
        System.out.println("Endereço de Ip:");
        String ipString = sc.nextLine();
        try {
            ip = InetAddress.getByName(ipString);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        System.out.println("Porta:");
        port = Integer.parseInt(sc.nextLine());
        System.out.println("Nome da pasta: ");
        String directoryName = sc.nextLine();
        createFolder(directoryName);
        directoryPath = Paths.get(directoryName);
        filesList = listFilesInFolder(directoryPath);

    }

    private static List<String> listFilesInFolder(Path directoryPath) {
        try (Stream<Path> files = Files.walk(Paths.get(directoryPath.toString()))) {
            return  files
                    .filter(Files::isRegularFile)
                    .map(e -> e.getFileName().toString())
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }

    }

    private static void createFolder(String DirectoryName){
        try {
            if (!Files.isDirectory(Paths.get(DirectoryName))) {
                Files.createDirectory(Paths.get(DirectoryName));
            }
        } catch (java.io.IOException e){
            e.printStackTrace();
        }
    }
}
