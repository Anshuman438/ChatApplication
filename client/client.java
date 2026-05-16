import java.io.*;
import java.net.*;
import java.util.Scanner;

public class client{
    public static void main(String[] args) {
        try{
            Scanner sc = new Scanner(System.in);
            //connect to server
            Socket socket = new Socket("localhost", 5000);

            System.out.println("sent the text to server");
            //sending message to server
            PrintWriter output = new PrintWriter(socket.getOutputStream() , true);
            
            String msg = sc.nextLine();
            output.println(msg);
            
            System.out.println("MESSAGE SENT TO Server ");

            //reading server
            BufferedReader input = new BufferedReader( new InputStreamReader(socket.getInputStream()));
        
            String message = input.readLine();
            System.out.println("the client said : "+ message);

            socket.close();
            output.close();
            input.close();
        }
        catch(Exception e){
            System.out.println(e);
        }
    }
}