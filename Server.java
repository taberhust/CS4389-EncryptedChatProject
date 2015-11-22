////////////////////////////////////////////////
// File:    Server.java
// Name:    Taber Hust
// Class:   CS 4390
// Date:    9/12/2015
// 
// Project 1
////////////////////////////////////////////////

//Socket Libraries
import java.net.ServerSocket;
import java.net.Socket;

//I/O Libaries for Properties and Sockets
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.Scanner;

//Properties file
import java.util.Properties;

//LinkedList for client management
import java.util.ArrayList;
import java.util.Iterator;

import java.util.concurrent.Semaphore;

public class Server {
    private ServerSocket listener;
    protected ArrayList<ServerListener> clients;
    private String hostName;
    private int port;
    private int nextID;
    
    //Generate semaphore for making registration thread safe
    Semaphore register = new Semaphore(1);
    
    private Properties properties;
    
    //For IDE
    //private final static String propertiesDir = "src/properties/";
    //For linux
    private final static String propertiesDir = "properties/";
    private final static String propertiesFile = "server.properties";
    
    public Server()
    {
        //Start id at 0
        nextID = 0;
        try{
            //Initalize objects
            clients = new ArrayList<>();
            //Get input stream for properties file
            FileInputStream io = new FileInputStream(propertiesDir + propertiesFile);
            
            //Initialize properties object and load from input stream
            properties = new Properties();
            properties.load(io);
            
            hostName = properties.getProperty("domain");
            String portS = properties.getProperty("port");
            if (portS != null)
            {
                port = Integer.parseInt(portS);
            }
            else //port not specified in properties, use 9090 as default
            {
                port = 9090;
            }
        }
        catch(Exception ex)
        {
            System.out.println("FATAL: Server failed to load properties file. Make sure properties file is in the proper location.");
            System.out.println(ex.toString());
            System.exit(-1);
        }
    }
    
    //Start server without a given port number
    public void startServer()
    {
        ServerListener currClient;
        try{
            listener = new ServerSocket(port);
            
            while(true){
                try{
                    Socket socket = listener.accept();
                    currClient = new ServerListener(nextID++,socket);
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
        catch(Exception ex)
        {
            System.out.println("FATAL: Server failed to create server socket.");
            System.out.println(ex.toString());
            System.exit(-1);
        }
    }
    
    public void closeConnections()
    {
        try{
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
        private int clientID;
        private String msg = "";
        private String userName = "";
        private String header = "";
        private String recipient = "";
        
        Socket client;
        Scanner clientInput;
        PrintWriter clientOutput;
        
        public ServerListener(int ID, Socket socket){
            clientID = ID;
            client = socket;
            try{
                clientInput = new Scanner(socket.getInputStream());
                clientOutput = new PrintWriter(socket.getOutputStream());
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
            try{
                clientInput = new Scanner(client.getInputStream());
                while(!msg.equalsIgnoreCase("EXIT"))
                {
                    if(clientInput.hasNextLine())
                    {
                        //Retrieve msg
                        msg = clientInput.nextLine();
                        System.out.println(this.userName + " " + msg);
                        //Pull out header from message
                        if(msg.contains(" "))
                        {
                            header = msg.substring(0,msg.indexOf(" "));
                            msg = msg.substring(header.length() + 1);
                        }
                        else
                        {
                            header = msg; //no space means the msg is a header ("EXIT")
                        }
                        switch(header)
                        {
                            case "REG":
                                if(registerUser(msg))
                                {
                                    broadcastMessage("SERVER", this.userName + " has joined the chat.");
                                }
                                else
                                {
                                    clientOutput.println("ERR 0");
                                }
                                break;
                            case "MESG":
                                if(userName.equals(""))
                                {
                                    clientOutput.println("ERR 4");
                                }
                                else
                                {
                                    broadcastMessage(this.userName,msg);
                                }
                                break;
                            case "PMSG":
                                if(userName.equals(""))
                                {
                                    clientOutput.println("ERR 4");
                                }
                                else
                                {
                                    recipient = msg.substring(0,msg.indexOf(" "));
                                    msg = msg.substring(recipient.length() + 1);
                                    sendPrivateMessage(recipient,msg);
                                }
                                break;
                            case "EXIT":
                                deregisterUser();
                                break;
                        }
                        
                        clientOutput.flush();
                    }
                }
                System.out.println("Client has closed the connection.");
                client.close();
                clientInput.close();
            }
            catch(Exception ex)
            {
                System.out.println("FATAL: Client Handler failed to create input stream for receiving client requests.");
                System.out.println(ex.toString());
            }
        }
        
        private boolean registerUser(String username)
        {
            String ack = "ACK";
            int currentClientIndex = -1;
            boolean canRegister = true;
            
            //Acquire Semaphore to ensure two clients don't enter the same username at the same time and bypass the registration check
            try{
                register.acquire();
            
            
                for(int i = 0; i < clients.size(); i++)
                {
                    if(clientID == clients.get(i).getID())
                    {
                        currentClientIndex = i;
                    }
                    //If username is taken, return false for error
                    else if(username.equalsIgnoreCase(clients.get(i).getUsername()))
                    {
                        canRegister = false;
                    }
                }
                //Client ID did not match with any clients in list
                if(currentClientIndex == -1)
                {
                    clientOutput.println("ERR 4");
                    clientOutput.flush();
                    register.release();
                    System.exit(-1);
                }
                else if(canRegister)
                {
                    clients.get(currentClientIndex).setUsername(username);
                    ack = ack.concat(" " + Integer.toString(clients.size()) + " " + clients.get(0).getUsername());
                    for(int i = 1; i < clients.size(); i++)
                    {
                        ack = ack.concat("," + clients.get(i).getUsername());
                    }
                    clientOutput.println(ack);
                    clientOutput.flush();
                }
            }
            catch(Exception ex)
            {
                System.out.println("FATAL: System failed to acquire access to registration critical zone.");
                System.out.println(ex.toString());
                System.exit(-1);
            }
            finally{
                //release lock on critical zone
                register.release();
            }
            
            return canRegister;
        }
        
        private void deregisterUser()
        {
            try{
                register.acquire();
                
                for(int i = 0; i < clients.size(); i++)
                {
                    if(clients.get(i).getUsername().equals(this.userName))
                    {
                        clients.remove(i);
                        broadcastMessage("SERVER", this.userName + " has disconnected from the chat.");
                    }
                }
            }
            catch(Exception ex)
            {
                System.out.println("Error when attempting to deregister user.");
                System.out.println(ex.toString());
            }
            finally{
                register.release();
                clientOutput.println("ACK EXIT");
            }
        }
        
        private void broadcastMessage(String username, String mesg)
        {
            PrintWriter tempOutput;
            ServerListener temp;
            Iterator<ServerListener> iterator = clients.iterator();
            while(iterator.hasNext())
            {
                temp = iterator.next();
                if(!temp.userName.equals(this.userName))
                {
                    tempOutput = temp.clientOutput;
                    tempOutput.println("MSG " + username + " " + mesg);
                    tempOutput.flush();
                }
            }
        }
        
        private void sendPrivateMessage(String username, String mesg)
        {
            boolean found = false;
            ServerListener temp;
            PrintWriter tempOutput;
            Iterator<ServerListener> iterator = clients.iterator();
            while(iterator.hasNext())
            {
                temp = iterator.next();
                if(temp.getUsername().equals(username))
                {
                    found = true;
                    tempOutput = temp.clientOutput;
                    tempOutput.println("MSG " + this.userName + " " + mesg);
                    tempOutput.flush();
                }
            }
            if(!found)
            {
                this.clientOutput.println("ERR 3");
                this.clientOutput.flush();
            }
        }
        
        public int getID()
        {
            return clientID;
        }
        
        public void setID(int id)
        {
            clientID = id;
        }
        
        public String getUsername()
        {
            return userName;
        }
        
        public void setUsername(String username)
        {
            userName = username;
        }
    }
}