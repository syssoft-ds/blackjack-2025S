import java.lang.reflect.InvocationTargetException;

import counter.Counter;
import dealer.Dealer;
import player.FlatBettingStrategy;
import player.Player;

public class Main
{

   
    private static void printHelp()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("usage: Main --role=[option] where option equals\n");
        builder.append("    dealer \n");
        builder.append("    player --strategy=[option] where option equals\n");
        builder.append("        flat\n");
        builder.append("    counter\n");
        System.out.println(builder.toString());
    }

    public static void main(String[] args)
    {
        if(args.length == 1)
        {
            if(args[0].equals("--role=dealer"))
            {
                Dealer dealer = new Dealer();
                dealer.run();            
            }

            else if(args[0].equals("--role=counter"))
            {
                Counter counter = new Counter();
                counter.run();
            }
            else
            {
                printHelp();
            }
        }
        else if (args.length == 2)
        {
            if(! args[0].equals("--role=player"))
            {
                printHelp();
                return;
            }
            if(args[1].equals("--strategy=flat"))
            {
                try
                {
                    Player player = new Player(FlatBettingStrategy.class);
                    player.run();
                }
                catch(IllegalAccessError | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
                {
                    System.out.println(e.getMessage());
                    printHelp();
                }
            }
            else
            {
                printHelp();
            }    
        }
        else
        {
            printHelp();
        }
    }
}
