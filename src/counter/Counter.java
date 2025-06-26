package counter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import org.javatuples.Pair;

import common.Card;
import common.Context;
import common.endpoint.InstanceId;
import common.endpoint.MaxRetriesExceededException;
import common.endpoint.UDP_Endpoint;
import common.Message;
import common.MessageIdentifier;
import common.Protocoll;
import common.StatisticsEntry;

public class Counter extends UDP_Endpoint
{

    public Pair<InetAddress, Integer> dealer;
    public Map<InstanceId, Pair<InetAddress, Integer>> players;

    public Card upcard;
    public int deckCount;
    public Map<Integer, Integer> card_map;

    public List<StatisticsEntry> statistics;

    public Counter()
    {
        super(Protocoll.Header.Role.COUNTER);
        players = Collections.synchronizedMap(new HashMap<>());
        card_map = new HashMap<>();
        statistics = new LinkedList<>();
    }

    @Override
    public void processMessage(InetAddress sender_addr, int sender_port, Message message)
    {
        switch (message.message_type)
        {
            case Protocoll.Header.Type.SYN:
                if(message.sender_role == Protocoll.Header.Role.COUNTER) break;
                switch (message.sender_role)
                {
                    case Protocoll.Header.Role.DEALER:
                        dealer = new Pair<InetAddress,Integer>(sender_addr, sender_port);
                        break;
                    case Protocoll.Header.Role.PLAYER:
                        players.put(message.sender_id, new Pair<InetAddress, Integer>(sender_addr, sender_port));
                        Message synMessage = Message.synMessage(id, Protocoll.Header.Role.COUNTER);
                        Thread synThread = new Thread(() ->
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
                        synThread.start();
                        break;
                    default:
                        break;
                }
                break;

            case Protocoll.Header.Type.FIN:
                if(message.message_type == Protocoll.Header.Role.DEALER) break;
                break;

            case Protocoll.Header.Type.DECKCOUNT:
                if(message.sender_role != Protocoll.Header.Role.DEALER) break;
                deckCount = message.getInt();                
                break;

            case Protocoll.Header.Type.SHUFFLED:
                if(message.sender_role != Protocoll.Header.Role.DEALER) break;
                
                card_map.clear();
                for(Card card : message.getCards())
                {
                    card_map.putIfAbsent(card.getRank(), 0);
                    card_map.put(card.getRank(), card_map.get(card.getRank()) + 1);
                }
                break;

            case Protocoll.Header.Type.UPCARD:
                if(message.sender_role != Protocoll.Header.Role.DEALER) break;
                upcard = message.getCard();
                card_map.put(upcard.getValue(), card_map.get(upcard.getValue()) - 1);
                break;

            case Protocoll.Header.Type.CARDS:
                if(message.sender_role == Protocoll.Header.Role.COUNTER) break;
                if(message.sender_role == Protocoll.Header.Role.DEALER)
                {
                    for(Card card : message.getCards())
                    {
                        card_map.put(card.getRank(), card_map.get(card.getRank()) - 1);
                    }
                }
                break;

            case Protocoll.Header.Type.ACTION_REQUEST:
                if(message.sender_role != Protocoll.Header.Role.PLAYER) break;
                byte bestAction = bestAction(upcard, message.getCards());
                Message actionMessage = Message.actionMessage(id, Protocoll.Header.Role.COUNTER, bestAction);
                Thread actionThread = new Thread( () -> 
                {
                    try
                    {
                        send(sender_addr, sender_port, actionMessage);
                    }
                    catch(IOException e)
                    {
                        System.out.println(e.getMessage());    
                    }
                });
                actionThread.start();
                break;
            
            case Protocoll.Header.Type.STATISTICS:
                if(message.sender_role == Protocoll.Header.Role.DEALER)
                {
                    statistics.add(message.getEntry());
                }
                else if(message.sender_role == Protocoll.Header.Role.PLAYER)
                {
                    List<StatisticsEntry> playerEntries = statistics.stream().filter(e -> e.player_id().equals(message.sender_id)).collect(Collectors.toList());
                    Thread statisticsTread = new Thread( () ->
                    {
                        for(StatisticsEntry entry : playerEntries)
                        {
                            Message statisticsMessage = Message.statisticsMessage(id, role, entry);
                            try
                            {
                                send(sender_addr, sender_port, statisticsMessage);
                            }
                            catch(IOException | MaxRetriesExceededException e)
                            {
                                System.out.println(e.getMessage());
                            }
                        }
                    });
                    statisticsTread.start();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void processInput(String input)
    {
        try
        {
            if(input.startsWith("register"))
            {
                String[] command = input.split(" ", 3);
                if(command.length != 3)
                {
                    printHelp();
                }
                
                InetAddress addr = InetAddress.getByName(command[1]);
                int port = Integer.valueOf(command[2]);

                Message synMessage = Message.synMessage(id, Protocoll.Header.Role.COUNTER);
                MessageIdentifier messageIdentifier = synMessage.getIdentifier();
                send(addr, port, synMessage);
                synchronized(acknowledged_Messages)
                {
                    if(acknowledged_Messages.contains(messageIdentifier))
                    {
                        acknowledged_Messages.remove(messageIdentifier);
                    }
                }
            }
            else if(input.equals("save"))
            {
                writeToCSV();
            }
            else
            {
                printHelp();
            }
        }
        catch(IOException e)
        {
            System.out.println(e.getMessage());
        }
    }

    @Override
    protected void printHelp()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("usage:\n");
        builder.append("    register <dealer_ip> <dealer_port>      #connect to dealer\n");
        builder.append("    save                                    #save current game history to a csv file\n");
        System.out.println(builder.toString());
    } 

    private byte bestAction(Card upCard, List<Card> hand)
    {   
        DecisionMaxHeap heap = new DecisionMaxHeap();
        List<Integer> values = new LinkedList<>();
        for(Card card : hand)
        {
            values.add(card.getValue());
        }
        
        OutcomeDistribution od = gerDealersOD(upCard, card_map);
        
        if(hand.size() == 2)
        {   
            heap.insert(new Decision(splitEV(hand.get(0), hand.get(1), upCard), Context.Game.Actions.SPLIT));
            heap.insert(new Decision(doubleDownEV(values, isSoft(values), card_map, od), Context.Game.Actions.DOUBLE_DOWN));
            heap.insert(new Decision(surrenderEV(), Context.Game.Actions.SURRENDER));
        }

        heap.insert(new Decision(hitEV(values, isSoft(values), card_map, od), Context.Game.Actions.HIT));
        heap.insert(new Decision(standEV(handTotal(values), isSoft(values), od), Context.Game.Actions.STAND));

        return heap.peekMax().action();
    }

    private static OutcomeDistribution gerDealersOD(Card upcard, Map<Integer, Integer> deck)
    {
        return simulateDealerHand(upcard.getValue(), upcard.getValue() == 1 ? 1 : 0, deck);
    }

    private static OutcomeDistribution simulateDealerHand(int total, int softAces, Map<Integer, Integer> deck)
    {
        if (total >= 17 && total <= 21) {
            // Dealer stands
            return switch (total) {
                case 17 -> new OutcomeDistribution(1, 0, 0, 0, 0, 0);
                case 18 -> new OutcomeDistribution(0, 1, 0, 0, 0, 0);
                case 19 -> new OutcomeDistribution(0, 0, 1, 0, 0, 0);
                case 20 -> new OutcomeDistribution(0, 0, 0, 1, 0, 0);
                case 21 -> new OutcomeDistribution(0, 0, 0, 0, 1, 0);
                default -> throw new IllegalStateException("Unexpected total: " + total);
            };
        }

        if (total > 21 && softAces > 0) {
            // Convert soft ace to hard ace
            return simulateDealerHand(total - 10, softAces - 1, deck);
        }

        if (total > 21) {
            // Dealer busts
            return new OutcomeDistribution(0, 0, 0, 0, 0, 1);
        }

        // Dealer must hit
        double totalCards = totalSum(deck);
        OutcomeDistribution result = OutcomeDistribution.zero();

        for(int i= 1 ; i < 11 ; i++)
        {
            int count = deck.get(i);
            if (count == 0) continue;

            double prob = count / totalCards;
            deck.put(i, deck.get(i) - 1);

            int cardValue = i == 1 ? 11 : i;
            boolean isAce = i == 1;

            int newTotal = total + cardValue;
            int newSoftAces = softAces + (isAce ? 1 : 0);

            OutcomeDistribution subResult = simulateDealerHand(newTotal, newSoftAces, deck);
            result = result.add(subResult, prob);

            deck.put(i, deck.get(i) + 1);
        }

        return result;
    }

    private static int totalSum(Map<Integer, Integer> cards)
    {
        return cards.values().stream().mapToInt(i -> i).sum();
    }

    private static boolean isSoft(List<Integer> hand) {
        int total = 0;
        int aces = 0;

        for (int val : hand) {
            if (val == 1) aces++;
            total += val == 1 ? 11 : 0;
        }

        return aces > 0 && total <= 21;
    }
    
    private static int handTotal(List<Integer> hand)
    {
        int total = 0;
        int aces = 0;

        for (int val : hand) {
            if (val == 1) {
                aces++;
            }
            total += val == 1 ? 11 : val;
        }

        // Adjust for soft aces
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }

        return total;
    }

    private static double splitEV(Card card1, Card card2, Card dealerUpcard)
    {
        if (card1.getRank() != card2.getRank()) return -Double.MAX_VALUE; // Only identical ranks can be split

        int rank = card1.getRank();
        int upcard = dealerUpcard.getRank();
        boolean split = false;

        switch (rank) {
            case 1, 8 -> split = true; // Always split
            case 10 -> split = false; // Never split tens
            case 9 -> split = upcard != 7 && upcard != 10 && upcard != 1; // Split vs 2–6, 8–9
            case 7 -> split = upcard <= 7; // Split vs 2–7
            case 6 -> split = upcard <= 6; // Split vs 2–6
            case 5 -> split = false; // Treat as 10 — never split
            case 4 -> split = upcard == 5 || upcard == 6; // Split vs 5–6
            case 2, 3 -> split = upcard <= 7; // Split vs 2–7
            default -> split = false;
        }
        return split ? Double.MAX_VALUE : -Double.MAX_VALUE;
    }

    public static double bestEV(List<Integer> hand, boolean soft, Map<Integer, Integer> deck, OutcomeDistribution dealerDist)
    {
        int total = handTotal(hand);

        if (total > 21) return -1.0;

        double standEV = standEV(total, soft, dealerDist);
        double hitEV = hitEV(hand, soft, deck, dealerDist);
        double doubleEV = doubleDownEV(hand, soft, deck, dealerDist);
        double surrenderEV = surrenderEV(); // Typically just -0.5

        return Math.max(Math.max(standEV, hitEV), Math.max(doubleEV, surrenderEV));
    }

    private static double hitEV(List<Integer> playerHand, boolean soft, Map<Integer, Integer> deck, OutcomeDistribution dealerDist)
    {
        if (handTotal(playerHand) > 21) return -1.0;

        double ev = 0;
        int totalCards = totalSum(deck);

        for (int i = 1; i < 11; i++)
        {
            int count = deck.get(i);
            if (count == 0) continue;

            deck.put(i, deck.get(i) - 1);
            List<Integer> newHand = new ArrayList<>(playerHand);
            newHand.add(i == 1 ? 11 : i);
            boolean newSoft = soft || i == 1;

            double prob = (double) count / totalCards;
            double val = bestEV(newHand, newSoft, deck, dealerDist);
            ev += prob * val;
            deck.put(i,deck.get(i) + 1);
        }

        return ev;
    }

    private static double doubleDownEV(List<Integer> hand, boolean soft, Map<Integer, Integer> deck, OutcomeDistribution dealerDist)
    {
        double ev = 0;
        int totalCards = totalSum(deck);

        for (int i = 1; i < 11; i ++)
        {
            int count = deck.get(i);
            if (count == 0) continue;

            deck.put(i, deck.get(i) -1);
            List<Integer> newHand = new ArrayList<>(hand);
            newHand.add(i == 1 ? 11 : i);
            boolean newSoft = soft || i == 1;

            int total = handTotal(newHand);
            double result = standEV(total, newSoft, dealerDist);
            ev += ((double) count / totalCards) * result;
            deck.put(i, deck.get(i) + 1);
        }

        return ev * 2; // double bet
    }

    private static double standEV(int playerTotal, boolean soft, OutcomeDistribution dealerDist)
    {
        if (playerTotal > 21) return -1.0; // player busts
        double win = 0, loss = 0, push = 0;

        win += dealerDist.pBust();
        if (playerTotal > 17) win += switch (playerTotal) {
            case 18 -> dealerDist.p17();
            case 19 -> dealerDist.p17() + dealerDist.p18();
            case 20 -> dealerDist.p17() + dealerDist.p18() + dealerDist.p19();
            case 21 -> dealerDist.p17() + dealerDist.p18() + dealerDist.p19() + dealerDist.p20();
            default -> 0;
        };

        push += switch (playerTotal) {
            case 17 -> dealerDist.p17();
            case 18 -> dealerDist.p18();
            case 19 -> dealerDist.p19();
            case 20 -> dealerDist.p20();
            case 21 -> dealerDist.p21();
            default -> 0;
        };

        loss = 1.0 - win - push;
        return win * 1.0 + push * 0.0 + loss * -1.0;
}

    private static double surrenderEV()
    {
        return - 0.5;
    }

    private void writeToCSV()
    {
        String path = System.getProperty("user.dir") + "/" + Context.Log.game_staticstics;
        String sep = Protocoll.SEPERATOR;
        File statisticsFile = new File(path);

        String[] header = {"round_id","role", "instance_id","hand_id", "win","blackjack","split","wager","winnings","deckcount","hand"};
        try (FileWriter writer = new FileWriter(statisticsFile, false))
        {
            writer.write(String.join(sep,header) + "\n");

            for (StatisticsEntry entry : statistics)
            {
                String[] strings = new String[11];
                strings[0] = Integer.toString(entry.round_id());
                strings[1] = Integer.toString(entry.role());
                strings[2] = entry.player_id().toString();
                strings[3] = Integer.toString(entry.hand_id());
                strings[4] = entry.win() ? "1" : "0";
                strings[5] = entry.blackJack() ? "1" : "0";
                strings[6] = entry.split() ? "1" : "0";
                strings[7] = Integer.toString(entry.wager());
                strings[8] = Integer.toString(entry.winnings());
                strings[9] = Integer.toString(entry.deckcount());
                strings[10] = entry.hand().stream().map(String::valueOf).collect(Collectors.joining(";"));
                writer.write(String.join(sep, strings) + "\n");
            }
        }
        catch(IOException e)
        {
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args)
    {
        Counter counter = new Counter();

        int deckCount = 7;
        Stack<Card> drawStack = new Stack<>();
        
        int[] arr;
        
        arr = new int[deckCount * 52];

        for(int j = 0; j < deckCount ; j++)
        {
            for(int i = 0; i < 52; i++)
            {
                    arr[j * 52 + i] = i + 1;
            }
        }

        for(int i = arr.length-1; i >= 0 ; i--)
        {
            int swap = (int) (Math.random() * i); 
            int temp = arr[i];
            arr[i] = arr[swap];
            arr[swap] = temp;
        }

        for(int i = 0; i < arr.length; i++)
        {
            drawStack.add(new Card((byte) arr[i]));
        }

        Map<Integer, Integer> deck = new HashMap<>();
        for(int i = 1 ; i < 14 ; i++)
        {
            deck.put(i > 10 ? 10 : i, i > 10 ? 10 : i);
        }

        Card upcard = drawStack.pop();
        Card card1 = drawStack.pop();
        Card card2 = drawStack.pop();
        
        deck.put(upcard.getValue(),deck.get(upcard.getValue()) - 1);
        deck.put(card1.getValue(),deck.get(card1.getValue()) - 1);
        deck.put(card2.getValue(),deck.get(card2.getValue()) - 1);
        
        counter.card_map = deck;

        System.out.println("upcard is:" + upcard);
        System.out.println("Hand is:" + card1 + "," +card2);
        
        byte action = counter.bestAction(upcard, List.of(card1, card2));

        switch(action)
        {
            case Context.Game.Actions.SPLIT:
                System.out.println("best action is split");
                break;
            case Context.Game.Actions.HIT:
                System.out.println("best action is hit");
                break;
            case Context.Game.Actions.DOUBLE_DOWN:
                System.out.println("best action is Double down");
                break;
            case Context.Game.Actions.STAND:
                System.out.println("best action is Stand");
                break;
            case Context.Game.Actions.SURRENDER:
                System.out.println("best action is Surrender");
                break;
        }


    }
}