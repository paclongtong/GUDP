// created by Bolong Tang on Oct. 1st, 2022

import javax.xml.crypto.Data;
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
    int[] window = new int[3];
    long l = 0;
    int BSN = -1;
    int seq = 0;
    short window_pointer = 0;
    int seq_expected = 0;
    long[] time_win = new long[3];
    DatagramPacket[] senderBuffer = new DatagramPacket[GUDPPacket1.MAX_WINDOW_SIZE];
    DatagramPacket[] receiverBuffer = new DatagramPacket[GUDPPacket1.MAX_WINDOW_SIZE];

    private InetSocketAddress sockaddr_sender;
    private InetSocketAddress sockaddr_receiver;

    public GUDPSocket(DatagramSocket socket) {
        datagramSocket = socket;
    }

    DatagramPacket[] packet_backup = new DatagramPacket[GUDPPacket1.MAX_WINDOW_SIZE];

    public void send(DatagramPacket packet) throws IOException {
        if (!startFlag) {
            startFlag = true;
            // listen to datagram buffer if there is something to receive
            while (BSN == -1) {      // rethink the condition
                int fail_count = 0;
                try {
                    sendBSN(packet);
                    byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
                    DatagramPacket udppacket_BSN = new DatagramPacket(buf, buf.length);
                    datagramSocket.setSoTimeout(500);           // Is this datagramSocket occupied by others?
                    System.out.println("Ready to receive BSN ACK");
//                    DatagramSocket BSN = new DatagramSocket(packet.getPort());
                    datagramSocket.receive(udppacket_BSN);
                    System.out.println("BSN received.");
                    GUDPPacket1 gudppacket = GUDPPacket1.unpack(udppacket_BSN);
                    BSN = gudppacket.getSeqno() - 1;
                } catch (Exception e) {
                    System.out.println("BSN ACK reception error: " + e.getMessage() + " Resend once again.");
                    fail_count += 1;
                    if (fail_count < 10) {
                        continue;
                    } else {
                        error_finish();
                    }
                }
            }
            // receive BSN and extract BSN
            byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
            DatagramPacket packet_ACK = new DatagramPacket(buf, GUDPPacket1.MAX_DATAGRAM_LEN);
            seq = BSN + 1;
            int seq_temp = seq;
            while (seq_temp == seq) {
                int fail_count = 0;
                sendDATA(packet, seq);
                System.out.println("DATA sent, seq: " + seq + " , number: " + (seq - BSN));
                senderBuffer[0] = packet;
                time_win[0] = System.currentTimeMillis();
                window[0] = seq;
                window_pointer += 1;

                byte[] buf_ACK = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
                DatagramPacket udppacket_ACK = new DatagramPacket(buf_ACK, buf_ACK.length);
                datagramSocket.setSoTimeout(500);           // Is this datagramSocket occupied by others?
                System.out.println("Ready to receive ACK");
                try {
                    datagramSocket.receive(udppacket_ACK);
                    GUDPPacket1 gudpACK = GUDPPacket1.unpack(udppacket_ACK);
                    seq = gudpACK.getSeqno();
                    System.out.println("ACK received, expected seq: " + seq);
                    window_move_left();
                } catch (IOException e) {
                    System.out.println("Exception when getting ACK: " + e.getMessage());
                    fail_count += 1;
                    if (fail_count < 10) {
                        continue;
                    } else {
                        error_finish();
                    }
                }
            }
        } else {
            // slide window reaction
//            datagramSocket.setSoTimeout(500);
            for (int i = 0; i <= window_pointer; i++) {
                int seq_temp = seq;
                while (seq_temp == seq) {
                    int fail_count = 0;
                    sendDATA(packet, seq);
                    System.out.println("DATA sent, seq: " + seq + " , number: " + (seq - BSN));
                    senderBuffer[window_pointer] = packet;
                    time_win[window_pointer] = System.currentTimeMillis();
                    window[window_pointer] = seq;
                    window_pointer += 1;

                    byte[] buf_ACK = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
                    DatagramPacket udppacket_ACK = new DatagramPacket(buf_ACK, buf_ACK.length);
                    datagramSocket.setSoTimeout(500);
                    System.out.println("Ready to receive ACK");
                    try {
                        datagramSocket.receive(udppacket_ACK);
                        GUDPPacket1 gudpACK = GUDPPacket1.unpack(udppacket_ACK);
                        seq = gudpACK.getSeqno();
                        System.out.println("ACK received, expected seq: " + seq);
                        window_move_left();
                    } catch (IOException e) {
                        System.out.println("Exception when getting ACK: " + e.getMessage());
                        fail_count += 1;
                        window_pointer -= 1;
                        if (fail_count < 10) {
                            continue;
                        } else {
                            error_finish();
                        }
                    }
                }
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
        System.out.println("BSN packet sent.");
    }

    public void receive(DatagramPacket packet) throws IOException {
        //System.out.println("Listening");
        byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
        datagramSocket.receive(udppacket);

        GUDPPacket1 gudppacket = GUDPPacket1.unpack(udppacket);
        sockaddr_sender = gudppacket.getSocketAddress();

        if (gudppacket.getType() == GUDPPacket1.TYPE_BSN) {
            this.BSN = gudppacket.getSeqno();
            System.out.println("Got BSN packet. BSN: " + BSN + "\nReady to send ACK.");
            GUDPPacket1 gudppacket_ACK = GUDPPacket1.encapsulate_ACK(packet, gudppacket.getSeqno() + 1);
            gudppacket_ACK.setSocketAddress(gudppacket.getSocketAddress());
            DatagramPacket udppacket_ACK = gudppacket_ACK.pack_back();
            datagramSocket.send(udppacket_ACK);

            byte[] buf2 = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
            DatagramPacket udppacket2 = new DatagramPacket(buf2, buf2.length);
            datagramSocket.receive(udppacket2);     // 1
            GUDPPacket1 gudppacket2 = GUDPPacket1.unpack(udppacket2);  // 2
            this.seq = gudppacket2.getSeqno();
            System.out.println("Got DATA packet: " + seq);
            /*byte[] buf_test = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
            DatagramPacket udppacket_test = new DatagramPacket(buf_test, buf_test.length);
            datagramSocket.receive(udppacket_test);     // 1
            GUDPPacket1 gudppacket_test = GUDPPacket1.unpack(udppacket_test);  // 2
            System.out.println("Got DATA packet: test , seq: " + gudppacket_test.getSeqno());*/
            gudppacket2.setSocketAddress(gudppacket.getSocketAddress());    // 3
//            if (gudppacket2.getSeqno() != seq)
            gudppacket2.decapsulate(packet);    // 4
            send_ACK(udppacket2, gudppacket2.getSeqno() + 1);

        } else if (gudppacket.getType() == GUDPPacket1.TYPE_DATA) {
            System.out.println("Got DATA packet. Number: " + (gudppacket.getSeqno() - this.BSN) + "\nReady to send ACK.");
            /*GUDPPacket1 gudppacket_ACK = GUDPPacket1.encapsulate_ACK(packet, gudppacket.getSeqno() + 1);
            gudppacket_ACK.setSocketAddress(gudppacket.getSocketAddress());
            DatagramPacket udppacket_ACK = gudppacket_ACK.pack_back();
            datagramSocket.send(udppacket_ACK);*/

            send_ACK(udppacket, gudppacket.getSeqno() + 1);

            gudppacket.decapsulate(packet); // load the UDP packet and outgoing to app
        }

    }

    public void send_ACK(DatagramPacket packet, int seq_expected) throws IOException {
        GUDPPacket1 gudppacket_ACK = GUDPPacket1.encapsulate_ACK(packet, seq_expected);
        DatagramPacket udppacket_ACK = gudppacket_ACK.pack_back();
        datagramSocket.send(udppacket_ACK);
        System.out.println("ACK sent. SEQ_expected: " + seq_expected);
    }

    public void receive_BSN() throws IOException {
        byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket_BSN = new DatagramPacket(buf, buf.length);
        datagramSocket.setSoTimeout(500);
        datagramSocket.receive(udppacket_BSN);
        GUDPPacket1 gudppacket = GUDPPacket1.unpack(udppacket_BSN);
        gudppacket.decapsulate(udppacket_BSN);
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

    public void receive_seq(DatagramPacket packet) throws IOException {
        byte[] buf = new byte[GUDPPacket1.MAX_DATAGRAM_LEN];
        DatagramPacket udppacket = new DatagramPacket(buf, buf.length);
        datagramSocket.setSoTimeout(1000);
        datagramSocket.receive(udppacket);
        GUDPPacket1 gudppacket = GUDPPacket1.unpack(udppacket);
        seq = gudppacket.getSeqno();
    }

    public void error_finish() throws IOException {
        System.out.println("Connection failed. Process finished.");
        startFlag = false;
        BSN = -1;
//        datagramSocket.close();
    }

    public void finish() throws IOException {
        System.out.println("Transmission process finished.");
        startFlag = false;
        BSN = -1;
//        datagramSocket.close();
    }

    public void close() throws IOException {
        datagramSocket.close();
    }

    public static void socketListener(int port) throws IOException {
        //create socket
        DatagramSocket socket = new DatagramSocket(port);
        InetAddress address_localhost = InetAddress.getByName("localhost");
        byte[] buf = new byte[64];
        try {
            //create packet
            DatagramPacket recPacket = new DatagramPacket(buf, buf.length);
            //accept data
            socket.receive(recPacket);      // when the socket has received the packet, move to the next step, or it will keep listening
            InetAddress address = recPacket.getAddress();
            String targetIp = address.getHostAddress(); // Returns the IP address string in textual presentation
            int targetPort = recPacket.getPort();
            socket.close();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            //close socket
            socket.close();
        }
    }

    public void window_move_left() throws IOException {
        window[0] = window[1];
        window[1] = window[2];
        window[2] = 0;
        window_pointer -= 1;
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
    private Integer payloadLength = 0;

    /*
     * Application send processing: Build a DATA GUDP packet to encaspulate payload
     * from the application. The application payload is in the form of a DatagramPacket,
     * containing data and socket address.
     */

    //Transform the existing UDP Packet to a new GUDP packet
    public static GUDPPacket1 encapsulate(DatagramPacket packet, int seq) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(packet.getLength() + HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setType(TYPE_DATA);
        gudppacket.setSeqno(seq);
        gudppacket.setVersion(GUDP_VERSION);
        byte[] data = packet.getData();
        gudppacket.setPayload(data);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress()); // destination UDP Socket address, and write GUDP socket sockaddr parameter
        return gudppacket;
    }

    public static GUDPPacket1 encapsulate_BSN(DatagramPacket packet) throws IOException {   // set BSN to its header
//        ByteBuffer buffer = ByteBuffer.allocate(packet.getLength() + HEADER_SIZE);
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);       // 10302022
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setType(TYPE_BSN);

        gudppacket.setVersion(GUDP_VERSION);
        Random r = new Random();
        int bsn = r.nextInt(127) + 1;
        gudppacket.setSeqno(bsn);
//        byte[] BSN = intToByteArray(bsn);
//        gudppacket.setPayload(BSN);

        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());
        return gudppacket;
    }

    public static GUDPPacket1 encapsulate_ACK(DatagramPacket packet, int seq) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);
        gudppacket.setType(TYPE_ACK);
        gudppacket.setVersion(GUDP_VERSION);
        gudppacket.setSeqno(seq);
//        byte[] SEQ = intToByteArray(seq + 1);
//        gudppacket.setPayload(SEQ);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress());

        return gudppacket;
    }

    /*
     * Application receive processing: Extract application payload into a DatagramPacket,
     * with data and socket address.
     */
    public void decapsulate(DatagramPacket packet) throws IOException {
        int plength = getPayloadLength();
        getPayload(packet.getData(), plength);  // load UDP packet buffer with GUDP Packet inherent content
        packet.setLength(plength);
        packet.setSocketAddress(getSocketAddress());
    }

    public int decapsulate_seq(DatagramPacket packet) throws IOException {
        int plength = getPayloadLength();
        getPayload(packet.getData(), plength);  // get all the data in datagram buffer -->
        packet.setLength(0);

        packet.setSocketAddress(getSocketAddress());        //

        int seq = getSeqno();       // the premise is:  a GUDP buffer exists
        return seq;
    }

    /*
     * Input processing: Turn a DatagramPacket received from UDP into a GUDP packet
     */

    // fill the GUDP Packet with received UDP packet content and get its SocketAddress
    public static GUDPPacket1 unpack(DatagramPacket packet) throws IOException {
        int plength = packet.getLength();
        if (plength < HEADER_SIZE)
            throw new IOException(String.format("Too short GUDP packet: %d bytes", plength));

        byte[] data = packet.getData();
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, plength);
        buffer.order(ByteOrder.BIG_ENDIAN);
        GUDPPacket1 gudppacket = new GUDPPacket1(buffer);    // a complete and pure GUDP structure is formed here, reflected by its bytebuffer
        gudppacket.setPayloadLength(plength - HEADER_SIZE);
        gudppacket.setSocketAddress((InetSocketAddress) packet.getSocketAddress()); // nothing is added in GUDP header as a Packet parameter
        return gudppacket;
    }

    /*
     * Output processing: Turn headers and payload into a DatagramPacket, for sending with UDP
     */

    // change original UDP Packet to a new UDP Packet with GUDP info created in encapsulate()
    public DatagramPacket pack() throws IOException {
        int totlength = HEADER_SIZE + getPayloadLength();
        InetSocketAddress socketAddress = getSocketAddress();
        return new DatagramPacket(getBytes(), totlength, sockaddr);
    }

    public DatagramPacket pack_BSN() throws IOException {
//        int totlength = HEADER_SIZE + getPayloadLength();
        int totlength = HEADER_SIZE;
        InetSocketAddress socketAddress = getSocketAddress();
        return new DatagramPacket(getBytes(), totlength, sockaddr);
    }

    public DatagramPacket pack_back() throws IOException {
        int totlength = HEADER_SIZE;        // 10302022
        InetSocketAddress socketAddress = getSocketAddress();
        return new DatagramPacket(getBytes(), totlength, socketAddress);
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

    public void setSocketAddress(InetSocketAddress socketAddress) {     // socket address of the remote host
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

    public void getSeq(byte[] dst, int length) {
        byteBuffer.position(HEADER_SIZE - 4);
        byteBuffer.get(dst, 0, length);
    }

    public static byte[] intToByteArray(int i) {
        byte[] result = new byte[4];
        result[0] = (byte) ((i >> 24) & 0xFF);
        result[1] = (byte) ((i >> 16) & 0xFF);
        result[2] = (byte) ((i >> 8) & 0xFF);
        result[3] = (byte) (i & 0xFF);
        return result;
    }

    public static int byteArrayToInt(byte[] bytes) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (3 - i) * 8;
            value += (bytes[i] & 0xFF) << shift;
        }
        return value;
    }
}