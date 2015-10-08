///////////////////////////////////////////////////
// File:    Client.java
// Name:    Taber Hust
// Class:   CS 4390
// Date:    9/12/2015
// 
// Project1
///////////////////////////////////////////////////

//For properties file containing server address and port number
import java.io.FileInputStream;
import java.util.Properties;

//For TCP socket connection
import java.net.Socket;
import java.io.PrintWriter;
import java.util.Scanner;

public class Client {
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
            System.out.println("FATAL: Program failed to load properties file from properties directory.");
            System.out.println(ex.toString());
            System.exit(-1);
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
            System.out.println("FATAL: Could not connect to client. Check server domain and port for correctness.");
            System.out.println("NOTE: This may be a result of not being connected to the UTD network.\nProgram must be run from the UTD network.");
            System.out.println(ex.toString());
            System.exit(-1);
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
        else if(msg.equalsIgnoreCase("EXIT"))
        {
            clientOutput.println(msg);
        }
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
            serverSocket.close();
            serverInput.close();
            clientOutput.close();
            System.out.println("Connections closed successfully");
        }
        catch(Exception ex)
        {
            System.out.println("Socket or input/output streams were prematurely closed before end of program. Could be error.");
            System.out.println(ex.toString());
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
    
    public static void main(String[] args)
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
        
        myClient.closeConnections();
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
                System.out.println("FATAL: Client failed to create listener for server. Program aborted.");
                System.out.println(ex.toString());
                System.exit(-1);
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
                            System.out.println(sender + ": " + msg);
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
                                System.out.println("You have successfully registered for the chat.");
                                System.out.println("To send a private message, type @username before your message (include space after username).");
                                System.out.println("Number of users: " + Integer.toString(numOfUsers));
                                System.out.print("Users: ");
                                for(int i = 0; i < users.length; i++)
                                {
                                    if(i!=0)
                                        System.out.print(", ");
                                    System.out.print(users[i]);
                                }
                                System.out.print("\n");
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
                    System.out.println("Username is already taken. Please enter another name to register.");
                    waiting = false;
                    break;
                case 1:
                    System.out.println("Username is too long. Please enter another name to register.");
                    waiting = false;
                    break;
                case 2:
                    System.out.println("Unknown message format. Contact support");
                    break;
                case 3:
                    System.out.println("The user you have attempted to private message is not a registered user.");
                    break;
                case 4:
                    System.out.println("You have not registered for the chat. Please do so.");
                    break;
            }
        }
    }
}
