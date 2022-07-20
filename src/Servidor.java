import com.google.gson.Gson;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/*TODO duvida: caso mande o Ip errado, ele nunca irá parar de mandar a requisição para o servidor, e nunca irá receber
 * o JOIN_OK, será que é importante implementar algum dispositivo no servidor que não aceite um certo número de
 * requisições repetidas em um determinado período de tempo? (por exemplo, uma black list).
 */
public class Servidor {
    private static final Map<InetSocketAddress, List<String>> peers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        handleRequests();
    }

    private static void handleRequests() {
        try {
            DatagramSocket socket = new DatagramSocket(10098);
            //noinspection InfiniteLoopStatement
            do {
                DatagramPacket receivedPacket = listenToIncomingMessages(socket);
                Mensagem receivedMessage = getMessageFromDatagramPacket(receivedPacket);
                switch (receivedMessage.getRequestType()) {
                    case "JOIN":
                        //cria uma thread para requisição JOIN
                        join(socket, receivedPacket, receivedMessage);
                        break;
                    case "SEARCH":
                        search(socket, receivedPacket, receivedMessage);
                        break;
                    case "LEAVE":
                        leave(socket, receivedPacket, receivedMessage);
                        break;
                }
            } while (true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void search(DatagramSocket socket, DatagramPacket receivedPacket,  Mensagem receivedMessage) {
        Thread searchThread = new Thread(() -> {
            List<InetSocketAddress> peersWithRequestedFiles = handleSearchRequest(receivedMessage);
            try {
                sendSearchResult(socket, receivedPacket, peersWithRequestedFiles);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        searchThread.start();
    }


    private static void leave(DatagramSocket socket, DatagramPacket receivedPacket, Mensagem receivedMessage) {
        Thread leaveThread = new Thread(()-> {
            try {
                removePeerFiles(receivedMessage);
                sendOkResponse(socket, receivedPacket, "LEAVE_OK");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        leaveThread.start();
    }


    private static void join(DatagramSocket socket, DatagramPacket receivedPacket, Mensagem receivedMessage) {
        Thread joinThread = new Thread(()-> {
            try {
                handleJoinRequest(receivedMessage);
                sendOkResponse(socket, receivedPacket, "JOIN_OK");
            } catch (IOException e) {
                e.printStackTrace();
            }
            alive(receivedPacket);
        });
        joinThread.start();
    }

    private static void alive(DatagramPacket receivedPacket) {
        Mensagem message = getMessageFromDatagramPacket(receivedPacket);
        Thread aliveThread = new Thread(() -> {
            try {
                DatagramSocket socket= new DatagramSocket();
                Mensagem AliveMessage = new Mensagem("ALIVE");
                DatagramPacket sendPacket = getDatagramPacketFromMessage(message.getIp(), message.getPort(), AliveMessage);
                while(true) {
                    System.out.println("Mandando Alive");
                    socket.send(sendPacket);
                    byte[] buf = new byte[1024];
                    DatagramPacket receivedAliveOk = new DatagramPacket(buf, buf.length);
                    socket.setSoTimeout(2000);
                    socket.receive(receivedAliveOk);
                    Mensagem receivedMessage = getMessageFromDatagramPacket(receivedAliveOk);
                    System.out.println(receivedMessage.getRequestType());
                    if (receivedMessage.getRequestType().equals("ALIVE_OK")) {
                        System.out.println("Alive Ok recebido");
                        sleep(30 * 1000);
                    }else{
                        break;
                    }
                }
                deadPeer(getMessageFromDatagramPacket(receivedPacket));
            } catch (SocketTimeoutException e){
                deadPeer(getMessageFromDatagramPacket(receivedPacket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        aliveThread.start();
    }

    private static void deadPeer(Mensagem message) {
        List<String> value = removePeerFiles(message);
        if (Objects.nonNull(value)) {
            String messageFilesString = message.getFiles().stream()
                    .map(f -> f + " ")
                    .collect(Collectors.joining());
            System.out.printf("Peer %s:%d morto. Eliminando seus arquivos %s%n", message.getIp(), message.getPort(), messageFilesString);
        }
    }

    private static void sleep(int milli) {
        try {
            Thread.sleep(milli);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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

    private static List<String> removePeerFiles(Mensagem receivedMessage) {
        InetSocketAddress peerAddress = new InetSocketAddress(receivedMessage.getIp(), receivedMessage.getPort());
        return peers.remove(peerAddress);

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

    private static void sendSearchResult(DatagramSocket socket, DatagramPacket receivedPacket, List<InetSocketAddress> peersWithRequestedFiles) throws IOException {
        List<String> peersString = peersWithRequestedFiles.stream()
                .map(i -> String.format("%s:%d",i.getAddress().toString(),i.getPort()))
                .collect(Collectors.toList());
        Mensagem searchOkMessage = new Mensagem("SEARCH_OK", peersString);
        DatagramPacket sendPacket = getDatagramPacketFromMessage(receivedPacket.getAddress(), receivedPacket.getPort(), searchOkMessage);
        socket.send(sendPacket);
    }

    private static void sendOkResponse(DatagramSocket socket, DatagramPacket receivedPacket, String okType) throws IOException {
        Mensagem OkMessage = new Mensagem(okType);
        DatagramPacket sendPacket = getDatagramPacketFromMessage(receivedPacket.getAddress(), receivedPacket.getPort(), OkMessage);

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

    private static DatagramPacket listenToIncomingMessages(DatagramSocket serverSocket) throws IOException {
        byte[] recBuffer = new byte[1024];
        DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
        serverSocket.receive(recPkt);
        return recPkt;
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
