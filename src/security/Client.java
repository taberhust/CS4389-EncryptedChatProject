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

//For properties file containing server address and port number
import java.io.FileInputStream;
import java.util.Properties;

//For TCP socket connection
import java.net.Socket;
import java.io.PrintWriter;
import java.util.Scanner;

import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;

public class Client extends Application 
{
	
	//Stores server information
	private static final String serverAddress = "localhost";
	private static final int[] serverPort = {9090,9091};
	
	private String userName;
	
	//Input and output streams to server and associated socket
	private Scanner serverInput;
	private PrintWriter clientOutput;
	private Socket serverSocket;
	
	//Thread class for incoming chat message listeners
	private ClientListener msgListener;
	
	private Thread listener;
	
	protected boolean communication;
	private boolean registered;
	protected boolean waiting;
	
	private Button btSend = new Button("Send"), btClear = new Button("Clear");
	private Button btDisconnect = new Button("Disconnect");
	private TextArea messageBox = new TextArea();
	private static TextArea chatBox = new TextArea();
	
	public void start(Stage primaryStage) throws Exception
	{
		primaryStage.setOnCloseRequest(e -> hardCloseConnections());
		btSend.setOnAction(e -> 
		{
			sendMsg(messageBox.getText());
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
		messageBox.setWrapText(true);
		
		chatBox.appendText("Welcome to the CS4389 chat server!\n");
		chatBox.appendText("Please enter a username to register: \n");
		
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
		primaryStage.setTitle("Secure chatroom");
		primaryStage.show();
		
		connectToServer();
	}	// End of start function
	
	/**
	* Connects to server using parameters found in properties file
	*/
	public void connectToServer()
	{
		for(int i = 0; i < serverPort.length; i++)
		{
			//Connect to server and initialize input/output streams
			try{
				communication = true;
				serverSocket = new Socket(serverAddress, serverPort[i]);
				serverInput = new Scanner(serverSocket.getInputStream());
				clientOutput = new PrintWriter(serverSocket.getOutputStream());
				
				listener = new Thread(new ClientListener(serverSocket));
				listener.start();
				break;
			}
			catch(IOException IOEx)
			{
				if(i==1)
				{
					chatBox.appendText("FATAL: Client could not connect to server.");
					chatBox.appendText(IOEx.toString());
					//System.exit(-1);
				}
				continue;
			}
			catch(Exception ex)
			{
				chatBox.appendText("FATAL: Unexpected exception.");
				chatBox.appendText(ex.toString() + "\n");
				//System.exit(-1);
			}
		}
	}
	
	private void listenToServer()
	{
		while(communication == true)
		{
			if(this.serverInput.hasNext())
			{
				String msg = this.serverInput.nextLine();
				chatBox.appendText(msg);
			}
		}
	}
	
	/**
	* Outputs string message to socket output stream
	* @param msg 
	*/
	public void sendMsg(String msg)
	{
		//@username signifies private message
		if(msg.startsWith("@"))
		{
			clientOutput.println("PMSG " + msg.substring(msg.indexOf("@") + 1));
		}
		//EXIT closes connection with server and deregisters user
		//else if(msg.equalsIgnoreCase("EXIT"))
		//{
		//clientOutput.println(msg);
		//}
		//Standard broadcast message
		else
		{
			clientOutput.println("MESG " + msg);
		}
		clientOutput.flush();
	}
	
	public void sendUsername(String username)
	{
		clientOutput.println("REG " + username);
		clientOutput.flush();
	}
	
	/**
	* Closes all sockets and input/output streams
	*/
	public void closeConnections()
	{
		try
		{
			clientOutput.println("EXIT");
			clientOutput.flush();
			serverSocket.close();
			serverInput.close();
			clientOutput.close();
			chatBox.appendText("Connections closed successfully.\n");
			chatBox.appendText("Closing client...\n");
		}
		catch(Exception ex)
		{
			chatBox.appendText("Socket or input/output streams were prematurely closed before end of program. Could be error.\n");
			chatBox.appendText(ex.toString() + "\n");
			//System.exit(-1);
		}
	}
	
	public void hardCloseConnections()
	{
		try
		{
			clientOutput.println("EXIT");
			clientOutput.flush();
			serverSocket.close();
			serverInput.close();
			clientOutput.close();
		}
		catch(Exception ex)
		{
			chatBox.appendText("Socket or input/output streams were prematurely closed before end of program. Could be error.\n");
			chatBox.appendText(ex.toString() + "\n");
			System.exit(-1);
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
	
	public boolean isWaiting()
	{
		return waiting;
	}
	
	public void setWait(boolean status)
	{
		this.waiting = status;
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
		private Scanner serverInputStream;
		String msg = "";
		
		public ClientListener(Socket socket)
		{
			try
			{
				serverInputStream = new Scanner(socket.getInputStream());
			}
			catch(Exception ex)
			{
				chatBox.appendText("FATAL: Client failed to create listener for server. Program aborted.\n");
				chatBox.appendText(ex.toString() + "\n");
				//System.exit(-1);
			}
		}
	
		@Override
		public void run()
		{
			String header;
			String sender;
			int numOfUsers;
			String[] users;
			
			//Continue to read from server until connections are closed
			while(communication == true)
			{
				if(serverInputStream.hasNext())
				{
					msg = serverInputStream.nextLine();
					System.out.println("\"" + msg + "\" was received from server");
					header = msg.substring(0,msg.indexOf(" "));
					msg = msg.substring(header.length() + 1);
					
					switch(header)
					{
						case "ERR":
							handleError(Integer.parseInt(msg));
							break;
						case "MSG":
							chatBox.appendText(msg +"\n");
							break;
						case "ACK":
							if(msg.equals("EXIT"))
							{
								communication = false;
							}
					}
				}
				else
				{
					registered = true;
					numOfUsers = Integer.parseInt(msg.substring(0,msg.indexOf(" ")));
					users = msg.substring(msg.indexOf(" ") + 1).split(",");
					chatBox.appendText("You have successfully registered for the chat.\n");
					chatBox.appendText("To send a private message, type @username before your message (include space after username).\n");
					chatBox.appendText("Number of users: " + Integer.toString(numOfUsers) + "\n");
					chatBox.appendText("Users: ");
					
					for(int i = 0; i < users.length; i++)
					{
						if(i!=0)
						chatBox.appendText(", ");
						chatBox.appendText(users[i]);
					}
					chatBox.appendText("\n");
					break;
				}
			}
		}
	
		private void handleError(int code)
		{
			switch(code)
			{
			case 0:
			chatBox.appendText("Username is already taken. Please enter another name to register.\n");
			waiting = false;
			break;
			case 1:
			chatBox.appendText("Username is too long. Please enter another name to register.\n");
			waiting = false;
			break;
			case 2:
			chatBox.appendText("Unknown message format. Contact support.\n");
			break;
			case 3:
			chatBox.appendText("The user you have attempted to private message is not a registered user.\n");
			break;
			case 4:
			chatBox.appendText("You have not registered for the chat. Please do so.\n");
			break;
			}
		}
	}
}