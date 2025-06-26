package dealer;

import common.Message;
import common.Protocoll;
import common.endpoint.InstanceId;
import common.endpoint.UDP_Endpoint;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.javatuples.Pair;

public class Dealer extends UDP_Endpoint
{
    public Pair<InetAddress, Integer> counter;
    public Map<InstanceId, Pair<InetAddress, Integer>> players;
        
    public Game game;
    public GameThread gameThread;

    public Dealer()
    {
        this(1);
    }

    public Dealer(int deckCount) throws IllegalArgumentException
    {   
        super(Protocoll.Header.Role.DEALER);
        this.counter = null;
        this.players = Collections.synchronizedMap(new HashMap<>());
        game = new Game(this);
        game.setDeckCount(deckCount);
        game.shuffle();
        gameThread = new GameThread(this, game);
        gameThread.start();
    } 

    @Override
    public void processMessage(InetAddress sender_addr, int sender_port, Message message)
    {
        switch (message.message_type) 
        {
            case Protocoll.Header.Type.SYN:
                if(message.sender_role == Protocoll.Header.Role.DEALER) break;
                Message synMessage = Message.synMessage(id, Protocoll.Header.Role.DEALER);
                Thread synThread = new Thread();
                switch (message.sender_role)
                {
                    case Protocoll.Header.Role.COUNTER:
                        counter = new Pair<InetAddress,Integer>(sender_addr, sender_port);
                        Message deckCountMessage = Message.deckCountMessage(id, game.deckCount);
                        Message shuffledMessage = Message.shuffledMessage(id, game.draw_stack);
                        synThread = new Thread(() ->
                        {
                            try
                            {
                                send(counter.getValue0(), counter.getValue1(), synMessage);
                                send(counter.getValue0(), counter.getValue1(), deckCountMessage);
                                send(counter.getValue0(), counter.getValue1(), shuffledMessage);
                            }
                            catch (IOException e)
                            {
                                System.out.println(e.getMessage());
                            }
                        });
                        break;

                    case Protocoll.Header.Role.PLAYER:
                        players.put(message.sender_id, new Pair<InetAddress, Integer>(sender_addr, sender_port));
                        synThread = new Thread(() ->
                        {
                            try
                            {
                                send(sender_addr, sender_port, synMessage);
                            }
                            catch (IOException e)
                            {
                                System.out.println(e.getMessage());
                            }
                        });
                        break;

                    default:
                        break;
                }
                synThread.start();
                break;
            
            case Protocoll.Header.Type.FIN:
                if(message.message_type == Protocoll.Header.Role.DEALER) break;
                break;
            
            case Protocoll.Header.Type.BET:
                if(message.sender_role != Protocoll.Header.Role.PLAYER) break;
                if(game.currentPhase != Game.BETTING_PHASE) break;

                game.placeBet(message.sender_id, message.getInt());
                if(game.allBetsPlaced())
                {
                    synchronized(game.lock)
                    {
                        gameThread.ready=true;
                        game.lock.notify();
                    }
                }
                break;
            
                case Protocoll.Header.Type.ACTION:
                    if(message.sender_role != Protocoll.Header.Role.PLAYER) break;
                    if(! message.sender_id.equals(game.currentHand.owner_id)) break;
                    byte action = message.getAction();

                    synchronized(game.lock)
                    {
                        game.currentHand.actionPerformed = true;
                        gameThread.action = action;
                        gameThread.ready = true;
                        game.lock.notify();
                    }
                break;

            default:
                break;
        }
    }

    @Override
    public void processInput(String input)
    {
        if(input.equals("start"))
        {
            game.paused = false;
            synchronized(game.lock) {game.lock.notify();}
        }
        else if(input.equals("pause"))
        {
            game.paused = true;
        }
        else
        {
            printHelp();
        }
    }

    @Override
    protected void printHelp()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("usage:\n");
        builder.append("    start       #type start if the counter and all players are connected\n");
        builder.append("    pause       #type pause if you want to connect more players, active round resumes until its finished\n");
        System.out.println(builder.toString());
    }
    
}
