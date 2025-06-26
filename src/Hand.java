import java.util.ArrayList;
import java.util.List;

import utilities.exceptions.illegalHandException;

public class Hand {

    // This class implements a hand in a card game

    private final List<Card> cards;
    private boolean stand = false;
    private boolean bust = false;
    private Player player = null;
    private int index;
    private int initialBet = 0;
    private int totalAmount = 0;

    public Hand(){
        this.cards = new ArrayList<>();
        index = player.getNumberOfHands()+1;
    }

    public Hand(Player player){
        this.cards = new ArrayList<>();
        this.player = player;
        index = player.getNumberOfHands()+1;
    }

    public Hand(int index){
        this.cards = new ArrayList<>();
        this.index = index;
    }

    public void setPlayer(Player player){
        this.player = player;
    }

    public void addCard(Card card){
        cards.add(card);
    }

    public List<Card> getCards(){
        return cards;
    }

    public Card getCard(int index){
        if(index <= cards.size())
            for(Card card: cards){
                index--;
                if(index == 0)
                    return card;
            }
        return null;
    }


    public void setBust(){
        bust = true;
    }

    public boolean getBust(){
        return bust;
    }

    public void setInitialBet(int amount) throws illegalHandException{
        if(initialBet == 0){
            if(player.getCredit() - amount >= 0){
                initialBet = amount;
                player.setCredit(amount);
                totalAmount = amount;
                player.increaseBet(amount);
            } else 
                throw new illegalHandException("You do not have enough funds to make this bet.");
        }
    }

    public int getInitialBet(){
        return initialBet;
    }

    public int getTotalAmount(){
        return totalAmount;
    }

    public void doubleDown() throws illegalHandException {
        if(player.getCredit() - initialBet >= 0){
            totalAmount += initialBet;
            player.increaseBet(initialBet);
        }
        else
            throw new illegalHandException("Insufficient funds to double down."); 
    }

    public void setHandNr(int index){
        this.index = index;
    }

    public int getHandNr(){
        return index;
    }

    public void stand(){
        stand = true;
    }

    public boolean isStandingDown (){
        return stand;
    }

    public boolean canISplitt(){
        return cards.size() == 2 && cards.get(0).getRank().equals(cards.get(1).getRank());
    }

    public Hand split(Player player) throws illegalHandException{

        if(!canISplitt())
            throw new illegalHandException("Hand is not a pair.");
        
        Hand secondHand = new Hand(player);
        
        // the first hand is never destroyed only shrunk by 1 card
        secondHand.addCard(cards.remove(1));
        
        return secondHand;
    }


    public boolean checkBlackJack(){
        
        boolean ace = false;
        boolean otherCard = false;
        if(cards.size() == 2){
            for(Card card: cards){

                switch(card.getRank()){
                
                    case "A":
                        if(ace == true)
                            return false;

                        ace = true;
                        break;
                
                    case "10", "J", "Q", "K":
                        if(otherCard == true)
                            return false;

                        otherCard = true;
                        break;
            
                    default: return false;
                }
            }
            return true;
        }
        return false;
    }

    public int calculatePoints(boolean croupier){
        int total = 0;
        int aces = 0;

        for(Card card: cards){

            switch(card.getRank()){
                
                case "J", "Q", "K":

                    total += 10;
                    break;
                
                case "A":

                    total += 11;
                    aces++;
                    break;
                
                default: total += Integer.parseInt(card.getRank());
            }
        }

        while(total > 21 && aces > 0 && !croupier){

            total -= 10;
            aces--;
        }

        return total;
    }

    public int size(){
        return cards.size();
    }

    public void clear(){
        cards.clear();
    }

    @Override
    public String toString(){

        if (cards.isEmpty()){
            return "Empty hand";
        }
        
        StringBuilder sb = new StringBuilder();
        
        for (Card card: cards)
            sb.append(card.toString()).append("\n");
        
        return sb.toString();    
    }   
}
