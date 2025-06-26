package utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class UtilityCloset {

    // This class provides utility methods for other classes

    public static boolean freePort(int port){

        // This method checks whether a port is available

        try(DatagramSocket socket = new DatagramSocket(port)){
            socket.close();
            return true;
        } catch (IOException e){
            // port is already in use

            System.out.println(port + " is already in use.");

            return false;
        }
    }

    public static int setPort(){

        // This method is used to set the port for a socket

        int port = -1;
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));

        while (port==-1){

            try{

                System.out.println("\nPlease enter a portnumber for the program to listen at. (valid port numbers: 1023 - 65535)");
                port = Integer.parseInt(input.readLine());

                if(port < 0 || 65535 < port)
                    throw new IOException("\nport is out of range.");
                
                if(!UtilityCloset.freePort(port))
                    throw new IOException("\nport is already in use.");
                break;

            } catch (IOException e){
                System.out.println(e.getMessage());
                port = -1;
            } catch (NumberFormatException e){
                System.out.println("\nPlease input an Integer.");
                port = -1;
            }
        }
        return port;
    }

    public static InetAddress getLocalIP(){

        // This method is used to determine the local Ip Address

        try {
            return InetAddress.getLocalHost();
        
        } catch(UnknownHostException e){
        
            System.out.println("Unable to determine local IP address.");
            return null;
        }
    }

    public static void help(){

        // This method implements the help command

        String clear = "\nclear: clears the current table by replacing it with a new table.\n";
        String contact = "\ncontact: prints the ip and the port the local udp socket is using.\n";
        String exit = "\nexit: exits the program.\n";
        String help = "\nhelp: prints a list of known commands with a short explanation.\n";
        String start = "\nstart: starts the game.\n";

        System.out.println("List of known commands:\n" + clear + contact + exit + help + start);
    }

    public static void greeter(){

        // This method implements a greeting message explaining certain commands to the User
        int numberOfLines = 8;
        String greetingMsg = "";
        String[] Msg = new String[numberOfLines];

        Msg[0] = "\nWelcome!\n";
        Msg[1] = "\nThe croupier acts as a server for this implementation of the game Black Jack. ";
        Msg[2] = "As such the croupier accepts registrations of four players and one kartenzaehler before a game is started. ";
        Msg[3] = "A game may be started by entering <start> in the console. ";
        Msg[4] = "However, to successfully start a game at least one player and one kartenzaehler must be registered. ";
        Msg[5] = "Once a game is finished the program may be closed by entering <exit> in the console.\n";
        Msg[6] = "\nTo see a list of all available commands type <help>. ";
        Msg[7] = "To bypass the lack of a kartenzaehler type <idkfa>.\n";
        
        for(int i = 0; i < numberOfLines; i++)
            greetingMsg +=Msg[i];

        System.out.println(greetingMsg);
    }



}