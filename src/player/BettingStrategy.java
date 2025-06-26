package player;

public abstract class BettingStrategy
{
    Player player;

    public BettingStrategy(Player player)
    {
        this.player = player;
    }

    public abstract int getWager();
}
