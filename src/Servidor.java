import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*TODO duvida: caso mande o Ip errado, ele nunca irá parar de mandar a requisição para o servidor, e nunca irá receber
 * o JOIN_OK, será que é importante implementar algum dispositivo no servidor que não aceite um certo número de
 * requisições repetidas em um determinado período de tempo? (por exemplo, uma black list).
 */
//TODO implementar LEAVE
public class Servidor {
    private static final Map<InetSocketAddress, List<String>> peers = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        try {
            DatagramSocket Socket = new DatagramSocket(10098);
            while(true){
                Mensagem receivedMessage = listenToIncomingMessages(Socket);
                switch (receivedMessage.getRequestType()) {
                    case "JOIN":
                        //cria uma thread para requisição JOIN
                        join(Socket, receivedMessage);
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void join(DatagramSocket socket, Mensagem receivedMessage) {
        Thread joinThread = new Thread(()-> {
            try {
                joinRequest(receivedMessage);
                sendJoinOkResponse(socket, receivedMessage);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        joinThread.start();
    }

    private static void joinRequest(Mensagem receivedMessage) throws IOException {
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

    private static void sendJoinOkResponse(DatagramSocket socket, Mensagem receivedMessage) throws IOException {
        Mensagem joinOkMessage = new Mensagem("JOIN_OK");
        DatagramPacket sendPacket = getDatagramPacketFromMessage(receivedMessage.getIp(), receivedMessage.getPort(), joinOkMessage);
//        byte[] sendBuf = "JOIN_OK".getBytes(StandardCharsets.UTF_8);
//        DatagramPacket sendPacket = new DatagramPacket(sendBuf,sendBuf.length, receivedMessage.getIp(), receivedMessage.getPort());

        //TODO apagar depois, imprime estado atual do peers
        peers.entrySet().stream().forEach(System.out::println);
        //TODO apagar esse sleep, só para teste
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
        System.out.println("Json sended: " + messageJson);
        byte[] sendData = messageJson.getBytes(StandardCharsets.UTF_8);
        return new DatagramPacket(sendData, sendData.length, receiverIpAddress, receiverPort );
    }
}
