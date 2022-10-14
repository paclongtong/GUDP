import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

public class GUDPSocket implements GUDPSocketAPI {
    DatagramSocket datagramSocket;
    boolean startFlag = false;
    short[] window = new short[3];
    long l = 0;
    int BSN = 0;
    int seq = 0;
    public GUDPSocket(DatagramSocket socket) {
        datagramSocket = socket;
    }


    public void send(DatagramPacket packet) throws IOException {
       /* TimerTask task = new TimerTask() {
            @Override
            public void run() {
                GUDPPacket1 gudppacket = null;
                try {
                    gudppacket = GUDPPacket1.encapsulate(packet);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                DatagramPacket udppacket = null;       // packed up to be a UDP packet
                try {
                    udppacket = gudppacket.pack();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                try {
                    datagramSocket.send(udppacket);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };*/

        if (!startFlag) {
            startFlag = true;
            long l = System.currentTimeMillis();
            sendBSN(packet);
            window[0] = 1;

            BSN = GUDPPacket1.byteArrayToInt(receive_payload(packet));
            seq = BSN + 1;
            sendDATA(packet, seq);

            window[1] = 1;

        } else {
            long l1 = System.currentTimeMillis();
            if ((l1 - l) >= 500) {

            }
        }


        /*GUDPPacket1 gudppacket = GUDPPacket1.encapsulate(packet);
        DatagramPacket udppacket = gudppacket.pack();       // packed up to be a UDP packet
        datagramSocket.send(udppacket);
        Timer timer = new Timer(true);
        timer.schedule(task, 500);*/

    }

    public void sendDATA(DatagramPacket packet, int seq) throws IOException {
        GUDPPacket1 gudppacket = GUDPPacket1.encapsulate(packet, seq);
        DatagramPacket udppacket = gudppacket.pack();       // packed up to be a UDP packet
        datagramSocket.send(udppacket);
    }

    public void sendBSN(DatagramPacket packet) throws IOException {
        GUDPPacket1 gudppacket = GUDPPacket1.encapsulate_BSN(packet);
        DatagramPacket udppacket = gudppacket.pack();       // packed up to be a UDP packet
        datagramSocket.send(udppacket);
    }

    public void receive(DatagramPacket packet) throws IOException {
        //System.out.println("Listening");
        byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
        datagramSocket.receive(udppacket);
        GUDPPacket1 gudppacket = GUDPPacket1.unpack(udppacket);
        gudppacket.decapsulate(packet);

        GUDPPacket1 gudppacket_ACK = GUDPPacket1.encapsulate(packet,gudppacket.getSeqno()+1);
        DatagramPacket udppacket_ACK = gudppacket_ACK.pack();
        datagramSocket.send(udppacket_ACK);
    }

    public byte[] receive_payload(DatagramPacket packet) throws IOException {
        byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
        datagramSocket.receive(udppacket);
        GUDPPacket1 gudppacket = GUDPPacket1.unpack(udppacket);
        gudppacket.decapsulate(packet);
        byte[] data = packet.getData();
        return data;
    }

    public void finish() throws IOException {
        ;
    }

    public void close() throws IOException {
        ;
    }

    public static void socketListener(int port) throws IOException {
        //create socket
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buf = new byte[64];
        try {
            //create packet
            DatagramPacket recPacket = new DatagramPacket(buf, buf.length);
            //accept data
            socket.receive(recPacket);
            InetAddress address = recPacket.getAddress();
            String targetIp = address.getHostAddress();
            int targetPort = recPacket.getPort();
            socket.close();
        } catch (Exception e) {
        } finally {
            //关闭socket
            socket.close();
        }
    }

}

class GUDPPacket1 {
    public static final short GUDP_VERSION = 1;
    public static final short HEADER_SIZE = 8;      // unit: byte
    public static final Integer MAX_DATA_LEN = 1000;
    public static final Integer MAX_DATAGRAM_LEN = MAX_DATA_LEN + HEADER_SIZE;
    public static final Integer MAX_WINDOW_SIZE = 3;
    public static final short TYPE_DATA = 1;
    public static final short TYPE_BSN = 2;
    public static final short TYPE_ACK = 3;

    private InetSocketAddress sockaddr;
    private ByteBuffer byteBuffer;
    private Integer payloadLength;

    /*
     * Application send processing: Build a DATA GUDP packet to encaspulate payload
     * from the application. The application payload is in the form of a DatagramPacket,
     * containing data and socket address.
     */

    public static GUDPPacket1 encapsulate(DatagramPacket packet, int seq) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(packet.getLength() + HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setType(TYPE_DATA);
        gudppacket.setSeqno(seq);
        gudppacket.setVersion(GUDP_VERSION);
        byte[] data = packet.getData();
        gudppacket.setPayload(data);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
        return gudppacket;
    }

    public static GUDPPacket1 encapsulate_BSN(DatagramPacket packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(packet.getLength() + HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setType(TYPE_BSN);
        gudppacket.setVersion(GUDP_VERSION);
        Random r = new Random();
        int bsn = r.nextInt(128);
        byte[] BSN = intToByteArray(bsn);
        gudppacket.setPayload(BSN);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
        return gudppacket;
    }

    public static GUDPPacket1 encapsulate_ACK(DatagramPacket packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(packet.getLength() + HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setType(TYPE_ACK);
        gudppacket.setVersion(GUDP_VERSION);
        byte[] data = packet.getData();
        gudppacket.setPayload(data);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
        return gudppacket;
    }

    /*
     * Application receive processing: Extract application payload into a DatagramPacket,
     * with data and socket address.
     */
    public void decapsulate(DatagramPacket packet) throws IOException {
        int plength = getPayloadLength();
        getPayload(packet.getData(), plength);
        packet.setLength(plength);
        packet.setSocketAddress(getSocketAddress());
        getSeqno();
    }

    /*
     * Input processing: Turn a DatagramPacket received from UDP into a GUDP packet
     */
    public static GUDPPacket1 unpack(DatagramPacket packet) throws IOException {
        int plength = packet.getLength();
        if (plength < HEADER_SIZE)
            throw new IOException(String.format("Too short GUDP packet: %d bytes", plength));

        byte[] data = packet.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, plength);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setPayloadLength(plength - HEADER_SIZE);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
        return gudppacket;
    }

    /*
     * Output processing: Turn headers and payload into a DatagramPacket, for sending with UDP
     */

    public DatagramPacket pack() throws IOException {
        int totlength = HEADER_SIZE + getPayloadLength();
        InetSocketAddress socketAddress = getSocketAddress();
        return new DatagramPacket(getBytes(), totlength, sockaddr);
    }

    /*
     * Constructor: create a GUDP packet with a ByteBuffer as back storage
     */
    public GUDPPacket1(ByteBuffer buffer) {
        byteBuffer = buffer;
    }

    /*
     * Serialization: Return packet as a byte array
     */
    public byte[] getBytes() {
        return byteBuffer.array();
    }

    public short getVersion() {
        return byteBuffer.getShort(0);
    }

    public short getType() {
        return byteBuffer.getShort(2);
    }

    public int getSeqno() {
        return byteBuffer.getInt(4);
    }

    public InetSocketAddress getSocketAddress() {
        return sockaddr;
    }

    public void setVersion(short version) {
        byteBuffer.putShort(0, version);
    }

    public void setType(short type) {
        byteBuffer.putShort(2, type);
    }

    public void setSeqno(int length) {
        byteBuffer.putInt(4, length);
    }

    public void setPayload(byte[] pload) {
        byteBuffer.position(HEADER_SIZE);
        byteBuffer.put(pload, 0, pload.length);
        payloadLength = pload.length;
    }

    public void setSocketAddress(InetSocketAddress socketAddress) {
        sockaddr = socketAddress;
    }

    public void setPayloadLength(int length) {
        payloadLength = length;
    }

    public int getPayloadLength() {
        return payloadLength;
    }

    public void getPayload(byte[] dst, int length) {
        byteBuffer.position(HEADER_SIZE);
        byteBuffer.get(dst, 0, length);
    }


    /**
     * int到byte[] 由高位到低位
     *
     * @param i 需要转换为byte数组的整行值。
     * @return byte数组
     */
    public static byte[] intToByteArray(int i) {
        byte[] result = new byte[4];
        result[0] = (byte) ((i >> 24) & 0xFF);
        result[1] = (byte) ((i >> 16) & 0xFF);
        result[2] = (byte) ((i >> 8) & 0xFF);
        result[3] = (byte) (i & 0xFF);
        return result;
    }

    /**
     * byte[]转int
     *
     * @param bytes 需要转换成int的数组
     * @return int值
     */
    public static int byteArrayToInt(byte[] bytes) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (3 - i) * 8;
            value += (bytes[i] & 0xFF) << shift;
        }
        return value;
    }
}