import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
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
                    System.out.println("Digite uma opção válida (entre 1 e 4)");
                    break;
            }
        } while (!leave);

    }

    private static int menu() {
        System.out.println("Digite a opção desejada:");
        System.out.println("1 - JOIN");
        System.out.println("2 - SEARCH");
        System.out.println("3 - DOWNLOAD");
        System.out.println("4 - LEAVE");
        Scanner sc = new Scanner(System.in);
        String optString;
        int opt;
        do {
            optString = sc.nextLine();
            opt = validateMenuOption(optString);
        } while( opt == -1);
        return opt;
    }

    private static int validateMenuOption(String optString) {
        int opt;
        try {
            opt = Integer.parseInt(optString);
        }catch (NumberFormatException e){
            System.out.println("Digite um valor numérico");
            return -1;
        }
        return opt;
    }

    private static void join() {
        getInfo();
        sendJoinRequest();
        receiveJoinResponse();
        printInfo();
    }

    private static void receiveJoinResponse() {
        try {
            DatagramSocket clientSocket = new DatagramSocket(port);
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
            clientSocket.receive(recPkt);
            System.out.println(new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength()));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendJoinRequest() {
        try {
            DatagramSocket clientSocket = new DatagramSocket(port);
            InetAddress serverIpAddress = InetAddress.getByName("127.0.0.1");
            //TODO enviar objeto Mensagem ao invés da string

            StringBuilder sb = new StringBuilder();
            sb.append("JOIN").append(" ").append(ip.toString()).append(" ").append(port);
            for(String file: filesList)
                sb.append(" ").append(file);
            byte[] sendData = sb.toString().getBytes(StandardCharsets.UTF_8);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverIpAddress, 10098 );
            clientSocket.send(sendPacket);
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }

    }

    private static void printInfo() {
        //String com as infos
        StringBuilder stringArquivos = new StringBuilder();
        for(String f : filesList)
            stringArquivos.append(f).append(" ");
        String infos = String.format("Sou peer %s:%s com arquivos %s",
                ip.toString(), port, stringArquivos);
        System.out.println(infos);
    }

    private static void getInfo() {
        Scanner sc = new Scanner(System.in);
        System.out.println("Endereço de Ip:");
        getAddress(sc);
        System.out.println("Porta:");
        getPort(sc);
        System.out.println("Nome da pasta: ");
        selectDirectory(sc);
        filesList = getFilesInDirectory(directoryPath);
    }

    private static void getAddress(Scanner sc) {
        String ipString = sc.nextLine();
        try {
            ip = InetAddress.getByName(ipString);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private static void getPort(Scanner sc) {
        port = Integer.parseInt(sc.nextLine());
    }

    private static void selectDirectory(Scanner sc){
        do {
            String directoryName = sc.nextLine();
            directoryPath = Paths.get(directoryName);
            try {
                if (!Files.isDirectory(Paths.get(directoryName))) {
                    System.out.println("O diretório ainda não existe, criando novo diretório...");
                    Files.createDirectory(Paths.get(directoryName));
                    System.out.println("Diretório criado com sucesso.");
                    return;
                }
                System.out.println("Diretório selecionado.");
                return;
            } catch (java.io.IOException e) {
                System.out.println("Erro para criar...digite um caminho válido");
            }
        }while(true);
    }

    private static List<String> getFilesInDirectory(Path directoryPath) {
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

}
