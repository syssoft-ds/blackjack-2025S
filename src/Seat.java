public class Seat {
 
    // This class implements a seat at a blackjack table

    private final int position;
    private Player player;
    private Table table;

    public Seat(Table table, int position){
        this.position = position;
        this.table = table;
        this.player = null;
    }
    
    public boolean isOccupied(){
        return player != null;
    }

    public void assignPlayer(Player player){
        this.player = player;
    }

    public void clearSeat(){
        this.player = null;
    }

    public int getPosition(){
        return position;
    }    

    public Player getPlayer(){
        return player;
    }

    public Table getTable(){
        return table;
    }
}
