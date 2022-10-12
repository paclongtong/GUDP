import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;

public class GUDPSocket implements GUDPSocketAPI {
    DatagramSocket datagramSocket;

    public GUDPSocket(DatagramSocket socket) {
        datagramSocket = socket;
    }

    public void send(DatagramPacket packet) throws IOException {
        GUDPPacket gudppacket = GUDPPacket.encapsulate(packet);
        DatagramPacket udppacket = gudppacket.pack();       // packed up to be a UDP packet
        datagramSocket.send(udppacket);
    }

    public void receive(DatagramPacket packet) throws IOException {
        System.out.println("Listening");
        byte[] buf = new byte[GUDPPacket.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
        datagramSocket.receive(udppacket);
        GUDPPacket gudppacket = GUDPPacket.unpack(udppacket);
        gudppacket.decapsulate(packet);
    }

    public void finish() throws IOException {
        ;
    }
    public void close() throws IOException {
        ;
    }
}

