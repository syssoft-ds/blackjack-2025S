import java.util.ArrayList;
import java.util.List;
import java.net.InetSocketAddress;

public class Player {

    private String name;
    private int credit;
    private InetSocketAddress contact;
    private List <Hand> hands;

    private boolean stand = false;
    private boolean lost = false; 
    private boolean surrender = false;
    private int numberOfHands = -1;
    private int totalAmount = 0;


    public Player (String name, int credit, InetSocketAddress contact){

        this.name = name;
        this.credit = credit;
        this.contact = contact;
        hands = new ArrayList<>();
    }

    public String getName(){
        return name;
    }

    public void setCredit(int amount){
        credit -=amount;
    }

    public int getCredit(){
        return credit;
    }

    public InetSocketAddress getContact(){
        return contact;
    }

    public void increaseBet(int amount){
        totalAmount+=amount;
    }

    public int getBet(){
        return totalAmount;
    }


    public void lost(){
        lost = true;
    }

    public boolean hasLost(){
        return lost;
    }

    public boolean isStandingDown(){
    
        // stand is called for each hand, we check here whether stand has been called for a hands 
        // and set stand to true if this is the case

        for(Hand hand: hands)
            if(!hand.isStandingDown()){
                stand = false;
                return false;
            }
        stand = true;
        return true;
    }
    
    public void addHand(Hand hand){
        hands.add(hand);
        numberOfHands++;
        hand.setHandNr(numberOfHands);
    }

    public Hand getHand(int index){
        if (index <= numberOfHands){
            for(Hand hand: hands){
                if(index == 0)
                    return hand;
                index--;
            }                
        }
        return null;
    }

    public List<Hand> getHands(){
        return hands;
    }

    public int getNumberOfHands(){
        return numberOfHands;
    }

    public void surrender(){
        surrender = true;
    }

    public boolean hasSurrendered(){
        return surrender;
    }

    public void payout(double result){
        credit += totalAmount+result; 
        
        if(0 < credit)
            credit = 0;

    }

    public void cleanup(){

        for(Hand hand: hands){
            hand.setPlayer(null);
            hand.clear();
        }
        
        hands = new ArrayList<>();
        totalAmount = 0;
        numberOfHands = 0;
        stand = false;
        lost = false;
        surrender = false;
    }

}
