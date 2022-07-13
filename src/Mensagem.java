import java.net.InetAddress;
import java.util.List;

public class Mensagem {
    private String requestType;
    private InetAddress ip;
    private int port;
    private List<String> files;

    public Mensagem() {
    }

    public Mensagem(String requestType, InetAddress ip, int port, List<String> files) {
        this.requestType = requestType;
        this.ip = ip;
        this.port = port;
        this.files = files;
    }

    public String getRequestType() {
        return requestType;
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public List<String> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        return "Mensagem{" +
                "requestType='" + requestType + '\'' +
                ", ip=" + ip +
                ", port=" + port +
                ", files=" + files +
                '}';
    }
}
