import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*TODO duvida: caso mande o Ip errado, ele nunca irá parar de mandar a requisição para o servidor, e nunca irá receber
 * o JOIN_OK, será que é importante implementar algum dispositivo no servidor que não aceite um certo número de
 * requisições repetidas em um determinado período de tempo? (por exemplo, uma black list).
 */
public class Servidor {
    private static final Map<InetSocketAddress, List<String>> peers = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        try {
            DatagramSocket socket = new DatagramSocket(10098);
            //noinspection InfiniteLoopStatement
            do {
                Mensagem receivedMessage = listenToIncomingMessages(socket);
                switch (receivedMessage.getRequestType()) {
                    case "JOIN":
                        //cria uma thread para requisição JOIN
                        join(socket, receivedMessage);
                        break;
                    case "SEARCH":
                        search(socket, receivedMessage);
                        break;
                    case "LEAVE":
                        leave(socket, receivedMessage);
                        break;
                }
            } while (true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void search(DatagramSocket socket, Mensagem receivedMessage) {
        Thread searchThread = new Thread(() -> {
            List<InetSocketAddress> peersWithRequestedFiles = handleSearchRequest(receivedMessage);
            try {
                sendSearchResult(socket, receivedMessage, peersWithRequestedFiles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        searchThread.start();
    }


    private static void leave(DatagramSocket socket, Mensagem receivedMessage) {
        Thread leaveThread = new Thread(()-> {
            try {
                handleLeaveRequest(receivedMessage);
                sendOkResponse(socket, receivedMessage, "LEAVE_OK");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        leaveThread.start();
    }


    private static void join(DatagramSocket socket, Mensagem receivedMessage) {
        Thread joinThread = new Thread(()-> {
            try {
                handleJoinRequest(receivedMessage);
                sendOkResponse(socket, receivedMessage, "JOIN_OK");
                checkIfPeerIsAlive(receivedMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        joinThread.start();
    }

    private static void checkIfPeerIsAlive(Mensagem receivedMessage) {
        try (DatagramSocket datagramSocket = new DatagramSocket()){
            Mensagem alive = new Mensagem("ALIVE");
            Gson gson = new Gson();
            String messageJson = gson.toJson(alive);
            byte[] sendData = messageJson.getBytes(StandardCharsets.UTF_8);
            byte[] receiveBuf = new byte[1024];
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, receivedMessage.getIp(), receivedMessage.getPort());
            DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
            while (true) {
                System.out.printf("mandando requisição ALIVE para %s:%d %n", receivedMessage.getIp(), receivedMessage.getPort());

                datagramSocket.send(sendPacket);
                Mensagem mensagem;
                try {
                    datagramSocket.setSoTimeout(2000);
                    datagramSocket.receive(receivePacket);
                    String recData = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
                    mensagem = gson.fromJson(recData, Mensagem.class);
                } catch(SocketTimeoutException e){
                    mensagem = new Mensagem("Error");
                }
                if (mensagem.getRequestType().equals("ALIVE_OK")) {
                    //TODO trocar para 30
                    System.out.println("Alive ok recebido");
                    Thread.sleep(5 * 1000);
                }
                else {
                    break;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        peerIsDead(receivedMessage.getIp(), receivedMessage.getPort());

    }

    private static void peerIsDead(InetAddress ip, int port) {
       //TODO remover arquivos
        InetSocketAddress dead = new InetSocketAddress(ip, port);
        String files = peers.get(dead).stream()
                .map(f -> f + " ")
                .collect(Collectors.joining());
        System.out.printf("Peer %s:%d morto. Eliminando seus arquivos %s%n", ip, port, files);
        peers.remove(dead);
    }

    private static List<InetSocketAddress> handleSearchRequest(Mensagem receivedMessage) {
        System.out.printf("Peer %s:%s solicitou arquivo %s%n",
                receivedMessage.getIp(), receivedMessage.getPort(), receivedMessage.getRequestedFile());
        String requestedFile = receivedMessage.getRequestedFile();
        return peers.entrySet().stream()
                .filter(e -> e.getValue().contains(requestedFile))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private static void handleLeaveRequest(Mensagem receivedMessage) throws IOException{
        InetSocketAddress peerAddress = new InetSocketAddress(receivedMessage.getIp(), receivedMessage.getPort());
        peers.remove(peerAddress);
    }

    private static void handleJoinRequest(Mensagem receivedMessage) throws IOException {
        InetSocketAddress peerAddress = new InetSocketAddress(receivedMessage.getIp(), receivedMessage.getPort());
        /*TODO se for feito um JOIN com o endereço de outra pessoa, mas com uma pasta com outros arquivos, esses arquivos
         * serão sobrescritos e o ALIVE_OK não irá limpar esses arquivos caso o peer original não tenho saído da rede.
         */
        peers.put(peerAddress, receivedMessage.getFiles());
        String messageFilesString = receivedMessage.getFiles().stream()
                .map(f -> f + " ")
                .collect(Collectors.joining());
        System.out.printf("Peer %s:%d adicionado com arquivos %s%n", peerAddress.getAddress(), peerAddress.getPort(), messageFilesString);

    }

    private static void sendSearchResult(DatagramSocket socket, Mensagem receivedMessage, List<InetSocketAddress> peersWithRequestedFiles) throws IOException {
        List<String> peersString = peersWithRequestedFiles.stream()
                .map(i -> String.format("%s:%d",i.getAddress().toString(),i.getPort()))
                .collect(Collectors.toList());
        Mensagem searchOkMessage = new Mensagem("SEARCH_OK", peersString);
        DatagramPacket sendPacket = getDatagramPacketFromMessage(receivedMessage.getIp(), receivedMessage.getPort(), searchOkMessage);
        socket.send(sendPacket);
    }

    private static void sendOkResponse(DatagramSocket socket, Mensagem receivedMessage, String okType) throws IOException {
        Mensagem OkMessage = new Mensagem(okType);
        DatagramPacket sendPacket = getDatagramPacketFromMessage(receivedMessage.getIp(), receivedMessage.getPort(), OkMessage);

        //TODO debug, apagar depois, imprime estado atual do peers
        System.out.println("Arquivos no map peers:");
        peers.entrySet().forEach(System.out::println);
        //TODO debug, apagar esse sleep, só para teste
        //simulando uma resposta demorada
//        try {
//            Thread.sleep(4500);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        socket.send(sendPacket);
    }

    private static Mensagem listenToIncomingMessages(DatagramSocket serverSocket) throws IOException {
        byte[] recBuffer = new byte[1024];
        DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
        serverSocket.receive(recPkt);
        return getMessageFromDatagramPacket(recPkt);
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
        System.out.println("Json request foi enviado para o Peer: " + messageJson);
        byte[] sendData = messageJson.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(sendData, sendData.length, receiverIpAddress, receiverPort );
    }
}
