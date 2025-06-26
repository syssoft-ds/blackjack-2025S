import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import utilities.exceptions.RegisterException;

public class Table {

    // This class implements a blackjack table

    private final List <Seat> seats;
    private final int numberOfSeats;
    private int playerCnt;
    
    // players and kartenzaehler
    private InetSocketAddress kartenzaehler;
    
    private static HashMap <String, Seat> nameLookup = new HashMap <>();

    // put bets here

    public Table(int numberOfSeats){
        this.seats = new ArrayList<>();
        this.numberOfSeats = numberOfSeats;
        for(int i = 0; i < numberOfSeats; i++)
            seats.add(new Seat(this, i));

        playerCnt = 0;
    }

    public int getNumberOfSeats(){
        return numberOfSeats;
    }

    public List<Seat> getSeats(){
        return seats;
    }

    public Seat getSeat(int position){
        return seats.get(position);
    }

    //public List<Seat> getOccupiedSeats(){
    //    return seats.stream().filter(Seat::isOccupied).toList();
    //}


    public void addPlayer(String name, int credit, InetSocketAddress contact)throws RegisterException{
        
        Player newPlayer = new Player(name, credit, contact);
        
        for(Seat seat: seats){
            
            if(!seat.isOccupied()){
            
                seat.assignPlayer(newPlayer);
                playerCnt++;
                nameLookup.put(name, seat);
                break;
            }
            throw new RegisterException("Table is already full.");
        }
    }

    public int getPlayerCnt(){
        return playerCnt;
    }

    public void setKartenzaehler(InetSocketAddress contact){
        kartenzaehler=contact;
    }

    public InetSocketAddress getKartenzaehler(){
        return kartenzaehler;
    }

    public HashMap<String, Seat> getNameLookup(){

        // Lookup should occure here, at the table not somewhere else
        return nameLookup;
    }

}
