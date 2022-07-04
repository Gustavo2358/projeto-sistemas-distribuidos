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
        boolean leave = false;
        do {
            int opt = menu();
            switch (opt) {
                case 1:
                    join();
                    break;
                case 2:
                    System.out.println("em construção");
                    break;
                case 3:
                    System.out.println("em construção");
                    break;
                case 4:
                    System.out.println("Leaving...");
                    leave = true;
                    break;
                default:
                    System.out.println("Digíte uma opção válida (entre 1 e 4)");
                    break;
            }
        } while (!leave);

    }

    private static void join() {
        getInfo();
        //String com as infos
        StringBuilder stringArquivos = new StringBuilder();
        for(String f : filesList)
            stringArquivos.append(f).append(" ");
        String infos = String.format("Sou peer %s:%s com arquivos %s",
                ip.toString().substring(1), port, stringArquivos);
        System.out.println(infos);
    }

    private static int menu() {
        System.out.println("Digíte a opção desejada:");
        System.out.println("1 - JOIN");
        System.out.println("2 - SEARCH");
        System.out.println("3 - DOWNLOAD");
        System.out.println("4 - LEAVE");
        Scanner sc = new Scanner(System.in);
        String optString;
        int opt;
        do {
            optString = sc.nextLine();
            opt = validate(optString);
        } while( opt == -1);
        return opt;
    }

    private static int validate(String optString) {
        int opt;
        try {
            opt = Integer.parseInt(optString);
        }catch (NumberFormatException e){
            System.out.println("Digíte um valor numérico");
            return -1;
        }
        return opt;
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
        selectDirectory(sc);
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

    private static void selectDirectory(Scanner sc){
        do {
            System.out.println("Nome da pasta: ");
            String directoryName = sc.nextLine();
            try {
                if (!Files.isDirectory(Paths.get(directoryName))) {
                    System.out.println("O diretório ainda não existe, criando novo diretório...");
                    Files.createDirectory(Paths.get(directoryName));
                    System.out.println("Diretório criado com sucesso.");
                    directoryPath = Paths.get(directoryName);
                    return;
                }
                System.out.println("Diretório selecionado.");
            } catch (java.io.IOException e) {
                System.out.println("Erro para criar...digite um caminho válido");
            }
        }while(true);
    }
}
