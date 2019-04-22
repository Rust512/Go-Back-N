import java.io.*;
import java.net.*;

public class sender {
	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println("Usage: ./sender <host> <port to> <port from> <file>");
			System.exit(1);
		}

		//read from file
		File file = new File(args[3]);
		byte sentence[] = new byte[(int)file.length()];
		FileInputStream fileinputstream = new FileInputStream(file);
		fileinputstream.read(sentence);

		//preapare packets
		int size = sentence.length/500;
		int transffered = 0;
		if (sentence.length % 500 != 0) size += 1;
		packet packets[] = new packet[size];
		for (int i = 0; i < size; i++) {
			byte cp[] = new byte[Math.min(500, sentence.length - transffered)];
			System.arraycopy(sentence, transffered, cp, 0, Math.min(500, sentence.length - transffered));
			packets[i] = packet.createPacket(i % 32, new String(cp));
			transffered += 500;
		}

		//init
		DatagramSocket senderSocket = new DatagramSocket();
		DatagramSocket recvSocket = new DatagramSocket(Integer.parseInt(args[2]));
		InetAddress IPAddress = InetAddress.getByName(args[0]);
		PrintWriter seqNumWriter = new PrintWriter("seqnum.log", "UTF-8");
		PrintWriter ackWriter = new PrintWriter("ack.log", "UTF-8");
		int windowBase = 0;
		int nextPacket = 0;
		int timeOut = 500;

		//repeat sending from windowBase to windowBase + 10
		while (windowBase != size) {
			while (nextPacket < windowBase + 10 && nextPacket < size) {
				byte[] sendData = packets[nextPacket].getUDPdata();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, 
					IPAddress, Integer.parseInt(args[1]));
				senderSocket.send(sendPacket);
				seqNumWriter.println(nextPacket % 32);
				nextPacket++;
			}
			try { // could time out
				recvSocket.setSoTimeout(timeOut);
				byte[] ack = new byte[1024];
				DatagramPacket recvPacket = new DatagramPacket(ack, ack.length);
				recvSocket.receive(recvPacket);
				packet pk = packet.parseUDPdata(recvPacket.getData());
				ackWriter.println(pk.getSeqNum());
				if (pk.getSeqNum() == windowBase % 32) {
					windowBase++;
				} else if (pk.getSeqNum() > windowBase % 32 && 
							(pk.getSeqNum() - windowBase % 32) < 10) {
					windowBase += pk.getSeqNum() - windowBase % 32 + 1;
				} else if (pk.getSeqNum() < 10 &&
							(pk.getSeqNum() + 32 - windowBase % 32) < 10) {
					windowBase += pk.getSeqNum() + 32 - windowBase % 32 + 1;
				}
			} catch (SocketTimeoutException e) { // lost all pac
				for (int i = windowBase; i < nextPacket; i++) {
					byte[] sendData = packets[i].getUDPdata();
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length,
						IPAddress, Integer.parseInt(args[1]));
					senderSocket.send(sendPacket);
					seqNumWriter.println(i % 32);
				}
			}
		}
	
		//send eot
		packet eot = packet.createEOT(size);
		byte[] eotData = eot.getUDPdata();
		DatagramPacket eotPacket = new DatagramPacket(eotData, eotData.length,
			IPAddress, Integer.parseInt(args[1]));
		senderSocket.send(eotPacket);
		seqNumWriter.println(eot.getSeqNum());
		while (true) { //can not exit before EOT from receiver
			try {
				byte[] recv = new byte[1024];
				DatagramPacket recvPacket = new DatagramPacket(recv, recv.length);
				recvSocket.receive(recvPacket);
				packet pk = packet.parseUDPdata(recvPacket.getData());
				if (pk.getType() == 2) break;
			} catch (SocketTimeoutException e) {
				continue;
			}
		}

		//closing
		senderSocket.close();
		recvSocket.close();
		seqNumWriter.close();
		ackWriter.close();
	}
}
