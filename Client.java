///////////////////////////////////////////////////
// File:    Client.java
// Name:    Taber Hust
// Class:   CS 4390
// Date:    9/12/2015
// 
// Project1
// JavaFX is needed for this project to work!
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

public class Client extends Application {
    //Properties file directory and file name
    //For IDE
    //private final static String propertiesDir = "src/properties/";
    //For linux
    private final static String propertiesDir = "properties/";
    private final static String propertiesFile = "server.properties";
    
    //Stores server information
    private String serverAddress;
    private String serverPort;
    
    private String userName;
    
    //Input and output streams to server and associated socket
    private Scanner serverInput;
    private PrintWriter clientOutput;
    private Socket serverSocket;
    
    //Thread class for incoming chat message listeners
    private ClientListener msgListener;
    
    //Properties file
    private Properties serverProperties;
    
    private Thread listener;
    
    protected boolean communication;
    private boolean registered;
    protected boolean waiting;
    
    private Button btSend = new Button("Send"), btClear = new Button("Clear");
    private Button btDisconnect = new Button("Disconnect");
    private TextArea messageBox = new TextArea();
    private static TextArea chatBox = new TextArea();
    
    public void start(Stage primaryStage) throws Exception{
    	primaryStage.setOnCloseRequest(e -> hardCloseConnections());
    	btSend.setOnAction(e -> { 
    		if(registered) {
    			sendMsg(messageBox.getText());
    			messageBox.setText("");
    		}
    		else {
    			sendUsername(messageBox.getText());
    			messageBox.setText("");
    		}
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
    	
    	chatBox.appendText("Welcome to the CS4390 chat server!\n");
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
     * Default constructor for client
     * Loads server properties from server.properties
     * Initializes variables when necessary
     */    
    public Client(){
        try{
            //Input stream from server.properties
            FileInputStream propertyIO = new FileInputStream(propertiesDir + propertiesFile);
            
            //Load properties into serverProperties using input stream
            serverProperties = new Properties();
            serverProperties.load(propertyIO);
            
            serverAddress = serverProperties.getProperty("domain");
            serverPort = serverProperties.getProperty("port");
            
            registered = false;
            waiting = false;
        }
        catch(Exception ex)
        {
        	chatBox.appendText("FATAL: Program failed to load properties file from properties directory.\n");
        	chatBox.appendText(ex.toString() + "\n");
//            System.exit(-1);
        }
    }
    
    /**
     * Connects to server using parameters found in properties file
     */
    public void connectToServer()
    {
        //Connect to server and initialize input/output streams
        try{
            communication = true;
            serverSocket = new Socket(serverAddress, Integer.parseInt(serverPort));
            serverInput = new Scanner(serverSocket.getInputStream());
            clientOutput = new PrintWriter(serverSocket.getOutputStream());
            
            listener = new Thread(new ClientListener(serverSocket));
            listener.start();
        }
        catch(Exception ex)
        {
            chatBox.appendText("FATAL: Could not connect to client. Check server domain and port for correctness.\n");
            chatBox.appendText("NOTE: This may be a result of not being connected to the UTD network.\nProgram must be run from the UTD network.\n");
            chatBox.appendText(ex.toString() + "\n");
//            System.exit(-1);
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
//        else if(msg.equalsIgnoreCase("EXIT"))
//        {
//            clientOutput.println(msg);
//        }
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
        try{
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
//            System.exit(-1);
        }
    }
    
    public void hardCloseConnections()
    {
        try{
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
    
    public void clearChat(){
    	chatBox.setText("");
    }
    
    public static void main(String[] args)
<<<<<<< HEAD
    {        
        launch(args);
=======
    {
        String msg = "";
        Scanner input = new Scanner(System.in);
        
        Client myClient = new Client();
        myClient.connectToServer();
        
        System.out.println("Welcome to the CS4389 chat server!");
        System.out.print("Please enter a username to register: ");
        
        while(!myClient.isRegistered())
        {
            if(!myClient.isWaiting())
            {
                msg = input.nextLine();
                myClient.sendUsername(msg);
                myClient.setWait(true);
            }
        }
        
        while(!msg.equalsIgnoreCase("EXIT"))
        {
            msg = input.nextLine();
            myClient.sendMsg(msg);
        }
>>>>>>> 4d93daa5d566399d29008b8ae6046ba30bf31fe3
        
        System.exit(0);
    }
    
    private class ClientListener implements Runnable
    {
        private Scanner serverInputStream;
        String msg = "";
        public ClientListener(Socket socket){
            try{
                serverInputStream = new Scanner(socket.getInputStream());
            }
            catch(Exception ex)
            {
            	chatBox.appendText("FATAL: Client failed to create listener for server. Program aborted.\n");
            	chatBox.appendText(ex.toString() + "\n");
//                System.exit(-1);
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
                    header = msg.substring(0,msg.indexOf(" "));
                    msg = msg.substring(header.length() + 1);
                    switch(header)
                    {
                        case "ERR":
                            handleError(Integer.parseInt(msg));
                            break;
                        case "MSG":
                            sender = msg.substring(0,msg.indexOf(" "));
                            msg = msg.substring(sender.length() + 1);
                            chatBox.appendText(sender + ": " + msg +"\n");
                            break;
                        case "ACK":
                            if(msg.equals("EXIT"))
                            {
                                communication = false;
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
