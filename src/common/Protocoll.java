package common;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


public final class Protocoll {
    
    private Protocoll(){}

    public static final Charset CHARSET = StandardCharsets.UTF_8;
    public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    public static final String SEPERATOR = ",";

    public static final class Header
    {
        public static final byte LENGTH = 24;  

        public static final class Type
        {
            public static final byte ACK = 0x01;
            public static final byte SYN = 0x02;
            public static final byte FIN = 0x03;
            public static final byte BET = 0x04;
            public static final byte ACTION_REQUEST = 0x05;
            public static final byte ACTION = 0x06;
            public static final byte WINNINGS = 0x07;
            public static final byte DECKCOUNT = 0x08;
            public static final byte SHUFFLED = 0x09;
            public static final byte CARDS = 0x0A;
            public static final byte UPCARD = 0x0B;
            public static final byte STATISTICS = 0x0C;
        }

        public static final class Role
        {
            public static final byte DEALER  = 0x01;
            public static final byte PLAYER  = 0x02;
            public static final byte COUNTER = 0x03;

            public static String toString(byte role)
            {
                switch (role) {
                    case DEALER:
                        return "dealer";
                    case PLAYER:
                        return "player";
                    case COUNTER:
                        return "counter";
                    default:
                        return null;
                }
            }
        }
    }
}
