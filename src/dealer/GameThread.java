package dealer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import common.Card;
import common.Message;
import common.Protocoll;
import common.StatisticsEntry;
import common.endpoint.InstanceId;
import common.endpoint.MaxRetriesExceededException;
import common.Context;

public class GameThread extends Thread
{
    public volatile boolean ready = false;
    public volatile byte action;
    public Dealer dealer;
    public Game game;

    public GameThread(Dealer dealer, Game game)
    {
        this.dealer = dealer;
        this.game = game;
    }

    @Override
    public void run()
    {
        while(true)
        {
            try
            {
                if(game.paused)
                {
                    synchronized(game.lock)
                    {
                        game.lock.wait();
                    }   
                }
                game.round_id++;
                bettinPhase();
                synchronized(game.lock)
                {
                    while(!ready)
                    {
                        game.lock.wait(Context.Game.BETTING_PHASE_DURATION);
                    }
                    ready = false;
                }

                synchronized(game.active_hands)
                {
                    for(Hand hand : game.active_hands)
                    {
                        if(hand.wager == 0)
                        {
                            game.active_hands.remove(hand);
                            synchronized(dealer.players)
                            {
                                dealer.players.remove(hand.owner_id);
                            }
                        }
                    }
                }
                drawPhase();
                playerPhase();
                dealerPhase();
                resetPhase();
            }
            catch(InterruptedException e)
            {
                System.out.println(e.getMessage());
            }
        }
    }

    public void bettinPhase()
    {
        if(game.currentPhase != Game.WAITING_PHASE) throw new GameException("WAITING_PHASE must preceed BETTING_PHASE");
        game.currentPhase = Game.BETTING_PHASE;
        
        synchronized(dealer.players)
        {
            for(InstanceId id : dealer.players.keySet())
            {
                Hand hand = new Hand(game.round_id, id);
                game.active_hands.add(hand);
                InetAddress addr = dealer.players.get(id).getValue0();
                int port = dealer.players.get(id).getValue1();
                Message bettingRequest = Message.betMessage(id, Protocoll.Header.Role.DEALER, 0);
                try
                {
                    dealer.send(addr, port, bettingRequest);
                }
                catch(IOException e)
                {
                    System.out.println(e.getMessage());
                }
                catch(MaxRetriesExceededException e)
                {
                    dealer.players.remove(id);
                    game.active_hands.remove(hand);
                }
            }
        }
    }

    public void drawPhase()
    {
        if(game.currentPhase != Game.BETTING_PHASE) throw new GameException("BETTING_PHASE must preceed DRAW_PHASE");
        game.currentPhase = Game.DRAW_PHASE;

        InetAddress counter_addr;
        int counter_port;
        synchronized(dealer.counter)
        {
            counter_addr = dealer.counter.getValue0();
            counter_port = dealer.counter.getValue1();
        }

        for(Hand hand : game.active_hands)
        {
            List<Card> cards = new LinkedList<>();
            cards.add(game.drawCard());
            cards.add(game.drawCard());
            Message message = Message.cardsMessage(dealer.id, dealer.role, cards);
            
            try
            {
                dealer.send(counter_addr, counter_port, message);
                hand.cards.addAll(cards);
            }
            catch(IOException e)
            {
                System.out.println(e.getMessage());
            }
            catch(MaxRetriesExceededException e)
            {
                game.draw_stack.addAll(cards);
                game.active_hands.remove(hand);
                dealer.players.remove(hand.owner_id);
            }
        }
        Card upcard = game.drawCard();
        Message upcardMessage = Message.upcardMessage(dealer.id, upcard);
        try
        {
            dealer.send(counter_addr, counter_port, upcardMessage);
            game.dealer_hand = new Hand(game.round_id, dealer.id);
            game.dealer_hand.add(upcard);
        }
        catch(IOException e)
        {
            System.out.println(e.getMessage());
        }

    }

    public void playerPhase()
    {
        if(game.currentPhase != Game.DRAW_PHASE) throw new GameException("DRAW_PHASE must preceed PLAYER_PHASE");
        game.currentPhase = Game.PLAYER_PHASE;
        
        while(!game.active_hands.isEmpty())
        {
            game.currentHand = game.active_hands.removeFirst();                
            InetAddress addr;
            int port;
            synchronized(dealer.players)
            {
                addr = dealer.players.get(game.currentHand.owner_id).getValue0();
                port = dealer.players.get(game.currentHand.owner_id).getValue1();
            }
            while(game.currentHand.active)
            {
                Message actionRequest = Message.actionRequest(dealer.id, Protocoll.Header.Role.DEALER, game.currentHand.cards);
                try
                {
                    dealer.send(addr, port, actionRequest);

                    while(!ready)
                    {
                        synchronized(game.lock){game.lock.wait(Context.Game.PLAYER_MOVE_DURATION);}
                    }
                    ready = false;

                    if(! game.currentHand.actionPerformed)
                    {
                        synchronized(dealer.players)
                        {
                            dealer.players.remove(game.currentHand.owner_id);
                            game.disc_stack.addAll(game.currentHand.cards);
                        }
                    }
                    else
                    {
                        processAction(action);
                    }
                }
                catch(IOException | InterruptedException | MaxRetriesExceededException e)
                {
                    System.out.println(e.getMessage());
                }
            }
        }
    }

    public void processAction(byte action)
    {
        InetAddress addr;
        int port;
        Message player_Message;
        Message counter_Message;

        synchronized(dealer.players)
        {
            addr = dealer.players.get(game.currentHand.owner_id).getValue0();
            port = dealer.players.get(game.currentHand.owner_id).getValue1();
        }


        switch(action)
        {
            case Context.Game.Actions.STAND:
                synchronized(game.currentHand)
                {
                    game.currentHand.active = false;
                    game.finished_hands.add(game.currentHand);
                }
                break;
            case Context.Game.Actions.HIT:
                synchronized(game.currentHand)
                {
                    Card card = game.drawCard();
                    counter_Message = Message.cardsMessage(dealer.id, Protocoll.Header.Role.DEALER, List.of(card));
                    game.currentHand.add(card);
                    try
                    {
                        dealer.send(dealer.counter.getValue0(),dealer.counter.getValue1(), counter_Message);
                    }
                    catch(IOException e)
                    {
                        System.out.println(e.getMessage());
                    } 
                    if(! game.currentHand.active)
                    {
                        game.finished_hands.add(game.currentHand);
                        player_Message = Message.cardsMessage(dealer.id, dealer.role, game.currentHand.cards);
                        try
                        {
                            dealer.send(addr, port, player_Message);
                        }
                        catch(IOException e)
                        {
                            System.out.println(e.getMessage());
                        } 
                    }
                }
                break;

            case Context.Game.Actions.DOUBLE_DOWN:
                synchronized(game.currentHand)
                {
                    game.currentHand.active = false;
                    game.currentHand.wager = game.currentHand.wager * 2;
                    Card card = game.drawCard();
                    counter_Message = Message.cardsMessage(dealer.id, Protocoll.Header.Role.DEALER, List.of(card));
                    try
                    {
                        dealer.send(dealer.counter.getValue0(),dealer.counter.getValue1(), counter_Message);
                    }
                    catch(IOException e)
                    {
                        System.out.println(e.getMessage());
                    } 
                    game.currentHand.add(card);
                    game.finished_hands.add(game.currentHand);
                    player_Message = Message.cardsMessage(dealer.id, Protocoll.Header.Role.DEALER, game.currentHand.cards);
                    try
                    {
                        dealer.send(addr, port, player_Message);
                    }
                    catch(IOException e)
                    {
                        System.out.println(e.getMessage());
                    } 
                }
                break;

            case Context.Game.Actions.SPLIT:
                synchronized(game.currentHand)
                {
                    Hand split = new Hand(game.currentHand);
                    game.active_hands.addFirst(split);
                    Card card1 = game.drawCard();
                    Card card2 = game.drawCard();

                    counter_Message = Message.cardsMessage(dealer.id, dealer.role, List.of(card1, card2));
                    try
                    {
                        dealer.send(dealer.counter.getValue0(),dealer.counter.getValue1(), counter_Message);
                    }
                    catch(IOException e)
                    {
                        System.out.println(e.getMessage());
                    } 
                    game.currentHand.add(card1);
                    split.add(card2);
                }
                break;
            case Context.Game.Actions.SURRENDER:
                synchronized(game.currentHand)
                {
                    game.currentHand.active = false;
                    game.currentHand.surrenderd = true;
                    game.finished_hands.add(game.currentHand);
                }
                break;
        }
    }

    public void dealerPhase()
    {
        if(game.currentPhase != Game.PLAYER_PHASE) throw new GameException("PLAYER_PHASE must preceed DEALER_PHASE");
        game.currentPhase = Game.DEALER_PHASE;

        InetAddress addr = dealer.counter.getValue0();
        int port = dealer.counter.getValue1();

        while(game.dealer_hand.value() < 17)
        {
            Card card = game.drawCard();
            Message message = Message.cardsMessage(dealer.id, dealer.role, List.of(card));
            try
            {
                dealer.send(addr, port, message);
            }
            catch(IOException | MaxRetriesExceededException e)
            {
                System.out.println(e.getMessage());
            }
            game.dealer_hand.cards.add(card);
        }
    }

    public void resetPhase()
    {
        if(game.currentPhase != Game.DEALER_PHASE) throw new GameException("DEALER_PHASE must preceed WAITING_PHASE");
        game.currentPhase = Game.WAITING_PHASE;
        int dealer_value = game.dealer_hand.value();
        boolean dealer_bust = dealer_value > 21;
        boolean dealer_blackJack = dealer_value == 21 & game.dealer_hand.cards.size() == 2;

        List<StatisticsEntry> statisticsEntries = new LinkedList<>();

        StatisticsEntry dealerEnry = new StatisticsEntry(game.round_id, dealer.role, dealer.id, game.dealer_hand.hand_id, !dealer_bust, dealer_blackJack, false, 0, 0, game.deckCount, game.dealer_hand.cards);
        statisticsEntries.add(dealerEnry);
        game.disc_stack.addAll(game.dealer_hand.cards);

        for(Hand hand : game.finished_hands)
        {
            game.disc_stack.addAll(hand.cards);
            boolean win = true;

            if(hand.value() > 21) win = false;
            if(hand.value() <= dealer_value && !dealer_bust) win = false;
            if(hand.surrenderd) win =false;
            
            boolean blackJack = hand.cards.size() == 2 && ! hand.split && hand.value() == 21;
            
            int winnings = 0;

            if(win && blackJack) winnings = (int) (2.5 * hand.wager);
            else if(win) winnings = 2 * hand.wager;
            else if(hand.surrenderd) winnings = (int) (0.5 * hand.wager);
            
            InetAddress addr;
            int port;
            
            synchronized(dealer.players)
            {
                addr = dealer.players.get(hand.owner_id).getValue0();
                port = dealer.players.get(hand.owner_id).getValue1();
            }
            
            Message winnigsMessage = Message.winnigsMessage(dealer.id, winnings);
            try
            {              
                dealer.send(addr,port, winnigsMessage);
            }
            catch(IOException | MaxRetriesExceededException e)
            {
                System.out.println(e.getMessage());
            }

            StatisticsEntry player_entry = new StatisticsEntry(game.round_id, Protocoll.Header.Role.PLAYER, hand.owner_id, hand.hand_id, win, blackJack, hand.split, hand.wager, winnings, game.deckCount, hand.cards );
            statisticsEntries.add(player_entry);

            synchronized(dealer.counter)
            {
                addr = dealer.counter.getValue0();
                port = dealer.counter.getValue1();
            }
        }
            
        InetAddress addr;
        int port;

        synchronized(dealer.counter)
        {
            addr = dealer.counter.getValue0();
            port = dealer.counter.getValue1();
        }        
        
        for(StatisticsEntry entry : statisticsEntries)
        {
            Message statisticMessage = Message.statisticsMessage(dealer.id, dealer.role, entry);                
            try
            {
                dealer.send(addr, port, statisticMessage);
            }
            catch(IOException | MaxRetriesExceededException e)
            {
                System.out.println(e.getMessage());
            }
        }
        game.finished_hands.clear();
    }
}