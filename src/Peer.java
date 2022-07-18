import com.google.gson.Gson;

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
//TODO no timeout do search é retornado um null ao invés da lista
public class Peer {

    private final static int TIME_OUT = 2000;
    private final static String SERVER_IP = "127.0.0.1";
    private final static int SERVER_PORT = 10098;

    private static InetAddress ip;
    private static int port;
    private static Path directoryPath;
    private static List<String> filesList;

    public static void main(String[] args) {
        getPeerInfo();
        boolean leaveOk = false;
        do {
            int opt = menu();
            switch (opt) {
                case 1:
                    join();
                    break;
                case 2:
                    search();
                    break;
                case 3:
                    System.out.println("em construção");
                    break;
                case 4:
                    System.out.println("Leaving...");
                    leaveOk = leave();
                    break;
                default:
                    System.out.println("Digite uma opção válida (entre 1 e 4)");
                    break;
            }
        } while (!leaveOk);

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

    private static void search() {
        final Scanner sc = new Scanner(System.in);
        System.out.println("Digite o nome do arquivo:");
        String requestedFile = sc.nextLine();
        Thread searchThread = new Thread(()->{
            boolean searchOk = false;
            do {
                sendSearchRequest(requestedFile);
                Mensagem response = receiveResponse();
                printSearchInfo(response);
                if (response.getRequestType().equals("SEARCH_OK")) {
                    searchOk = true;
                }
            }while (!searchOk);
        });
        searchThread.start();
    }

    private static boolean leave() {
        Thread leaveThread = new Thread(()->{
            boolean leaveOk = false;
            do {
                sendLeaveRequest();
                Mensagem response = receiveResponse();
                if (response.getRequestType().equals("LEAVE_OK")) {
                    leaveOk = true;
                }
            }while (!leaveOk);
        });
        leaveThread.start();
        return true;
    }

    private static void join() {
        //TODO dúvida: join deve ser totalmente assíncrono ou não? caso não, as outras operações poderão ser feitas sem fazer o join
        Thread joinThread = new Thread(() -> {
            boolean joinOk = false;
            //TODO dúvida: deve refazer a requisição eternamente ou por um certa quantidade de vezes?
            do {
                sendJoinRequest();
                Mensagem response = receiveResponse();
                if (response.getRequestType().equals("JOIN_OK")) {
                    joinOk = true;
                    printJoinInfo();
                }
            }while (!joinOk);
        });
        joinThread.start();
    }

    private static Mensagem receiveResponse() {
        try (DatagramSocket clientSocket = new DatagramSocket(port)){
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
            clientSocket.setSoTimeout(TIME_OUT);
            clientSocket.receive(recPkt);
            return getMessageFromDatagramPacket(recPkt);
        }  catch (SocketTimeoutException e) {
            return new Mensagem("Error");
        } catch (IOException e) {
            e.printStackTrace();
            return new Mensagem("Error");
        }
    }

    private static void sendSearchRequest(String requestedFile){
        try(DatagramSocket clientSocket = new DatagramSocket(port)) {
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("SEARCH", ip, port, requestedFile);
            DatagramPacket sendPacket = getDatagramPacketFromMessage(serverIpAddress, SERVER_PORT, joinMessage);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private static void sendLeaveRequest() {
        try (DatagramSocket clientSocket = new DatagramSocket(port)){
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("LEAVE", ip, port, filesList);
            DatagramPacket sendPacket = getDatagramPacketFromMessage(serverIpAddress, SERVER_PORT, joinMessage);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private static void sendJoinRequest() {
        try(DatagramSocket clientSocket = new DatagramSocket(port)) {
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("JOIN", ip, port, filesList);
            DatagramPacket sendPacket = getDatagramPacketFromMessage(serverIpAddress, SERVER_PORT, joinMessage);
            clientSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }

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

    private static Mensagem getMessageFromDatagramPacket(DatagramPacket recPkt) {
        String recData = new String(recPkt.getData(), recPkt.getOffset(), recPkt.getLength());
        Gson gson = new Gson();
        return gson.fromJson(recData, Mensagem.class);
    }

    private static DatagramPacket getDatagramPacketFromMessage(InetAddress receiverIpAddress,int receiverPort, Mensagem Message) {
        Gson gson = new Gson();
        String messageJson = gson.toJson(Message);
        //TODO debug, apagar depois
        System.out.println("Json enviado para o Servidor: " + messageJson);
        byte[] sendData = messageJson.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(sendData, sendData.length, receiverIpAddress, receiverPort );
    }

    private static void printJoinInfo() {
        //String com as infos
        StringBuilder stringArquivos = new StringBuilder();
        for(String f : filesList)
            stringArquivos.append(f).append(" ");
        String infos = String.format("Sou peer %s:%s com arquivos %s",
                ip.toString(), port, stringArquivos);
        System.out.println(infos);
    }

    private static void printSearchInfo(Mensagem message){
        try {
            System.out.print("peers com arquivo solicitado: ");
            message.getPeersWithRequestedFiles()
                    .forEach(p -> System.out.print(p + " "));
            System.out.println();
        } catch (NullPointerException e){
            System.out.println("sem informações do peer");
        }
    }

    private static void getPeerInfo() {
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
                    //TODO debug, apagar mensagens no console que não estão na requisição do professor
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
