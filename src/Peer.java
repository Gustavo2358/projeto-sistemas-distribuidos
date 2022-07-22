import com.google.gson.Gson;

import java.io.*;
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
    private static String requestedFile;
    private static List<String> peersWithRequestedFile;

    public static void main(String[] args) {
        getPeerInfo();
        handleRequestsThread();
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
                    download();
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
        System.out.println("saiu do loop");
    }

    private static void handleRequestsThread() {
        Thread thread = new Thread(()->{
            try {
                DatagramSocket socket = new DatagramSocket(port);
                loop:
                while (true) {
                    DatagramPacket receivedPacket = listenToIncomingMessages(socket);
                    Mensagem receivedMessage = getMessageFromDatagramPacket(receivedPacket);
                    switch (receivedMessage.getRequestType()) {
                        case "ALIVE":
                            System.out.println("Recebeu alive request");
                            sendAliveOk(receivedPacket);
                            break;
                        case "DOWNLOAD":
                            break;
                        case "END":
                            break loop;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
    }

    private static void sendAliveOk(DatagramPacket receivedPacket) {
        System.out.println("Caiu no case ALIVE");
        Mensagem aliveOk = new Mensagem("ALIVE_OK");
        DatagramPacket aliveOKPacket = getDatagramPacketFromMessage(receivedPacket.getAddress(), receivedPacket.getPort(), aliveOk);
        DatagramSocket socket;
        try {
            socket = new DatagramSocket();
            socket.send(aliveOKPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static DatagramPacket listenToIncomingMessages(DatagramSocket serverSocket) throws IOException {
        byte[] recBuffer = new byte[1024];
        DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
        serverSocket.receive(recPkt);
        return recPkt;
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

    private static void download(){
//        System.out.println(server.getLocalPort());
        InetSocketAddress address = getDownloadPeerAddress();
        Thread downloadThread = new Thread(() -> {
            try {
                //socket
                ServerSocket server = new ServerSocket(0);
                Socket socket = server.accept();
                //in
                InputStream inputStream = socket.getInputStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                //out
                OutputStream outputStream = new FileOutputStream(directoryPath + "/" + requestedFile);
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = bufferedInputStream.read(buffer, 0, buffer.length)) != -1) {
                    if(new String(buffer, 0, 15).equals("DOWNLOAD_NEGADO")) {
                        System.out.println("Download negado");
                        break;
                    }
                    outputStream.write(buffer, 0, read);
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        });
        downloadThread.start();

    }

    private static InetSocketAddress getDownloadPeerAddress() {
        Scanner sc = new Scanner(System.in);
        System.out.println("Digite o ip do peer que irá enviar o arquivo: ");
        InetAddress hostIp = getAddress(sc);
        System.out.println("Digite o número da porta do peer que irá enviar o arquivo: ");
        Integer hostPort = getPort(sc);
        return new InetSocketAddress(hostIp, hostPort);
    }

    private static void search() {
        final Scanner sc = new Scanner(System.in);
        System.out.println("Digite o nome do arquivo:");
        requestedFile = sc.nextLine();
        Thread searchThread = new Thread(()->{
            boolean searchOk = false;
            DatagramSocket socket = getDatagramSocket();
            do {
                sendSearchRequest(socket);
                Mensagem response = receiveResponse(socket);
                peersWithRequestedFile = response.getPeersWithRequestedFiles();
                printSearchInfo();
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
            DatagramSocket leaveSocket = getDatagramSocket();
            do {
                sendLeaveRequest(leaveSocket);
                Mensagem response = receiveResponse(leaveSocket);
                if (response.getRequestType().equals("LEAVE_OK")) {
                    leaveOk = true;
                }
            }while (!leaveOk);
            sendEndRequest(leaveSocket);
        });
        leaveThread.start();
        return true;
    }


    private static void join() {
        //TODO dúvida: deve refazer a requisição eternamente ou por um certa quantidade de vezes?
        Thread joinThread = new Thread(() -> {
            boolean joinOk = false;
            DatagramSocket joinSocket = getDatagramSocket();
            do {
                sendJoinRequest(joinSocket);
                Mensagem response = receiveResponse(joinSocket);
                if (response.getRequestType().equals("JOIN_OK")) {
                    joinOk = true;
                    printJoinInfo();
                }
            }while (!joinOk);
        });
        joinThread.start();
    }

    private static Mensagem receiveResponse(DatagramSocket socket) {
        try {
            byte[] recBuffer = new byte[1024];
            DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
            socket.setSoTimeout(TIME_OUT);
            socket.receive(recPkt);
            return getMessageFromDatagramPacket(recPkt);
        }  catch (SocketTimeoutException e) {
            return new Mensagem("Error");
        } catch (IOException e) {
            e.printStackTrace();
            return new Mensagem("Error");
        }
    }

    private static void sendSearchRequest(DatagramSocket socket){
        try {
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("SEARCH", ip, port, requestedFile);
            DatagramPacket sendPacket = getDatagramPacketFromMessage(serverIpAddress, SERVER_PORT, joinMessage);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private static void sendLeaveRequest(DatagramSocket socket) {
        try {
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("LEAVE", ip, port, filesList);
            DatagramPacket sendPacket = getDatagramPacketFromMessage(serverIpAddress, SERVER_PORT, joinMessage);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    private static void sendEndRequest(DatagramSocket socket) {
        try {
            InetAddress localHost = InetAddress.getByName("127.0.0.1");
            Mensagem joinMessage = new Mensagem("END");
            DatagramPacket sendPacket = getDatagramPacketFromMessage(localHost, port, joinMessage);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendJoinRequest(DatagramSocket socket) {
        try {
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("JOIN", ip, port, filesList);
            DatagramPacket sendPacket = getDatagramPacketFromMessage(serverIpAddress, SERVER_PORT, joinMessage);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
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

    private static void printSearchInfo(){
        try {
            System.out.print("peers com arquivo solicitado: ");
            peersWithRequestedFile
                    .forEach(p -> System.out.print(p + " "));
            System.out.println();
        } catch (NullPointerException e){
            System.out.println("sem informações do peer");
        }
    }

    private static void getPeerInfo() {
        Scanner sc = new Scanner(System.in);
        System.out.println("Endereço de Ip:");
        ip = getAddress(sc);
        System.out.println("Porta:");
        port = getPort(sc);
        System.out.println("Nome da pasta: ");
        selectDirectory(sc);
        filesList = getFilesInDirectory(directoryPath);
    }

    private static InetAddress getAddress(Scanner sc) {
        String ipString = sc.nextLine();
        try {
            return InetAddress.getByName(ipString);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            System.out.println("Endereço inválido, digite novamente:");
            return getAddress(sc);
        }
    }

    private static Integer getPort(Scanner sc) {
        return Integer.parseInt(sc.nextLine());
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

    private static DatagramSocket getDatagramSocket() {
        try {
            return new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            return null;
        }
    }

}
