import java.io.*;
import java.net.*;
import java.util.Scanner;

public class client{
    public static void main(String[] args) {
        try{
            Scanner sc = new Scanner(System.in);
            //connect to server
            Socket socket = new Socket("localhost", 5000);

            System.out.println("server connecterd");
            //sending message to server
                
            PrintWriter output = new PrintWriter(socket.getOutputStream() , true);

            //reading server
            BufferedReader input = new BufferedReader( new InputStreamReader(socket.getInputStream()));
        
        while (true) {
            System.out.print("You : ");
            String msg = sc.nextLine();
            
            // Exit condition
            if (msg.equalsIgnoreCase("exit")) {
                    break;
            }
            output.println(msg);

            String message = input.readLine();
            System.out.print("server : "+ message +"\n");
        }
            socket.close();
            output.close();
            input.close();
        }
        catch(Exception e){
            System.out.println(e);
        }
    }
}