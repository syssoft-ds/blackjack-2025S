package dealer;

import java.util.LinkedList;
import java.util.List;

import common.Card;
import common.Protocoll;
import common.endpoint.InstanceId;

public class Hand
{   
    public final int round_id;
    public final InstanceId owner_id;

    private static int id_counter = 0;
    public final int hand_id;

    public List<Card> cards;
    public int wager;
    public boolean active;
    public boolean actionPerformed;
    public boolean split;
    public boolean surrenderd;
    
    private Hand(int round_id, InstanceId owner_id, int hand_id, int wager, boolean split, boolean surrenderd)
    {
        this.round_id = round_id;
        this.hand_id = hand_id;
        this.owner_id = owner_id;
        this.cards = new LinkedList<Card>();
        this.wager = wager;
        this.split = split;
        this.surrenderd = surrenderd;
        this.active = true;
        this.actionPerformed = false;
    }


    public Hand(int round_id, InstanceId owner_id)
    {
        this(round_id, owner_id, id_counter++, 0, false, false);
    }

    public Hand(int round_id, InstanceId owner_id, int wager)
    {
        this(round_id, owner_id, id_counter++, wager, false, false);
    }

    public Hand(Hand split)
    {
        this(split.round_id, split.owner_id, id_counter++, split.wager, true, false);
        this.cards.add(split.cards.remove(0));
        split.split = true;
    }

    public void add(Card card)
    {
        cards.add(card);
        if(value() > 21)
        {
            active = false;
        }
    }

    public int size()
    {
        return cards.size();
    }

    public int value()
    {

        int total = 0;
        int aces = 0;

        for (Card card : cards) {
            if (card.getValue() == 1) {
                aces++;
            }
            total += card.getValue() == 1 ? 11 : card.getValue();
        }

        // Adjust for soft aces
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }

        return total;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < cards.size(); i++)
        {
            builder.append(cards.get(i)).append(Protocoll.SEPERATOR);
        }
        builder.append(this.value());

        return builder.toString();
    }

    byte[] cardsToBytes()
    {        
        byte[] arr = new byte[cards.size()];
        
        for(int i = 0; i < cards.size(); i++)
        {
            arr[i] = cards.get(i).value;
        }

        return arr;
    }

}
