import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

public class Mensagem {
    private String requestType;
    private InetAddress ip;
    private Integer port;
    private List<String> files;
    private String requestedFile;
    private List<String> peersWithRequestedFiles;

    public Mensagem() {
    }

    public Mensagem(String requestType, InetAddress ip, int port, List<String> files) {
        this.requestType = requestType;
        this.ip = ip;
        this.port = port;
        this.files = files;
    }

    public Mensagem(String requestType, InetAddress ip, int port, String requestedFile) {
        this.requestType = requestType;
        this.ip = ip;
        this.port = port;
        this.requestedFile = requestedFile;
    }

    public Mensagem(String requestType) {
        this.requestType = requestType;
    }

    public Mensagem(String requestType, List<String> peersWithRequestedFiles) {
        this.requestType = requestType;
        this.peersWithRequestedFiles = peersWithRequestedFiles;
    }

    public Mensagem(String requestType, InetAddress ip, int port) {
        this.requestType = requestType;
        this.ip = ip;
        this.port = port;
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

    public String getRequestedFile() {
        return requestedFile;
    }

    public List<String> getPeersWithRequestedFiles() {
        return peersWithRequestedFiles;
    }
}
