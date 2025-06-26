package player;

import common.Context;

public class FlatBettingStrategy extends BettingStrategy
{

    public FlatBettingStrategy(Player player)
    {
        super(player);
    }

    @Override
    public int getWager()
    {
        return Context.Game.WAGER;
    }
}
