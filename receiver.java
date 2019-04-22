import java.io.*;
import java.net.*;

public class receiver {
	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println("Usage: ./receiver <host> <port to> <port from> <file>");
			System.exit(1);
		}

		//init
		DatagramSocket dataSocket = new DatagramSocket(Integer.parseInt(args[2]));
		DatagramSocket ackSocket = new DatagramSocket(Integer.parseInt(args[1]));
		InetAddress IPAddress = InetAddress.getByName(args[0]);
		PrintWriter dataWriter = new PrintWriter(args[3], "UTF-8");
		PrintWriter arrvWriter = new PrintWriter("arrival.log", "UTF-8");

		//repeat receiving
		int nextPacket = 0;
		byte[] recvData = new byte[1024];
		DatagramPacket recvPacket = new DatagramPacket(recvData, recvData.length);
		while (true) {
			dataSocket.receive(recvPacket);
			packet pk = packet.parseUDPdata(recvPacket.getData());
			arrvWriter.println(pk.getSeqNum());
			if (nextPacket == pk.getSeqNum()) {
				if (pk.getType() == 2) { // eot
					packet eot = packet.createEOT(pk.getSeqNum());
					byte[] eotData = eot.getUDPdata();
					DatagramPacket eotPacket = new DatagramPacket(eotData, eotData.length,
						IPAddress, Integer.parseInt(args[1]));
					ackSocket.send(eotPacket);

					ackSocket.close();
					dataSocket.close();
					dataWriter.close();
					arrvWriter.close();
					return;
				}

				//write
				dataWriter.print(new String(pk.getData()));

				//return ack
				packet ack = packet.createACK(pk.getSeqNum());
				byte[] ackData = ack.getUDPdata();
				DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length,
					IPAddress, Integer.parseInt(args[1]));
				ackSocket.send(ackPacket);
				nextPacket++;
				nextPacket %= 32;
			} else {
				packet nack = packet.createACK((nextPacket + 31) % 32);
				byte[] nackData = nack.getUDPdata();
				DatagramPacket nackPacket = new DatagramPacket(nackData, nackData.length,
					IPAddress, Integer.parseInt(args[1]));
				ackSocket.send(nackPacket); 
			}
		}
	}
}
