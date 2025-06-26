package dealer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedDeque;

import common.Card;
import common.Message;
import common.endpoint.InstanceId;
import common.endpoint.MaxRetriesExceededException;

public class Game
{
    public Object lock = new Object();
    public volatile boolean paused;
    public volatile int round_id;

    public static final int WAITING_PHASE = 0;
    public static final int BETTING_PHASE = 1;
    public static final int DRAW_PHASE = 2;
    public static final int PLAYER_PHASE = 3;
    public static final int DEALER_PHASE = 4; 
    public volatile int currentPhase;

    private Dealer dealer;
    public Deque<Hand> active_hands;
    public Set<Hand> finished_hands;
    public Hand currentHand;
    public Hand dealer_hand;
    
    public Stack<Card> draw_stack;
    public Stack<Card> disc_stack;
    public int deckCount;
    public boolean deckCountChanged;

    public int cut;
    
    public Game(Dealer dealer)
    {
        this.dealer = dealer;
        this.paused = true;
        this.round_id = 0;
        this.currentPhase = WAITING_PHASE;
        this.active_hands = new ConcurrentLinkedDeque<>();
        this.finished_hands = Collections.synchronizedSet(new HashSet<>());
        this.draw_stack = new Stack<>();
        this.disc_stack = new Stack<>();
        this.dealer_hand = new Hand(this.round_id, dealer.id);

    }

    public boolean inProgress()
    {
        return currentPhase != WAITING_PHASE;
    }

    public void placeBet(InstanceId owner, int amount)
    {
        for(Hand hand : active_hands)
        {
            if(hand.owner_id.equals(owner))
            {
                hand.wager = amount;
            }
        }
    }

    public boolean allBetsPlaced()
    {
        long missingBets = active_hands.stream().filter(h -> h.wager == 0).count();
        return missingBets == 0;
    }

   public Card drawCard()
    {
        if(cut == 0)
        {
            shuffle();
        }
        cut--;
        return draw_stack.pop();
    }

    public void setDeckCount(int deckCount) throws IllegalArgumentException
    {
        if(1 > deckCount || 8 < deckCount) throw new IllegalArgumentException("deckount must be between 1 and 8");
        this.deckCount = deckCount;
        this.deckCountChanged = true;
    }

    public void shuffle()
    {
        int[] arr;
        
        if(deckCountChanged)
        {
            arr = new int[deckCount * 52];
            for(int j = 0; j < deckCount ; j++)
            {
                for(int i = 0; i < 52; i++)
                {
                    arr[j * 52 + i] = i + 1;
                }
            }
        }
        else
        {
            arr = new int[draw_stack.size() + disc_stack.size()];
            int index = 0;
            while(!draw_stack.empty())
            {
                arr[index++] = draw_stack.pop().value;
            }

            while(!disc_stack.empty())
            {
                arr[index++] = disc_stack.pop().value;
            }
        }

        int maxCut = (int) (0.75 * arr.length);
        int minCut = (int) (0.50 * arr.length);
        cut = (int) (Math.random() * (maxCut - minCut) + minCut);

        //Shuffle
        draw_stack.clear();
        disc_stack.clear();

        for(int i = arr.length-1; i >= 0 ; i--)
        {
            int swap = (int) (Math.random() * i); 
            int temp = arr[i];
            arr[i] = arr[swap];
            arr[swap] = temp;
        }

        for(int i = 0; i < arr.length; i++)
        {
            draw_stack.add(new Card((byte) arr[i]));
        }

        if(dealer.counter != null)
        {            
            InetAddress addr = dealer.counter.getValue0();
            int port = dealer.counter.getValue1();
            Message shuffledMessage = Message.shuffledMessage(dealer.id, draw_stack);
            try
            {
                dealer.send(addr, port, shuffledMessage);
            }
            catch(IOException e)
            {

            }
            catch(MaxRetriesExceededException e)
            {

            }   
        }


    }

}