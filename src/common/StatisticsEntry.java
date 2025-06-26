package common;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import common.endpoint.InstanceId;

public record StatisticsEntry(int round_id, byte role, InstanceId player_id, int hand_id, boolean win, boolean blackJack, boolean split, int wager, int winnings, int deckcount, List<Card> hand)
{
    public static StatisticsEntry fromByte(byte[] arr)
    {
        int round_id = ByteBuffer.wrap(Arrays.copyOfRange(arr, 0, 4)).order(Protocoll.BYTE_ORDER).getInt();

        byte role = arr[4];
        
        InstanceId player_id = new InstanceId(Arrays.copyOfRange(arr, 5, 13));
        
        int hand_id = ByteBuffer.wrap(Arrays.copyOfRange(arr, 13, 17)).order(Protocoll.BYTE_ORDER).getInt();
        
        boolean win = arr[17] == 0 ? false : true;
        
        boolean blackJack = arr[18] == 0 ? false : true;
        
        boolean split = arr[19] == 0 ? false : true;
        
        int wager = ByteBuffer.wrap(Arrays.copyOfRange(arr, 20, 24)).getInt();
        
        int winnings = ByteBuffer.wrap(Arrays.copyOfRange(arr, 24,28)).getInt();
        int deckCount = ByteBuffer.wrap(Arrays.copyOfRange(arr, 28,32)).getInt();

        List<Card> hand = new LinkedList<Card>();
        for(int i = 32 ; i < arr.length; i++)
        {
            Card card = new Card(arr[i]);
            hand.add(card);
        }
        
        return new StatisticsEntry(round_id, role, player_id, hand_id, win, blackJack, split, wager, winnings, deckCount, hand);
    }

    public byte[] toByte()
    {
        byte[] arr = new byte[32 + hand.size()];

        ByteBuffer buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(round_id);
        arr[0] = buffer.get(0);
        arr[1] = buffer.get(1);
        arr[2] = buffer.get(2);
        arr[3] = buffer.get(3);
        
        arr[4] = role;

        arr[5] = player_id.id[0];
        arr[6] = player_id.id[1];
        arr[7] = player_id.id[2];
        arr[8] = player_id.id[3];
        arr[9] = player_id.id[4];
        arr[10] = player_id.id[5];
        arr[11] = player_id.id[6];
        arr[12] = player_id.id[7];

        buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(hand_id);
        arr[13] = buffer.get(0);
        arr[14] = buffer.get(1);
        arr[15] = buffer.get(2);
        arr[16] = buffer.get(3);

        arr[17] = (byte) (win ? 1 : 0);
        arr[18] = (byte) (blackJack ? 1 : 0);
        arr[19] = (byte) (split ? 1 : 0);

        buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(wager);
        arr[20] = buffer.get(0);
        arr[21] = buffer.get(1);
        arr[22] = buffer.get(2);
        arr[23] = buffer.get(3);

        buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(winnings);
        arr[24] = buffer.get(0);
        arr[25] = buffer.get(1);
        arr[26] = buffer.get(2);
        arr[27] = buffer.get(3);

        buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(deckcount);
        arr[28] = buffer.get(0);
        arr[29] = buffer.get(1);
        arr[30] = buffer.get(2);
        arr[31] = buffer.get(3);
        
        int i = 32;
        for(Card c : hand)
        {
            arr[i++] = c.value;
        }

        return arr;
    }
}