package common.endpoint;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import common.Context;
import common.Message;
import common.MessageIdentifier;
import common.Protocoll;

public abstract class UDP_Endpoint implements Runnable
{ 

    private final String logFilePath;
    public final InstanceId id;
    public final byte role;

    public InetAddress addr;
    public int port;

    public DatagramSocket socket;
    public Thread listenThread;  
    public Thread inputThread;
    
    public FixedSizeQueue<MessageIdentifier> received_messages;
    public Set<MessageIdentifier> acknowledged_Messages;
   
    public UDP_Endpoint(byte role)
    {
        this.role = role;

        if(role == Protocoll.Header.Role.PLAYER)
        {
            this.logFilePath = System.getProperty("user.dir") + "/" +Context.Log.player_log;
        }
        else if(role == Protocoll.Header.Role.DEALER)
        {
            this.logFilePath = System.getProperty("user.dir") + "/" +Context.Log.dealer_log;
        }
        else
        {
            this.logFilePath = System.getProperty("user.dir") + "/" +Context.Log.counter_log;
        }

        this.acknowledged_Messages = Collections.synchronizedSet(new TreeSet<>());
        this.received_messages = new FixedSizeQueue<>(Context.Network.NUMBER_OF_LAST_MESSAGES);
        initIpAndPort();
        this.id = new InstanceId(this.port);

        listenThread = new Thread(() -> this.listenNetwork());
        inputThread = new Thread(() -> this.listenSystemIn());
        System.out.println("instance id is " + id + " of udp endpoint (" + addr.getHostAddress() +", " +  port + ") with role " + Protocoll.Header.Role.toString(role));
        printHelp();
    }

    private void initIpAndPort()
    {
        try
        {
            socket = new DatagramSocket();
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            addr = socket.getLocalAddress();
            port = socket.getLocalPort();
            socket.disconnect();
        }
        catch(UnknownHostException e)
        {
            System.out.println(e.getMessage());
        }
        catch(SocketException e)
        {
            System.out.println(e.getMessage());
        }
    }

    public final void listenNetwork()
    {
        byte[] buffer = new byte[Context.Network.BUFFER_LENGTH];

        while(true)
        {
            try
            {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                LocalDateTime timestamp = LocalDateTime.now();
                InetAddress addr = packet.getAddress();
                int port = packet.getPort();

                Message message = new Message(packet);
                log(timestamp, addr, port, message, true);
                

                MessageIdentifier messageIdentifier = message.getIdentifier(); 
                
                if(message.message_type == Protocoll.Header.Type.ACK)
                {
                    synchronized(acknowledged_Messages)
                    {
                        acknowledged_Messages.add(messageIdentifier);
                    }
                }
                else
                {
                    Message ackMessage = Message.ackMessage(message.sender_id, role, message);
                    DatagramPacket ackPacket = new DatagramPacket(ackMessage.getBytes(), ackMessage.message_length, addr, port);
                    socket.send(ackPacket);
                    timestamp = LocalDateTime.now();
                    log(timestamp, addr, port, ackMessage, false);
                    if(! received_messages.contains(messageIdentifier))
                    {
                        received_messages.add(messageIdentifier);
                        processMessage(addr, port, message);
                    }
                }
            }
            catch(IOException e)
            {
                System.out.println(e.getMessage());
            }
        }
    } 
    
    public void send(InetAddress addr, int port, Message message) throws IOException, MaxRetriesExceededException
    {
        MessageIdentifier messageIdentifier = message.getIdentifier();
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        
        int tries = 0;
        int maxTries = Context.Network.TRIES_LINEAR + Context.Network.TRIES_NON_LINEAR;
        while(tries < maxTries)
        {
            synchronized(acknowledged_Messages)
            {
                if(acknowledged_Messages.contains(messageIdentifier))
                {   
                    acknowledged_Messages.remove(messageIdentifier);
                    break;
                }
            }
            socket.send(packet);
            LocalDateTime timeStamp = LocalDateTime.now();
            log(timeStamp, addr, port, message, false);
            tries++;
            try
            {
                if(tries < Context.Network.TRIES_LINEAR)
                {
                    Thread.sleep(Context.Network.TIMEOUT_MS);
                }
                else
                {
                    Thread.sleep((long) Math.pow(2, tries - Context.Network.TRIES_LINEAR) * Context.Network.TIMEOUT_MS);
                }
            }
            catch (InterruptedException e)
            {
                System.out.println(e.getMessage());
            }
        }
        if(tries == maxTries) throw new MaxRetriesExceededException("maximum tries exceeded");
    }

    public final void listenSystemIn()
    {
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while(true)
            {
                String input = reader.readLine();
                processInput(input);
            }
        }
        catch(IOException e)
        {
            System.out.println(e.getMessage());
        }
    }
    
    public final void log(LocalDateTime timestamp, InetAddress addr, int port, Message message, boolean received)
    {
        StringBuilder builder = new StringBuilder();
        String formattedTimestamp = timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        builder.append(formattedTimestamp).append(Protocoll.SEPERATOR);
        
        if(received)
        {
            builder.append(addr.getHostAddress()).append(Protocoll.SEPERATOR);
            builder.append(this.addr.getHostAddress()).append(Protocoll.SEPERATOR);
            builder.append(port + "->" + this.port).append(Protocoll.SEPERATOR);
        }
        else
        {
            builder.append(addr.getHostAddress()).append(Protocoll.SEPERATOR);
            builder.append(this.addr.getHostAddress()).append(Protocoll.SEPERATOR);
            builder.append(port + "->" + this.port).append(Protocoll.SEPERATOR);
        }
        builder.append(message.toString()).append("\n");

        File logFile = new File(logFilePath);

        try(FileWriter writer = new FileWriter(logFile, true))
        {
            writer.write(builder.toString());
        }
        catch(IOException e)
        {
            System.out.println(e.getMessage());
        }

    }

    public abstract void processMessage(InetAddress sender_addr, int sender_port, Message message);
    public abstract void processInput(String message);
    protected abstract void printHelp();

    public void run()
    {
        listenThread.start();
        inputThread.start();
    }
}