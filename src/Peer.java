import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
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
        listenToDownloadRequests();
        while (true) {
            int opt = menu();
            switch (opt) {
                case 1:
                    join();
                    break;
                case 2:
                    search();
                    break;
                case 3:
                    download();
                    break;
                case 4:
                    System.out.println("Leaving...");
                    leave();
                    break;
                default:
                    System.out.println("Digite uma opção válida (entre 1 e 4)");
                    break;
            }
        }
    }

    private static void alive(DatagramSocket socket) {
        Thread thread = new Thread(()->{
            try {
                while (true) {
                    DatagramPacket receivedPacket = listenToIncomingMessages(socket);
                    Mensagem receivedMessage = getMessageFromDatagramPacket(receivedPacket);
                    if ("ALIVE".equals(receivedMessage.getRequestType())) {
                        sendAliveOk(receivedPacket);
                    } else if ("END".equals(receivedMessage.getRequestType())) {
                        break;
                    }
                }
            }catch (SocketTimeoutException e){
                System.out.println("saiu do alive");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();


    }

    private static void sendAliveOk(DatagramPacket receivedPacket) {
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
        serverSocket.setSoTimeout(32000);
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


    //Peer atuando como Servidor
    private static void listenToDownloadRequests(){
        Thread listenThread = new Thread(() -> {

            try {
                //escuta conexões TCP no IP e porta que o usuário digitou para identificar o Peer
                ServerSocket serverSocket = new ServerSocket(port);
                while(true) {
                    Socket socket = serverSocket.accept();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String jsonMessage = reader.readLine();
                        Gson gson = new Gson();
                        Mensagem message = gson.fromJson(jsonMessage, Mensagem.class);
                        if (message.getRequestType().equals("DOWNLOAD")){

                            Thread uploadThread = new Thread(() -> {
                                try {
                                    Random random = new Random();
                                    if(random.nextBoolean()) {
                                        sendFile(message.getRequestedFile(), socket);
                                    } else {
                                        denyDownload(socket);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                            uploadThread.start();
                        }
                        else if(message.getRequestType().equals("END")){
                            //TODO Implementar end
                            break;
                        }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenThread.start();
    }


    private static void denyDownload(Socket socket) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] buffer = "DOWNLOAD_NEGADO".getBytes(StandardCharsets.UTF_8);
        outputStream.write(buffer,0,buffer.length);
    }

    private static void sendFile(String requestedFile, Socket socket) throws IOException {
        InputStream inputStream = new FileInputStream(directoryPath.toString().concat("/").concat(requestedFile));
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
        OutputStream outputStream = socket.getOutputStream();
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = bufferedInputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        socket.close();

        //TODO debug, apagar depois
        System.out.println("ARQUIVO ENVIADO");

    }

    //Peer atuando como cliente
    private static void download(){
        InetSocketAddress address = getDownloadPeerAddress();

        class DownloadThread extends Thread{
            private InetSocketAddress address;
            public DownloadThread(InetSocketAddress address){
                this.address = address;
            }

            @Override
            public void run() {

                Queue<String> availablePeersQueue = new LinkedList<>(peersWithRequestedFile);

                //pega endereço do peer selecionado pelo usuário
                availablePeersQueue.remove(address.toString());

                boolean downloadOk = false;
                while(!downloadOk) {
                    try {
                        //send download request
                        Socket socket = new Socket(address.getAddress(), address.getPort());
                        OutputStream outputStream = socket.getOutputStream();
                        DataOutputStream writer = new DataOutputStream(outputStream);
                        Mensagem message = new Mensagem("DOWNLOAD", requestedFile);
                        Gson gson = new Gson();
                        String jsonMessage = gson.toJson(message);
                        writer.writeBytes(jsonMessage.concat("\n"));
                        //
                        downloadOk = getDownloadResponse(socket);
                        if(!downloadOk){
                            if(peersWithRequestedFile.size() == 1){
                                System.out.println("Download negado, refazendo o pedido em 5 segundos");
                                Thread.sleep(5000);
                            }
                            else if(!availablePeersQueue.isEmpty()) {
                                socket.close();
                                InetSocketAddress oldAddress = address;
                                address = getInetSocketAddressFromString(availablePeersQueue.poll());
                                System.out.printf("peer %s negou o download, pedindo agora para o peer %s%n", oldAddress, address);
                            } else {
                                availablePeersQueue = new LinkedList<>(peersWithRequestedFile);
                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        DownloadThread downloadThread = new DownloadThread(address);
        downloadThread.start();
    }

    private static InetSocketAddress getInetSocketAddressFromString(String address) {
        String[] split = address.substring(1).split(":");
        return new InetSocketAddress(split[0], Integer.parseInt(split[1]));
    }

    private static boolean getDownloadResponse(Socket socket){
            try {
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
                        outputStream.close();
                        Files.delete(Paths.get(String.valueOf(directoryPath), requestedFile));
                        return false;
                    }
                    outputStream.write(buffer, 0, read);
                }
                update();
                socket.close();
                return true;
            }catch (IOException e){
                e.printStackTrace();
                return false;
            }
    }

    private static void update() {

        Thread updateThread = new Thread(() -> {
            filesList = getFilesInDirectory(directoryPath);
            boolean updateOk = false;
            DatagramSocket updateSocket = getDatagramSocket();
            do {
                sendUpdateRequest(updateSocket);
                Mensagem response = receiveResponse(updateSocket);
                if (response.getRequestType().equals("UPDATE_OK")) {
                    updateOk = true;
                    //TODO debug, apagar depois
                    System.out.println("UPDATE_OK");
                }
            }while (!updateOk);
        });
        updateThread.start();

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

    private static void leave() {
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

            sendEndRequest();
        });
        leaveThread.start();
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
            alive(joinSocket);
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

    //TODO tem que se comunicar com o socket TCP
    private static void sendEndRequest() {
        try {

            Socket socket = new Socket("127.0.0.1", port);
            OutputStream outputStream = socket.getOutputStream();
            DataOutputStream writer = new DataOutputStream(outputStream);
            Mensagem message = new Mensagem("END");
            Gson gson = new Gson();
            String jsonMessage = gson.toJson(message);
            writer.writeBytes(jsonMessage.concat("\n"));
            socket.close();

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

    private static void sendUpdateRequest(DatagramSocket socket) {
        try {
            InetAddress serverIpAddress = InetAddress.getByName(SERVER_IP);
            Mensagem joinMessage = new Mensagem("UPDATE", ip, port, filesList);
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
        //TODO debug, Json apagar depois
        //System.out.println("Json enviado para o Servidor: " + messageJson);
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
