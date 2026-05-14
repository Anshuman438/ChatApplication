import java.io.*;
import java.net.*;
import java.util.Scanner;

public class client{
    public static void main(String[] args) {
        try{
            Scanner sc = new Scanner(System.in);
            //connect to server
            Socket socket = new Socket("localhost", 5000);

            //sending message to server
            PrintWriter output = new PrintWriter(socket.getOutputStream() , true);
            
            String msg = sc.nextLine();
            output.println(msg);
            
            System.out.println("MESSAGE SENT TO CLIENT ");

            socket.close();
            output.close();
        }
        catch(Exception e){
            System.out.println(e);
        }
    }
}