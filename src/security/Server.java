package security;

////////////////////////////////////////////////
//File:    Server.java
//Name:    Taber Hust, Michael Munzing, Brad Kupka
//Class:   CS 4389
//Date:    11/22/2015
//
//Final Project
////////////////////////////////////////////////

import java.net.*;
import java.io.*;
import java.util.*;
import java.security.KeyFactory;
import java.security.PublicKey;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;

import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.Semaphore;

public class Server 
{
	private ServerSocket listener;
	protected ArrayList<ServerListener> clients;
	
	private ArrayList<Username> usernameList;
	
	private static final String hostName = "localhost";
	private static final int port[] = {9090,9091};
	private static final int headerLength = 4;
	
	//Generate semaphore for making registration thread safe
	Semaphore register = new Semaphore(1);
	
	//For IDE
	private final static String propertiesDir = "src/properties/";
	//For linux
	//private final static String propertiesDir = "properties/";
	private final static String usernames = "usernames.txt";
	
	private SecretKey sessionKey;
	
	public Server()
	{
		// initialize list of anonymous usernames to be assigned to users as they join the chat
		usernameList = new ArrayList<>();
		try
		{
			//Initialize list of clients that utilize the chat system
			clients = new ArrayList<>();
			//Get input stream for username file
			Scanner io = new Scanner(new FileInputStream(propertiesDir + usernames));
			
			while(io.hasNextLine())
			{
				usernameList.add(new Username(io.nextLine()));
			}
			
		}
		catch(Exception ex)
		{
			System.out.println("FATAL: Server failed to load usernames. Make sure usernames.txt is in the proper location.");
			System.out.println(ex.toString());
			System.exit(-1);
		}
		
		generateSessionKey();
	}
	
	private void generateSessionKey()
	{
		try{
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(128);
			this.sessionKey = keyGen.generateKey();
		}
		catch(Exception ex)
		{
			System.out.println("FATAL: Error generating session key.");
			System.out.println(ex.toString());
		}
	}
	
	//Start server without a given port number
	public void startServer()
	{
		ServerListener currClient;
		for(int i = 0; i < port.length; i++)
		{
			try
			{
				listener = new ServerSocket(port[i]);
				System.out.println("Server running on...");
				System.out.println("Host: " + hostName);
				System.out.println("Port: " + port[i]);
				
				while(true)
				{
					try
					{
						Socket socket = listener.accept();
						currClient = new ServerListener(socket);
						clients.add(currClient);
						Thread thread = new Thread(currClient);
						thread.start();
					}	                
					catch(Exception ex)
					{
						System.out.println("FATAL: Server failed to create socket for client");
						System.out.println(ex.toString());
						break;
					}
				}
			}
			catch(IOException IOEx)
			{
				if(i == 1)
				{
					System.out.println("Servers already started on designated ports.");
					System.out.println(IOEx.toString());
					System.exit(0);
				}
				continue;
			}
			catch(Exception ex)
			{
				System.out.println("FATAL: Server failed to create server socket.");
				System.out.println(ex.toString());
				System.exit(-1);
			}
		}
	}
	
	private byte[] encrypt(byte[] msgBytes, int offset, int length)
	{
		byte[] cipherText = new byte[length];
		for(int i = 0; i < length; i++)
		{
			cipherText[i] = msgBytes[i + offset];
		}
		try{
			Cipher aesCipher = Cipher.getInstance("AES");
			aesCipher.init(Cipher.ENCRYPT_MODE, sessionKey);
			cipherText = aesCipher.doFinal(cipherText);
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		
		return cipherText;
	}
	
	private String decrypt(byte[] msgBytes, int offset, int length)
	{
		String msg = "";
		byte[] cipherText = new byte[length];
		for(int i = 0; i < length; i++)
		{
			cipherText[i] = msgBytes[i + offset];
		}
		try{
			Cipher aesCipher = Cipher.getInstance("AES");
			aesCipher.init(Cipher.ENCRYPT_MODE, sessionKey);
			msg = new String(aesCipher.doFinal(cipherText));
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		
		return msg;
	}
	
	public void closeConnections()
	{
		try
		{
			listener.close();
		}
		catch(Exception ex)
		{
			System.out.println("WARNING: Server Socket closed prematurely");
			System.out.println(ex.toString());
		}
	}
	
	public static void main(String[] args)
	{
		Server myServer = new Server();
		if(args.length == 1)
		{
			myServer.startServer();
		}
		else
		{
			myServer.startServer();
		}
		
		myServer.closeConnections();
		
		System.out.println("Server closed successfully");
		System.exit(0);
	}
	
	private class ServerListener implements Runnable
	{
		private String msg = "";
		private byte[] msgBytes = new byte[99999];
		private String userName = "";
		private String header = "";
		private int msgLength;
		private int bytesRead;
		
		Socket client;
		DataInputStream clientInput;
		DataOutputStream clientOutput;
		PublicKey userPubKey;
		
		public ServerListener(Socket socket)
		{
			client = socket;
			try
			{
				clientInput = new DataInputStream(socket.getInputStream());
				clientOutput = new DataOutputStream(socket.getOutputStream());
			}
			catch(Exception ex)
			{
				System.out.println("FATAL: System failed to retrieve input stream from client.");
				System.out.println(ex.toString());
				System.exit(-1);
			}
		}
	
		@Override
		public void run()
		{
			try
			{
				while(!header.equalsIgnoreCase("EXIT"))
				{
						//Retrieve msg
						msgLength = clientInput.readInt();
						bytesRead = clientInput.read(msgBytes, 0, msgLength);
						if(bytesRead != msgLength)
						{
							System.out.println("Server did not read enough bytes before attempting to execute code.");
							System.out.println(bytesRead + "/" + msgLength + " bytes read.");
							System.out.println("From stream: " + new String(msgBytes,0,bytesRead));
						}
						header = new String(msgBytes, 0, headerLength);
				
						switch(header)
						{
							case "REGR":
								registerUser();
								msg = "Server: " + this.userName + " has joined the chat.";
								System.out.println(msg);
								break;
							case "MESG":
								System.out.println(this.userName + ": " + new String(msgBytes, (headerLength + 1), msgLength - (headerLength + 1)));
								broadcastMessage(msgBytes, headerLength+1, msgLength - (headerLength + 1));
								break;
							case "SKEY":
								sendSessionKey(retrievePublicKey(msgBytes, headerLength + 1, msgLength - (headerLength + 1)));
								break;
							case "EXIT":
								deregisterUser();
								break;
						}
				}
				System.out.println("Client has closed the connection.");
				notifyOfDeReg(this.getUsername());
				client.close();
				clientInput.close();
			}
			catch(Exception ex)
			{
				notifyOfDeReg(this.getUsername());
				System.out.println("FATAL: Client Handler failed to create input stream for receiving client requests.");
				System.out.println(ex.toString());
			}
		}
		
		private PublicKey retrievePublicKey(byte[] msgBytes, int offset, int length)
		{
			PublicKey key = null;
			int keySize, bytesRead;
			byte[] keyBytes = new byte[length];
			for(int i = 0; i < length; i++)
			{
				keyBytes[i] = msgBytes[i + offset];
			}
			
			try{
				X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
				KeyFactory fact = KeyFactory.getInstance("RSA");
				key =  fact.generatePublic(spec);
			}
			catch(Exception ex)
			{
				System.out.println("FATAL: Error retrieving clients public key.");
				System.out.println(ex.toString());
			}
			this.userPubKey = key;
			return key;
		}
		
		private void sendSessionKey(PublicKey userPubKey)
		{
			byte[] cipherBytes;
			try{
				Cipher cipher = Cipher.getInstance("RSA");
				cipher.init(Cipher.ENCRYPT_MODE, userPubKey);
				cipherBytes = cipher.doFinal(sessionKey.getEncoded());
				
				clientOutput.writeInt(cipherBytes.length);
				clientOutput.write(cipherBytes, 0, cipherBytes.length);
			}
			catch(Exception ex)
			{
				System.out.println("FATAL: Error sending session key to client.");
				System.out.println(ex.toString());
			}
		}
		
		private byte[] encryptWithPublicKey(String msg, PublicKey userPubKey)
		{
			byte[] cipherText = null;
			try{
				Cipher rsaCipher = Cipher.getInstance("RSA");
				rsaCipher.init(Cipher.ENCRYPT_MODE, this.userPubKey);
				cipherText = rsaCipher.doFinal(msg.getBytes());
			}
			catch(Exception ex)
			{
				System.out.println(ex.toString());
			}
			
			return cipherText;
		}
	
		private void registerUser()
		{
			byte[] msg;
			//Acquire Semaphore to ensure two clients aren't issued the same username at the same time
			try
			{
				register.acquire();
				
				for(int i = 0; i < usernameList.size(); i++)
				{
					if(usernameList.get(i).isAvailable())
					{
						usernameList.get(i).setNotAvailable();
						setUsername(usernameList.get(i).getName());
						msg = ("REG " + this.userName).getBytes();
						clientOutput.writeInt(msg.length);
						clientOutput.write(msg,0,msg.length);
						clientOutput.flush();
						
						sendUserList();
						notifyOfReg(this.getUsername());
						break;
					}
				}
			}
			catch(Exception ex)
			{
				System.out.println("FATAL: System failed to acquire access to registration critical zone.");
				System.out.println(ex.toString());
				System.exit(-1);
			}
			finally
			{
				//release lock on critical zone
				register.release();
			}			
		}
		
		private void notifyOfReg(String username)
		{
			byte[] header = "REG ".getBytes();
			byte[] usernameBytes = username.getBytes();
			byte[] msg = new byte[header.length + username.length()];
			for(int i = 0; i < header.length; i++)
			{
				msg[i] = header[i];
			}
			for(int i = 0; i < usernameBytes.length; i++)
			{
				msg[i + header.length] = usernameBytes[i];
			}
			DataOutputStream tempOutput;
			ServerListener temp;
			Iterator<ServerListener> iterator = clients.iterator();
			while(iterator.hasNext())
			{
				temp = iterator.next();
				if(!temp.getUsername().equals(this.getUsername()))
				{
					tempOutput = temp.clientOutput;
					try{
						System.out.println(new String(msg));
						tempOutput.writeInt(header.length + usernameBytes.length);
						tempOutput.write(msg,0,header.length + usernameBytes.length);
						tempOutput.flush();
					}
					catch(Exception ex)
					{
						System.out.println(ex.toString());
					}
				}
			}
		}
		
		private void notifyOfDeReg(String username)
		{
			byte[] header = "DRG ".getBytes();
			byte[] usernameBytes = username.getBytes();
			byte[] msg = new byte[header.length + username.length()];
			for(int i = 0; i < header.length; i++)
			{
				msg[i] = header[i];
			}
			for(int i = 0; i < usernameBytes.length; i++)
			{
				msg[i + header.length] = usernameBytes[i];
			}
			DataOutputStream tempOutput;
			ServerListener temp;
			Iterator<ServerListener> iterator = clients.iterator();
			while(iterator.hasNext())
			{
				temp = iterator.next();
				if(!temp.getUsername().equals(this.getUsername()))
				{
					tempOutput = temp.clientOutput;
					try{
						tempOutput.writeInt(msg.length);
						tempOutput.write(msg,0,msg.length);
						tempOutput.flush();
					}
					catch(Exception ex)
					{
						System.out.println(ex.toString());
					}
				}
			}
		}
		
		private void sendUserList()
		{
			Username temp;
			int count = 0;
			String users = "";
			Iterator<Username> myIterator = usernameList.iterator();
			while(myIterator.hasNext()){
				temp = myIterator.next();
				if(!temp.isAvailable()){
					count++;
					if(users.equals(""))
					{
						users = users.concat(temp.getName());
					}
					else
					{
						users = users.concat("," + temp.getName());
					}
				}
			}
			
			users = count + " " + users;
			try{
				clientOutput.writeInt(users.length());
				clientOutput.write(users.getBytes(), 0, users.length());
				clientOutput.flush();
			}
			catch(Exception ex)
			{
				System.out.println(ex.toString());
			}
		}
	
		private void deregisterUser()
		{			
			String msg;
			try
			{
				register.acquire();
			
				for(int i = 0; i < clients.size(); i++)
				{
					if(usernameList.get(i).getName().equals(this.userName))
					{
						usernameList.get(i).resetAvailable();
						msg = "Server: " + this.userName + " has disconnected from the chat.";
						System.out.println(msg);
						//broadcastMessage(msg.getBytes(), 0, msg.length());
						break;
					}
				}
			}
			catch(Exception ex)
			{
				System.out.println("Error when attempting to deregister user.");
				System.out.println(ex.toString());
			}
			finally
			{
				register.release();
				msg = "ACK EXIT";
				try{
					clientOutput.writeInt(msg.length());
					clientOutput.write(msg.getBytes(), 0, msg.length());
				}
				catch(Exception ex)
				{
					System.out.println(ex.toString());
				}
			}
		}
	
		private void broadcastMessage(byte[] msgBytes, int offset, int length)
		{
			byte[] header = "MSG ".getBytes();
			byte[] msg = new byte[header.length + length];
			for(int i = 0; i < header.length; i++)
			{
				msg[i] = header[i];
			}
			for(int i = 0; i < length; i++)
			{
				msg[i + header.length] = msgBytes[i + offset];
			}
			DataOutputStream tempOutput;
			ServerListener temp;
			Iterator<ServerListener> iterator = clients.iterator();
			while(iterator.hasNext())
			{
				temp = iterator.next();
				tempOutput = temp.clientOutput;
				try{
					tempOutput.writeInt(msg.length);
					tempOutput.write(msg,0,msg.length);
					tempOutput.flush();
				}
				catch(Exception ex)
				{
					System.out.println(ex.toString());
				}
			}
		}
	
		public String getUsername()
		{
			return userName;
		}
		
		public void setUsername(String userName)
		{
			this.userName = userName;
		}
	}
}