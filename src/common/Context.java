package common;

public final class Context
{

    private Context(){}

    public static final class Network
    {
        public static final int BUFFER_LENGTH = 4096;
        public static final int NUMBER_OF_LAST_MESSAGES = 1000;
        public static final int TRIES_LINEAR = 5;
        public static final int TRIES_NON_LINEAR = 5;
        public static final int TIMEOUT_MS = 1000;
    }

    public static final class Log
    {
        public static final String dealer_log = "logs/dealer.log";
        public static final String player_log = "logs/player.log";
        public static final String counter_log = "logs/counter.log";
        public static final String game_staticstics = "statistics.csv";
    }

    public static final class Game
    {
        public static final int MAX_PLAYERS = 7;
        public static final int BETTING_PHASE_DURATION = 30000;
        public static final int PLAYER_MOVE_DURATION = 20000;
        public static final int START_BANKROLL = 4000;
        public static final int WAGER = 40;
        
        public static final class Actions
        {
            public static final byte HIT = 0x01;
            public static final byte STAND = 0x02;
            public static final byte SPLIT = 0x03;
            public static final byte DOUBLE_DOWN = 0x04;
            public static final byte SURRENDER = 0x05;

            public static String toString(byte action)
            {
                StringBuilder builder = new StringBuilder();
                switch (action) {
                    case HIT:
                        builder.append("hit");
                        break;
                    case STAND:
                        builder.append("stand");
                        break;
                    case DOUBLE_DOWN:
                        builder.append("double down");
                        break;
                    case SPLIT:
                        builder.append("split");
                        break;
                    case SURRENDER:
                        builder.append("surrender");
                        break;
                }
                return builder.toString();
            }
        }
    }

    

}