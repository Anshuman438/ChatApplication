import java.io.*;
import java.net.*;
import java.util.Scanner;

public class server{
    public static void main(String args []){
        try{

        Scanner sc = new Scanner(System.in);
        //creating a port for my connection at 5000
        ServerSocket serverSocket = new ServerSocket(5000);

        System.out.println("waiting for client's response");

        //connect if client sends a connection 
        Socket socket = serverSocket.accept();

        System.out.println("client got connected");

        BufferedReader input = new BufferedReader( new InputStreamReader(socket.getInputStream()));
        
        //socket.getInputStream()  -> this takes the input from the client
        //InputStreamReader()      -> this make the input readable to computer 
        //BufferedReader()         -> this give useful futons like Readline() and all


        //store the input 
        String message = input.readLine();

        System.out.println("the client said : "+ message);

        
        //reply to client
        PrintWriter reply = new PrintWriter(socket.getOutputStream() , true);
        System.out.println("say something to client");
        String msg = sc.nextLine();
        reply.println(msg);
        

        //now close all the connections 

        input.close();
        reply.close();
        serverSocket.close();
        socket.close();
        }

        catch (IOException e) {

            System.out.println("Error: " + e);

        }

    }
}