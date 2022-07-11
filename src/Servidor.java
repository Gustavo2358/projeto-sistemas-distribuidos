import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class Servidor {
    public static void main(String[] args) {
        try {
            DatagramSocket serverSocket = new DatagramSocket(10098);
            while(true){

                byte[] recBuffer = new byte[1024];
                DatagramPacket recPkt = new DatagramPacket(recBuffer, recBuffer.length);
                serverSocket.receive(recPkt);
                String recData = new String(recPkt.getData(),recPkt.getOffset(),recPkt.getLength());
                //TODO Quando receber o JOIN, print “Peer [IP]:[porta] adicionado com arquivos [só nomes dos arquivos].
                System.out.println(recPkt.getAddress() + " " + recPkt.getPort());
                System.out.println(recData);

                byte[] sendBuf = "JOIN_OK".getBytes(StandardCharsets.UTF_8);
                DatagramPacket sendPacket = new DatagramPacket(sendBuf,sendBuf.length,recPkt.getAddress(), recPkt.getPort());
                serverSocket.send(sendPacket);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
