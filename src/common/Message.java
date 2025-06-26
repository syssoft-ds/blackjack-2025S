package common;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import common.endpoint.InstanceId;

public class Message {
    
    private static int id_counter = 1;
    
    public int    message_length;
    public int    message_id;
    public InstanceId     sender_id;
    public byte   sender_role;
    public byte   message_type;
    public byte[] payload;

    private Message(InstanceId sender_id, int sender_type, int message_id, int message_type, byte[] payload)
    {
        if(payload == null)
        {
            this.message_length =  Protocoll.Header.LENGTH ;
        }
        else
        {
            this.message_length = payload.length + Protocoll.Header.LENGTH ;
        }
        this.sender_id = sender_id;
        this.sender_role = (byte) sender_type;
        this.message_type = (byte) message_type;
        this.message_id = message_id;
        this.payload = payload;
    }

    public Message (DatagramPacket packet)
    {
        byte[] data = packet.getData();

        byte[] length = Arrays.copyOfRange(data, 0, 4);
        this.message_length = ByteBuffer.wrap(length).order(Protocoll.BYTE_ORDER).getInt();

        byte[] message_id = Arrays.copyOfRange(data, 4, 8);
        this.message_id = ByteBuffer.wrap(message_id).order(Protocoll.BYTE_ORDER).getInt();

        this.sender_id = new InstanceId(Arrays.copyOfRange(data, 8 , 16));

        this.message_type = data[16];
        this.sender_role = data[17];
        this.payload = Arrays.copyOfRange(data, Protocoll.Header.LENGTH, this.message_length);
    }

    public MessageIdentifier getIdentifier()
    {
        return new MessageIdentifier(sender_id, message_id);
    }

    public static Message ackMessage(InstanceId sender_id, byte sender_type, Message message)
    {
        return new Message(sender_id, sender_type, message.message_id, Protocoll.Header.Type.ACK, null);
    }

    public static Message synMessage(InstanceId sender_id, byte sender_type)
    {
        return new Message(sender_id, sender_type, id_counter++, Protocoll.Header.Type.SYN, null);
    }

    public static Message betMessage(InstanceId sender_id, byte sender_type, int amount)
    {
        byte[] payload = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(amount).array();
        return new Message(sender_id, sender_type, id_counter++, Protocoll.Header.Type.BET, payload);
    }

    public static Message cardsMessage(InstanceId sender_id, byte sender_type, List<Card> cards)
    {
        byte[] payload = new byte[cards.size()];
        for(int i = 0; i < payload.length; i++)
        {
            payload[i] = cards.get(i).value;
        }
        return new Message(sender_id, sender_type, id_counter++, Protocoll.Header.Type.CARDS, payload);
    }

    public static Message upcardMessage(InstanceId sender_id, Card card)
    {
        byte[] payload = new byte[1];
        payload[0] = card.value;
        return new Message(sender_id, Protocoll.Header.Role.DEALER, id_counter++, Protocoll.Header.Type.UPCARD, payload);
    }

    public static Message actionRequest(InstanceId sender_id, byte sender_type, List<Card> hand)
    {
        byte[] payload = new byte[hand.size()];
        for(int i = 0; i < payload.length; i++)
        {
            payload[i] = hand.get(i).value;
        }
        return new Message(sender_id, sender_type, id_counter++, Protocoll.Header.Type.ACTION_REQUEST, payload);
    }

    public static Message actionMessage(InstanceId sender_id, byte sender_type, byte action)
    {
        byte[] payload = new byte[1];
        payload[0] = action;
        return new Message(sender_id, sender_type, id_counter++, Protocoll.Header.Type.ACTION, payload);
    }

    public static Message shuffledMessage(InstanceId sender_id, Stack<Card> draw_stack)
    {
        byte[] payload = new byte[draw_stack.size()];
        int index = 0;
        for(Card card : draw_stack)
        {
            payload[index++] = card.value;
        }

        return new Message(sender_id, Protocoll.Header.Role.DEALER, id_counter++, Protocoll.Header.Type.SHUFFLED, payload);
    }

    public static Message deckCountMessage(InstanceId sender_id, int deckCount)
    {
        byte[] payload = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(deckCount).array();
        return new Message(sender_id, Protocoll.Header.Role.DEALER, id_counter++, Protocoll.Header.Type.DECKCOUNT, payload);
    }

    public static Message winnigsMessage(InstanceId sender_id, int winnings)
    {
        byte[] payload = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(winnings).array();
        return new Message(sender_id, Protocoll.Header.Role.DEALER, id_counter++, Protocoll.Header.Type.WINNINGS, payload);
    }

    public static Message statisticsMessage(InstanceId sender_id, int sender_role, StatisticsEntry entry)
    {
        byte[] payload = entry.toByte();
        return new Message(sender_id, sender_role, id_counter++, Protocoll.Header.Type.STATISTICS, payload);
    }

    public StatisticsEntry getEntry()
    {
        if(message_type != Protocoll.Header.Type.STATISTICS) throw new IllegalStateException("message is not type STATISTICS");
        return StatisticsEntry.fromByte(payload);
    }

    public byte getAction()
    {
        if(message_type != Protocoll.Header.Type.ACTION) throw new IllegalStateException("message is not of type ACTION");
        return payload[0];
    }

    public int getInt()
    {
        if(! (message_type == Protocoll.Header.Type.BET || message_type == Protocoll.Header.Type.DECKCOUNT || message_type == Protocoll.Header.Type.WINNINGS))
        {
            throw new IllegalStateException("message is not of type BET or DECKCOUNT or WINNIGS");
        } 
        return ByteBuffer.wrap(payload).order(Protocoll.BYTE_ORDER).getInt();
    }

    public Card getCard()
    {
        if(message_type != Protocoll.Header.Type.UPCARD) throw new IllegalStateException("message is not of type UPCARD");
        return new Card(payload[0]);
    }

    public List<Card> getCards()
    {
        if(! (message_type == Protocoll.Header.Type.CARDS || message_type == Protocoll.Header.Type.ACTION_REQUEST || message_type == Protocoll.Header.Type.SHUFFLED))
        {
            throw new IllegalStateException("message is not of type CARDS");
        }
        List<Card> cards = new LinkedList<>();

        for(int i = 0; i < payload.length; i++)
        {
            cards.add(new Card(payload[i]));
        }

        return cards;
    }

    public byte[] getBytes()
    {
        byte[] arr = new byte[Protocoll.Header.LENGTH + message_length];
        
        ByteBuffer buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(message_length);
        arr[0] = buffer.get(0);
        arr[1] = buffer.get(1);
        arr[2] = buffer.get(2);
        arr[3] = buffer.get(3);

        buffer = ByteBuffer.allocate(4).order(Protocoll.BYTE_ORDER).putInt(message_id);
        arr[4] = buffer.get(0);
        arr[5] = buffer.get(1);
        arr[6] = buffer.get(2);
        arr[7] = buffer.get(3);

        arr[8]  = this.sender_id.id[0];
        arr[9]  = this.sender_id.id[1];
        arr[10] = this.sender_id.id[2];
        arr[11] = this.sender_id.id[3];
        arr[12] = this.sender_id.id[4];
        arr[13] = this.sender_id.id[5];
        arr[14] = this.sender_id.id[6];
        arr[15] = this.sender_id.id[7];

        arr[16] = (byte) message_type;
        arr[17] = (byte) sender_role;

        if(payload != null)
        {
            for(int i = 0; i < payload.length; i++)
            {
                arr[Protocoll.Header.LENGTH + i] = payload[i];
            }
        } 

        return arr;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("{length: ").append(message_length).append("}").append(Protocoll.SEPERATOR);
        builder.append("{message_id: ").append(message_id).append("}").append(Protocoll.SEPERATOR);
        builder.append("{sender_id: ").append(sender_id).append("}").append(Protocoll.SEPERATOR);
        builder.append("{message_type: ").append(message_type).append("}").append(Protocoll.SEPERATOR);
        builder.append("{sender_type: ").append(sender_role).append("}").append(Protocoll.SEPERATOR);
        if(payload != null)
        {
            builder.append("{payload: ");
            for(int i = 0; i < payload.length; i++)
            {
                builder.append(String.format("%02X", payload[i]));
            }
            builder.append("}");
        }
        return builder.toString();
    }
}