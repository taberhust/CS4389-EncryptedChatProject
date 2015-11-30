package security;

///////////////////////////////////////////////////
//File:    Client.java
//Name:    Taber Hust, Michael Munzing, Brad Kupka
//Class:   CS 4389
//Date:    11/22/2015
//
//Final Project
//JavaFX is needed for this project to work!
///////////////////////////////////////////////////

import java.io.*;
import java.security.*;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import java.net.*;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.ArrayList;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Client extends Application 
{		
	//Stores server information
	private final static String defaultServerAddress = "localhost";
	private final static int [] defaultServerPort = {9090, 9091};
	private final static int headerLength = 3;
	
	//Input and output streams to server and associated socket
	private DataInputStream serverInput;
	private DataOutputStream clientOutput;
	private Socket serverSocket;
	private String serverAddress;
	private int[] serverPort;
	
	//Thread class for incoming chat message listeners
	private ClientListener msgListener;
	
	private Thread listener;
	
	protected boolean communication;
	private boolean registered = false;
	private String username;
	private int numOfUsers;
	private ArrayList<String> chatList;
	
	private PublicKey pubKey;
	private PrivateKey privKey;
	private SecretKey sessionKey;
	private static final String PRIVATE_KEY_FILE = "src/properties/private.key";
	private static final String PUBLIC_KEY_FILE = "src/properties/public.key";
	
	private Button btSend = new Button("Send"), btClear = new Button("Clear");
	private Button btDisconnect = new Button("Disconnect");
	private TextField messageBox = new TextField();
	private static TextArea chatBox = new TextArea();
	
	public void setHost(String host)
	{
		serverAddress = host;
	}
	
	public void setPorts(int[] ports)
	{
		serverPort = ports;
	}
	
	public void start(Stage primaryStage) throws Exception
	{
		/* Collect arguments and pass to correct variables */
		Parameters argParams = getParameters();
		List<String> args = argParams.getRaw();
		if(args.size() < 2)
		{
			System.out.println("Use: java Client <host name> <port #1> <alternate port #1> ... <alternate port #n>");
			System.out.println("Using default: <host name> = \"net01.utdallas.edu\" and <port #> = \"9090\" or \"9091\"");
			setHost(defaultServerAddress);
			setPorts(defaultServerPort);
		}
		else
		{
			setHost(args.get(0));
			int[] portNum = new int[args.size() - 1];
			for(int i = 0; i < portNum.length; i++)
			{
				portNum[i] = Integer.parseInt(args.get(i + 1));
			}
			setPorts(portNum);
		}
		btSend.setDefaultButton(true);
		btDisconnect.setCancelButton(true);
		primaryStage.setOnCloseRequest(e -> closeConnections());
		btSend.setOnAction(e -> 
		{ 
			if(messageBox.getText() != null && !messageBox.getText().equals(""))
			{
				sendMsg(username + ": " + messageBox.getText());
			}
			messageBox.setText("");
		});
		
		btDisconnect.setOnAction(e -> closeConnections());
		btClear.setOnAction(e -> clearChat());
		btSend.setPrefSize(100, 100);
		btClear.setPrefSize(100, 100);
		btDisconnect.setPrefSize(100, 100);
		chatBox.setEditable(false);
		chatBox.setPrefHeight(250);
		chatBox.setWrapText(true);
		messageBox.setPrefHeight(50);
		
		chatBox.appendText("Welcome to the CS4389 chat server!\n");
		
		BorderPane mainPane = new BorderPane();
		GridPane btPane = new GridPane();
		GridPane boxPane = new GridPane();
		
		boxPane.add(chatBox, 0, 0);
		boxPane.add(messageBox, 0, 1);
		
		btPane.add(btDisconnect, 0, 0);
		btPane.add(btClear, 0, 1);
		btPane.add(btSend, 0, 2);
		
		mainPane.setLeft(boxPane);
		mainPane.setRight(btPane);
		Scene scene = new Scene(mainPane);
		primaryStage.setScene(scene);
		primaryStage.setTitle("Secure Chatroom");
		primaryStage.show();
		
		//Public/Private key pair generation
		if(!keysGenerated())
			generateKeys();
		else
			loadKeys();
		
		connectToServer();
	}	// End of start function
	
	private byte[] sign(byte[] msg)
	{
		byte[] signature = null;
		try{
			Signature sign = Signature.getInstance("SHA1withRSA");
			sign.initSign(this.privKey);
			sign.update(msg);
			signature = sign.sign();
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		
		return signature;
	}
	
	private boolean keysGenerated()
	{
		File privKeyFile = new File(PRIVATE_KEY_FILE);
		File pubKeyFile = new File(PUBLIC_KEY_FILE);
		
		if(privKeyFile.exists() && pubKeyFile.exists())
			return true;

		else
			return false;
	}
	
	private byte[] decryptWithPrivateKey(byte[] cipherText)
	{
		byte[] msg = null;
		try{
			Cipher rsaCipher = Cipher.getInstance("RSA");
			rsaCipher.init(Cipher.DECRYPT_MODE, this.privKey);
			msg = rsaCipher.doFinal(cipherText);
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		
		return msg;
	}
	
	private String decryptWithPrivateKey(byte[] msgBytes, int offset, int length)
	{
		byte[] msg = null;
		byte[] cipherText = new byte[length];
		for(int i = 0; i < length; i++)
		{
			cipherText[i] = msgBytes[i + offset];
		}
		
		try{
			Cipher rsaCipher = Cipher.getInstance("RSA");
			rsaCipher.init(Cipher.DECRYPT_MODE, this.privKey);
			msg = rsaCipher.doFinal(cipherText);
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		
		return new String(msg);
	}
	
	/*Send server public key to retrieve cipher text of session key*/
	private void swapKeys()
	{
		byte[] header = "SKEY ".getBytes();
		byte[] sessionKeyBytes;
		byte[] pubKeyBytes = this.pubKey.getEncoded();
		byte[] signBytes;
		byte[] msg = new byte[pubKeyBytes.length + header.length];
		for(int i = 0; i < header.length; i++)
		{
			msg[i] = header[i];
		}
		for(int i = 0; i < pubKeyBytes.length; i++)
		{
			msg[i + header.length] = pubKeyBytes[i];
		}
		try{
			//Send SKEY and public key
			clientOutput.writeInt(msg.length);
			clientOutput.write(msg, 0, msg.length);
			
			signBytes = sign(pubKeyBytes);
			clientOutput.writeInt(signBytes.length);
			clientOutput.write(signBytes, 0, signBytes.length);
			
			int sessionKeySize = serverInput.readInt();
			if(sessionKeySize != 0)
			{
				sessionKeyBytes = new byte[sessionKeySize];
				int bytesRead = serverInput.read(sessionKeyBytes, 0, sessionKeySize);
				if(sessionKeySize != bytesRead)
				{
					System.out.println("FATAL: Client did not retrieve the whole session key byte array.");
				}
				else
				{
					setSessionKey(decryptWithPrivateKey(sessionKeyBytes));
				}
			}
			else
			{
				displayMessage("ERROR: Possible security threat detected. Exiting system for your protection.");
				closeConnections();
			}
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
	}
	
	private void setSessionKey(SecretKey key)
	{
		this.sessionKey = key;
	}
	
	private void setSessionKey(byte[] keyBytes)
	{
		try{
			this.sessionKey = new SecretKeySpec(keyBytes, 0, keyBytes.length, "AES");
		}
		catch(Exception ex)
		{
			System.out.println("FATAL: Error retrieving session key.");
			System.out.println(ex.toString());
		}
	}
	
	private void generateKeys()
	{
		try
		{
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
			keyGen.initialize(1024);
			KeyPair keys = keyGen.generateKeyPair();
			this.privKey = keys.getPrivate();
			this.pubKey = keys.getPublic();
			File privKeyFile = new File(PRIVATE_KEY_FILE);
			File pubKeyFile = new File(PUBLIC_KEY_FILE);
			ObjectOutputStream keyOS;
			
			if(!privKeyFile.exists())
			{
				privKeyFile.createNewFile();
				keyOS = new ObjectOutputStream(new FileOutputStream(privKeyFile));
				keyOS.writeObject(keys.getPrivate());
			}
			if(!pubKeyFile.exists())
			{
				pubKeyFile.createNewFile();
				keyOS = new ObjectOutputStream(new FileOutputStream(pubKeyFile));
				keyOS.writeObject(keys.getPublic());
			}
		}
		catch(Exception ex)
		{
			Platform.runLater(() -> {
				chatBox.appendText(ex.toString() + "\n");
			});
		}
	}
	
	private void loadKeys()
	{
		File pubKeyFile = new File(PUBLIC_KEY_FILE);
		File privKeyFile = new File(PRIVATE_KEY_FILE);
		ObjectInputStream keyIS;
		
		if(pubKeyFile.exists())
		{
			try
			{
				keyIS = new ObjectInputStream(new FileInputStream(pubKeyFile));
				this.pubKey = (PublicKey) keyIS.readObject();
			}
			catch(Exception ex)
			{
				Platform.runLater(() -> {
					chatBox.appendText("FATAL: Error loading public key.\n");
					chatBox.appendText(ex.toString() + "\n");
				});
			}
		}
		if(privKeyFile.exists())
		{
			try
			{
				keyIS = new ObjectInputStream(new FileInputStream(privKeyFile));
				this.privKey = (PrivateKey) keyIS.readObject();
			}
			catch(Exception ex)
			{
				Platform.runLater(() -> {
					chatBox.appendText("FATAL: Error loading private key.\n");
					chatBox.appendText(ex.toString() + "\n");
				});
			}
		}
	}
	
	private byte[] encrypt(String text)
	{
		byte[] cipherText = null;
		try
		{
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, this.sessionKey);
			cipherText = cipher.doFinal(text.getBytes());
		}
		catch(Exception ex)
		{
			Platform.runLater(() -> {
				chatBox.appendText(ex.toString() + "\n");
			});
		}
		return cipherText;
	}
	
	private String decrypt(byte[] msgText, int offset, int length)
	{
		byte[] cipherText = new byte[length];
		for(int i = 0; i < length; i++)
		{
			cipherText[i] = msgText[i + offset];
		}
		String msg = "";
		try
		{
			Cipher cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, this.sessionKey);
			msg = new String(cipher.doFinal(cipherText));
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		return msg;
	}
	
	/**
	* Connects to server using parameters found in properties file
	*/
	public void connectToServer()
	{
		//Connect to server and initialize input/output streams
		for(int i = 0; i < serverPort.length; i++)
		{
			try{
				communication = true;
				serverSocket = new Socket(serverAddress, serverPort[i]);
				serverInput = new DataInputStream(serverSocket.getInputStream());
				clientOutput = new DataOutputStream(serverSocket.getOutputStream());
				
				requestUsername();
				
				swapKeys();
				
				listener = new Thread(new ClientListener(serverSocket));
				listener.start();
				
				break;
			}
			catch(IOException IOEx)
			{
				if(i == 1)
				{
					Platform.runLater(() -> {
						chatBox.appendText("Server capacity reached.");
						chatBox.appendText(IOEx.toString());
					});
				}
				continue;
			}
			catch(Exception ex)
			{
				Platform.runLater(() -> {
					chatBox.appendText("FATAL: Could not connect to client. Check server domain and port for correctness.\n");
					chatBox.appendText("NOTE: This may be a result of not being connected to the UTD network.\nProgram must be run from the UTD network.\n");
					chatBox.appendText(ex.toString() + "\n");
				});
			}
		}
	}
	
	/**
	* Outputs string message to socket output stream
	* @param msg 
	*/
	public void sendMsg(String msg)
	{
		byte[] headerBytes = "MESG ".getBytes();
		byte[] msgBytes;
		byte[] cipherText = encrypt(msg);
		msgBytes = new byte[headerBytes.length + cipherText.length];
		for(int i = 0; i < headerBytes.length; i++)
		{
			msgBytes[i] = headerBytes[i];
		}
		for(int i = 0; i < cipherText.length; i++)
		{
			msgBytes[i+headerBytes.length] = cipherText[i];
		}
		try{
			clientOutput.writeInt(msgBytes.length);
			clientOutput.write(msgBytes, 0, msgBytes.length);
			clientOutput.flush();
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
	}
	
	public void requestUsername()
	{
		byte[] regMsg = "REGR".getBytes();
		int msgLength, bytesRead;
		String response;
		byte[] responseBytes;
		while(registered == false)
		{
			try{
				clientOutput.writeInt(regMsg.length);
				clientOutput.write(regMsg, 0, regMsg.length);
				
				msgLength = serverInput.readInt();
				responseBytes = new byte[msgLength];
				bytesRead = serverInput.read(responseBytes, 0, msgLength);
				if(bytesRead != msgLength)
				{
					System.out.println("Client did not read all bytes of username during registration.");
				}
				else
				{
					this.username = new String(responseBytes, headerLength + 1, msgLength - (headerLength + 1));
					displayMessage("You have successfully registered as username: " + this.username);
					System.out.println("Username is " + this.username);
					registered = true;
					receiveChatList();
				}
			}
			catch(Exception ex)
			{
				System.out.println(ex.toString());
				closeConnections();
				break;
			}
		}
	}
	
	public void receiveChatList()
	{
		this.chatList = new ArrayList<>();
		this.numOfUsers = 0;
		int msgLength, bytesRead;
		byte[] msgBytes = new byte[85];
		String list;
		try{
			msgLength = serverInput.readInt();
			bytesRead = serverInput.read(msgBytes, 0, msgLength);
			System.out.println("Received from server: " + new String(msgBytes, 0, bytesRead));
			
			if(bytesRead != msgLength)
			{
				System.out.println("Client did not read the correct number of bytes when retrieving chat room occupants.");
				System.out.println(bytesRead + "/" + msgLength + " bytes read.");
				System.out.println("In stream: " + new String(msgBytes, 0, bytesRead));
			}
		}
		catch(Exception ex)
		{
			System.out.println(ex.toString());
		}
		
		list = new String(msgBytes);
		if(list.contains(" "))
		{
			String[] chatDetails = list.split(" ");
			numOfUsers = Integer.parseInt(chatDetails[0]);
			if(chatDetails[1].contains(","))
			{
				String[] usernameList = chatDetails[1].split(",");
				for(int i = 0; i < usernameList.length; i++)
				{
					this.chatList.add(usernameList[i]);
				}
			}
			else if(chatDetails[1].equals(""))
			{
				//Do nothing
			}
			else
			{
				this.chatList.add(chatDetails[1]);
			}
			displayChatList();
		}
		else //chat room is either empty or error occurred
		{
			displayChatList();
		}
	}
	
	public void displayChatList()
	{
		String msg = "";
		msg = msg.concat("There are currently " + numOfUsers + " users in this chat room.\n");
		msg = msg.concat("Users list: ");
		Iterator<String> myIterator = this.chatList.iterator();
		while(myIterator.hasNext()){
			msg = msg.concat(myIterator.next());
			if(myIterator.hasNext())
			{
				msg = msg.concat(", ");
			}
		}
		displayMessage(msg);
	}
	
	public void displayMessage(String msg)
	{
		Platform.runLater(() -> {
			chatBox.appendText(msg + "\n");
		});
	}
	
	/**
	* Closes all sockets and input/output streams
	*/
	public void closeConnections()
	{
		try
		{
			clientOutput.writeInt(4);
			clientOutput.write("EXIT".getBytes(), 0, 4);
			clientOutput.flush();
			
			serverSocket.close();
			serverInput.close();
			clientOutput.close();
			System.exit(1);
		}
		catch(Exception ex)
		{
			Platform.runLater(() -> {
				chatBox.appendText("Socket or input/output streams were prematurely closed before end of program. Could be error.\n");
				chatBox.appendText(ex.toString() + "\n");
			});
		}
	}
		
	public boolean isRegistered()
	{
		return registered;
	}
	
	public void setRegistered(boolean status)
	{
		this.registered = status;
	}
	
	public void clearChat()
	{
		chatBox.setText("");
	}
	
	public static void main(String[] args)
	{        
		launch(args);
		System.exit(0);
	}
	
	private class ClientListener implements Runnable
	{
		private DataInputStream serverInputStream;
		String msg = "";
		
		public ClientListener(Socket socket)
		{
			try
			{
				serverInputStream = new DataInputStream(socket.getInputStream());
			}
			catch(Exception ex)
			{
				String errorString = "";
				errorString.concat("FATAL: Client failed to create listener for server. Program aborted.\n");
				errorString.concat(ex.toString() + "\n");
				Platform.runLater(() -> {
					chatBox.appendText(errorString);
				});
			}
		}
	
		@Override
		public void run()
		{
			String msg = "";
			String header = "";
			byte[] msgBytes = new byte[9999];
			int msgLength, bytesRead;
			String[] users;
			
			//Continue to read from server until connections are closed
			if(registered == true)
			{
				while(communication == true)
				{
					try{
						msgLength = serverInput.readInt();
						bytesRead = serverInput.read(msgBytes, 0, msgLength);
						
						System.out.println("Received: " + new String(msgBytes, 0, msgLength));
										
						if(bytesRead != msgLength)
						{
							System.out.println("Client failed to read the correct number of bytes from the server.");
							System.out.println(bytesRead + "/" + msgLength + " bytes read from stream.");
							System.out.println("From stream: " + new String(msgBytes, 0, bytesRead));
						}
						
						header = new String(msgBytes, 0, headerLength);
						
						switch(header)
						{
							case "REG": //new user has registered for this chat room
								msg = new String(msgBytes, headerLength + 1, msgLength - (headerLength + 1));
								chatList.add(msg);
								numOfUsers++;
								msg = msg.concat(" has joined the chat.");
								break;
							case "PRV":
								msg = decryptWithPrivateKey(msgBytes, (headerLength + 1), msgLength - (headerLength +1));
								break;
							case "MSG":
								msg = decrypt(msgBytes, (headerLength + 1), msgLength - (headerLength + 1));
								break;
							case "DRG": //user has left the chat room
								msg = new String(msgBytes, headerLength + 1, msgLength - (headerLength + 1));
								chatList.remove(chatList.indexOf(msg));
								numOfUsers--;
								msg = msg.concat(" has left the chat.");
								break;
							case "ERR":
								handleError((int) msgBytes[headerLength + 1]);
								break;
						}
					}
					catch(Exception ex)
					{
						System.out.println(ex.toString());
						break;
					}
					
					displayMessage(msg);
					System.out.println("Decrypted: " + msg);
				}
			}
			else
			{
				Platform.runLater(() -> {
					chatBox.appendText("There was an error registering you for the chat. Contact tech support.\n");
					closeConnections();
					communication = false;
				});
			}
		}
		
		private void displayMessage(String msg)
		{
			Platform.runLater(() -> {
				chatBox.appendText(msg + "\n");
			});
		}
	
		private void handleError(int code)
		{
			String errorString = "";
			switch(code)
			{
			case 0:
				errorString.concat("Server Capacity Reached. Close program and try again later.\n");
				break;
			}
			
			Platform.runLater(() -> {
				chatBox.appendText(errorString);
			});
		}
	}
}