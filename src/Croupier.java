import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import json.*;
import utilities.enums.Role;
import utilities.exceptions.*;
import utilities.UtilityCloset;
import utilities.MessageBus;
import utilities.MessageParser;

public class Croupier{

    // UDP stuff
    private static int localport=-1;  
    private static InetAddress localhost;
    private static DatagramSocket socket;
    private static final int BUFFER_SIZE=4096;
    private static BufferedReader input;
    private static MessageBus bus = new MessageBus();
    private static HashMap <InetAddress, String> addressLookup = new HashMap <>();

    // Croupier stuff
    private static Table table;
    private static final int MAX_PLAYER = 4;
    private static final int numberOfDecks=6;
    private static Deck deck;
    private static Hand croupierHand;

/********************************
 ** main method, program start **
 ********************************/


    public static void main(String[] args)throws IOException{

        // set port for udp socket
        localport = UtilityCloset.setPort();

        // determine local ip
        localhost = UtilityCloset.getLocalIP();

        if(localhost == null)
            return;

        try{

            socket = new DatagramSocket(localport);

            Thread receiver = new Thread(() -> { try{ receiver(socket);} 
                                                 catch (IOException e){ System.out.println("receiver shutdown.");}
                                                });
            receiver.start();
            initGame();

        } catch(IOException e){
            // generic
        } finally{
            input.close();
            socket.close();
        }
    }


/**********************************************
 ** Initialisation, registration, game start **
 **********************************************/

    private static void initGame(){

        // setup input
        input = new BufferedReader(new InputStreamReader(System.in));
        String line;
        boolean retry = false;

        // setup game objects
        table = new Table(MAX_PLAYER);
        deck = new Deck(numberOfDecks);
        deck.shuffle();

        UtilityCloset.greeter();
        
        while(true){
            try{
                if(retry)
                    System.out.println("\nTo Start another round at the same type <start>. To clear the table type <clear>. For more information type <help>. To bypass the lack of a kartenzaehler type <idkfa>.\n");

                line = input.readLine();

                // splitting strings
                line = line.trim();

                switch(line.toLowerCase()){
                    case "start":
                        if (table.getPlayerCnt() == 0)
                            throw new StartGameException("No player has been registered at this table.");
                        
                        if (table.getKartenzaehler() == null)
                            throw new StartGameException("No kartenzaehler has been registered at this table.");
                        
                        System.out.println("Starting game.");

                        // add message to all registered players and kartenzaehler
                        
                        blackJack();
                        retry = true;
                        break;
                    
                    case "contact":
                        System.out.println("local IP:\t" + localhost.getHostAddress() + "\nlocal port:\t" + localport);
                        break;

                    case "clear":

                        // a message should be sent to each player and the kartenzaehler, but no format has been specified yet
                        // hence no such format is implemented by the player or kartenzaehler class

                        table = new Table(MAX_PLAYER);
                        break;

                    case "help":
                        UtilityCloset.help();

                        break;

                    case "idkfa":
                        // for debugging and testing
                        table.setKartenzaehler(new InetSocketAddress(localhost, localport));
                        break;

                    case "exit": return;
                    
                    default: System.out.println("\"" + line + "\" is not a known command.");
                    }
            } catch (StartGameException startException){
                System.out.println(startException.getMessage());
            } catch (IOException e){
            // generic
            }

        }
    }


    private static void registerPlayer (RegREQ register, InetAddress srcAddress, int srcPort) throws RegisterException{

        // This method registers players

        // check whether table exists
        if(table == null)
            table = new Table(MAX_PLAYER);
        
        // check whether table is full
        if(table.getPlayerCnt() != table.getNumberOfSeats()){

            if(!addressLookup.containsKey(srcAddress)){

                InetSocketAddress contact = new InetSocketAddress(srcAddress, srcPort);
                table.addPlayer(register.name, register.credit, contact);
                addressLookup.put(srcAddress, register.name);
            }
        }
        else{
            throw new RegisterException ("Player is already registered at this table."); 
        }
    }

    private static void registerZaehler(RegREQ register, InetAddress srcAddress, int srcPort) throws RegisterException{

        // This method registers Kartenzaehler

        if(table.getKartenzaehler()==null)
            table.setKartenzaehler(new InetSocketAddress(srcAddress, srcPort));
    
        else{
            throw new RegisterException ("Another kartenzaehler is already registered at this table.");

        }
    }


/*********************************************
 ** receiver logic for incoming UDP Packets **
 *********************************************/


    private static void receiver (DatagramSocket socket)throws IOException{
        
        // This method receives incomming UDP Packages and parses them for known JSON objects 

        byte[] buffer = new byte[BUFFER_SIZE];
        byte[] packetBuffer = new byte[BUFFER_SIZE];
        ObjectMapper mapper = new ObjectMapper();
        BlackJackMessage message = null;

        while(true){
            DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
            socket.receive(packet);

            String payload = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
            
            try{
                message = MessageParser.parse(payload); 
            } catch (UnknownJsonTypeException jsonException){
                System.out.println(jsonException.getMessage());
                continue;
            } catch (Exception e){
                e.printStackTrace();
            }

            switch(message.getType()){
                // bet ACK
                case "bet": 
                    if(message instanceof BetACK response){
                        CompletableFuture<BetACK> future = bus.betRequests.remove(packet.getAddress());
                        if(future != null)
                            future.complete(response);
                    }

                    break;
                
                // error x
                case "error": 
                    MalformedRequest error = (MalformedRequest) message;
                    System.out.println(error.message);
                    break;

                // register REQ x     
                case "register": 
                    try{                    
                        RegREQ register = (RegREQ) message;

                        if(register.role==Role.Spieler){
                            registerPlayer(register, packet.getAddress(), packet.getPort());
                            RegACK regSuccessPrep = new RegACK();
                            buffer = mapper.writeValueAsBytes(regSuccessPrep);
                            DatagramPacket regSuccessPack = new DatagramPacket(buffer, buffer.length, packet.getAddress(), packet.getPort());
                            socket.send(regSuccessPack);
                        }
                        else
                            registerZaehler(register, packet.getAddress(), packet.getPort());
                    
                    } catch (RegisterException regException){
                            
                        MalformedRequest errorMSG = new MalformedRequest(regException.getMessage());
                            buffer = mapper.writeValueAsBytes(errorMSG);
                            DatagramPacket sendMSG = new DatagramPacket(buffer, buffer.length,packet.getAddress(), packet.getPort());
                            
                            try{
                                socket.send(sendMSG);
                            }catch(IOException IOExcept){
                                // generic
                            }
                    }
                    break;

                // surrender ACK
                case "surrender": 
                    if (message instanceof SurrenderACK response){
                        CompletableFuture<SurrenderACK> future = bus.surrenderRequests.get(packet.getAddress());
                        if(future != null)
                            future.complete(response);
                    }
                    break;
                
                // turn ACK
                case "action": 
                    if (message instanceof TurnACK response){
                        mapper.writeValueAsString(message);
                        CompletableFuture<TurnACK> future = bus.turnRequests.get(packet.getAddress());
                        if(future != null)
                            future.complete(response);
                    }

                    break;
                
            }
        }
    }


    /*********************************************
     ** Game logic and outgoing UDP packets **
     *********************************************/


    private static void blackJack(){

        // This is where the game happens

        byte[] BlackJackBuffer = new byte[BUFFER_SIZE];
        ObjectMapper mapper = new ObjectMapper();
        croupierHand = null;
        // eject players that have won to often
        // send list of players to kartenzaehler

        // notify game start

        for(Seat seat:table.getSeats())
            if(seat.isOccupied()){
                try{
                    InetSocketAddress contact = seat.getPlayer().getContact();
                    StartGame startPrep = new StartGame();
                    BlackJackBuffer = mapper.writeValueAsBytes(startPrep);
                    DatagramPacket startPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                    socket.send(startPack);

                    // debugging
                    // System.out.println(contact.getAddress()+":"+contact.getPort());
                    // System.out.println(mapper.writeValueAsString(startPrep));

                }catch(Exception e){ e.printStackTrace();}
            }

        // betting phase
        boolean betset;
        try{
            for(Seat seat: table.getSeats()){
                if(seat.isOccupied()){
                    betset = false;
                    while(!betset){
                        InetSocketAddress contact = seat.getPlayer().getContact();

                        // send BetREQ

                        BetREQ betREQ = new BetREQ();
                        BlackJackBuffer = mapper.writeValueAsBytes(betREQ);
                        DatagramPacket sendBetREQ = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                        socket.send(sendBetREQ);

                        // create future
                        
                        CompletableFuture<BetACK> future = new CompletableFuture<>();
                        bus.betRequests.put(contact.getAddress(), future);

                        // wait for response

                        BetACK futureResponse = future.get(180, TimeUnit.SECONDS);

                        // create initial Hand
                        seat.getPlayer().addHand(new Hand(0));
                        seat.getPlayer().getHand(0).setPlayer(seat.getPlayer());

                        // place bet
                        try{
                            
                            seat.getPlayer().getHand(0).setInitialBet(futureResponse.amount);
                            betset = true;

                        } catch (illegalHandException ih){
                            
                            MalformedRequest errorPrep = new MalformedRequest(ih.getMessage());
                            BlackJackBuffer = mapper.writeValueAsBytes(errorPrep);
                            DatagramPacket errorPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                            socket.send(errorPack);
                        }
                    }
                }
            }
        } catch (TimeoutException timeoutException){
            timeoutException.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }


        // deal cards

        try{
            for(int turn = 0; turn < 2; turn++){
                for (Seat seat: table.getSeats()){
                    if(seat.isOccupied()){
                        // debugging
                        // System.out.println(seat.getPosition());
                
                        Player player = seat.getPlayer();
                        InetSocketAddress contact = player.getContact();

                        if (player.getHand(0).getCards().size() == 0){

                            // draw card
                            Card newCard = deck.draw();

                            // debugging
                            // System.out.println(newCard.toString());

                            // add to hand
                            player.getHand(0).addCard(newCard);

                            // send card to player
                            DrawCard newCardPrep = new DrawCard(new PlayerCard(newCard.getRank(), newCard.getSuit()), 0);
                            BlackJackBuffer = mapper.writeValueAsBytes(newCardPrep);
                            DatagramPacket newCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                            socket.send(newCardPack);

                            // Debugging
                            // System.out.println(mapper.writeValueAsString(newCardPrep));

                            // send new card to kartenzaehler

                        }
                        else {
                            // first round finished, player must have 1 hand

                            // draw card

                            Card newCard = deck.draw();

                            // debugginQg
                            // System.out.println(newCard.toString());

                            // add to hand

                            player.getHand(0).addCard(newCard);

                            // send new card to player

                            DrawCard newCardPrep = new DrawCard(new PlayerCard(newCard.getRank(), newCard.getSuit()),0);
                            BlackJackBuffer = mapper.writeValueAsBytes(newCardPrep);
                            DatagramPacket newCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                            socket.send(newCardPack);

                            // Debugging
                            // System.out.println(mapper.writeValueAsString(newCardPrep));
                            
                            // send new card to kartenzaehler
                        }
                    }
                }
                // Croupier logic
                if(croupierHand == null){
                    croupierHand = new Hand(0);
                    croupierHand.addCard(deck.draw());

                    // the first croupier card is send to each player with the first TurnREQ below
                    
                    // send new card to kartenzaehler

                } else {
                    croupierHand.addCard(deck.draw());

                    // card is hidden
                }
            }

        } catch(DeckException DeckException){
            System.out.println(DeckException.getMessage());
            
            // return if no cards in Deck b/c not all players have sufficient cards to play
            // possibly add more robust exception handling

            return;
        } catch (Exception e){
            e.printStackTrace();
        }

        // test for blackJack
        
        if (croupierHand.checkBlackJack()){

            // compute payouts and send results

            double multiplier = 0;

            for(Seat seat: table.getSeats()){

                if (seat.isOccupied()){
                    // compute payouts                
                    if (seat.getPlayer().getHand(0).checkBlackJack()){
                        seat.getPlayer().payout(seat.getPlayer().getHand(0).getInitialBet()*1.5);
                        multiplier = 1.5;
                    } else{
                        seat.getPlayer().payout(seat.getPlayer().getHand(0).getInitialBet()*(-1));
                        multiplier = -1;
                    }
                    
                    // get contact details
                    InetSocketAddress contact = seat.getPlayer().getContact();

                    // send results
                    try{
                        Result resultPrep = new Result((int) (seat.getPlayer().getBet()*multiplier), multiplier==1.5? "You won.": "You lost.");
                        BlackJackBuffer = mapper.writeValueAsBytes(resultPrep);
                        DatagramPacket resultPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                        socket.send(resultPack);
                    } catch (IOException e){
                        // generic
                    }
                }
            }
            
            // cleanup hands and bets
            for(Seat seat: table.getSeats())
                if(seat.isOccupied())
                    seat.getPlayer().cleanup();

            // game over
            return;
        }

        // ask for surrender

        try{
            int surrenderCnt = 0;

            for(Seat seat: table.getSeats()){
                if(seat.isOccupied()){
                    // get contact details
                    InetSocketAddress contact = seat.getPlayer().getContact();

                    // send surrender request
                    SurrenderREQ surrenderRequest = new SurrenderREQ();
                    BlackJackBuffer = mapper.writeValueAsBytes(surrenderRequest);
                    DatagramPacket sendRequest = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                    socket.send(sendRequest);
                
                    // create future
                    CompletableFuture<SurrenderACK> future = new CompletableFuture<>();
                    bus.surrenderRequests.put(contact.getAddress(), future); 

                    // wait for response
                    SurrenderACK futureResponse = future.get(180, TimeUnit.SECONDS);

                    // add logic to interprete response/
                    if(futureResponse.answer.equalsIgnoreCase("yes")){
                        
                        seat.getPlayer().surrender();
                        surrenderCnt++;

                        // calculate payout
                        seat.getPlayer().payout(seat.getPlayer().getHand(0).getInitialBet()*(-0.5));

                        // send result
                        Result resultPrep = new Result((int) (seat.getPlayer().getHand(0).getInitialBet()*(-0.5)), "You surrendered.");
                        BlackJackBuffer = mapper.writeValueAsBytes(resultPrep);
                        DatagramPacket resultPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                        socket.send(resultPack);
                    }
                }
            }

            if(surrenderCnt == table.getPlayerCnt()){

                //return to initial state
                for(Seat seat: table.getSeats())
                    if(seat.isOccupied())
                        seat.getPlayer().cleanup();

                // game over
                return;
            }

        } catch (Exception e){
            // generic
        }

        // player turns

        for(Seat seat: table.getSeats()){
            
            if(seat.isOccupied()){

                try{

                    InetSocketAddress contact = seat.getPlayer().getContact();
                    
                    // send turn request

                    PlayerCard croupierCard = new PlayerCard(croupierHand.getCard(1).getRank(), croupierHand.getCard(1).getSuit()); 
                    TurnREQ croupierCardPrep = new TurnREQ(croupierCard);
                    BlackJackBuffer = mapper.writeValueAsBytes(croupierCardPrep);
                    DatagramPacket croupierCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                    socket.send(croupierCardPack);
            
                    do{

                        // create future

                        CompletableFuture<TurnACK> future = new CompletableFuture<>();
                        bus.turnRequests.put(contact.getAddress(), future);

                        // auf antwort warten
                
                        TurnACK futureResponse = future.get(180, TimeUnit.SECONDS);

                        switch(futureResponse.action){
                            case "hit": 
                                
                                // draw card

                                Card newCard = deck.draw();
                                
                                seat.getPlayer().getHand(futureResponse.handIndex).addCard(newCard);

                                // Debugging
                                // System.out.println(seat.getPlayer().getHands().toString());

                                // send card

                                DrawCard newCardPrep = new DrawCard(new PlayerCard(newCard.getRank(), newCard.getSuit()), futureResponse.handIndex);
                                BlackJackBuffer = mapper.writeValueAsBytes(newCardPrep);
                                DatagramPacket newCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(),contact.getPort());
                                socket.send(newCardPack);
                                
                                // added
                                if(seat.getPlayer().getHand(futureResponse.handIndex).calculatePoints(false) <= 21){
                                    TurnREQ turnRequestPrep = new TurnREQ(croupierCard);
                                    BlackJackBuffer = mapper.writeValueAsBytes(turnRequestPrep);
                                    DatagramPacket turnRequestPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                    socket.send(turnRequestPack);
                                } else{
                                
                                    MalformedRequest errorPrep = new MalformedRequest("Bust.");
                                    BlackJackBuffer = mapper.writeValueAsBytes(errorPrep);
                                    DatagramPacket errorPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                    socket.send(errorPack);
                                    
                                    seat.getPlayer().getHand(futureResponse.handIndex).stand();
                                    seat.getPlayer().getHand(futureResponse.handIndex).setBust();
                                }
                                break;

                            case "split": 

                                if(seat.getPlayer().getHand(futureResponse.handIndex).getCard(0) == seat.getPlayer().getHand(futureResponse.handIndex).getCard(1)){
                                    //split hand

                                    if(seat.getPlayer().getHand(futureResponse.handIndex).getInitialBet() <= seat.getPlayer().getCredit()){

                                        Hand secondHand = seat.getPlayer().getHand(futureResponse.handIndex).split(seat.getPlayer());
                                        Hand firstHand = seat.getPlayer().getHand(futureResponse.handIndex);
                                        secondHand.setPlayer(seat.getPlayer());
                                        seat.getPlayer().addHand(secondHand);

                                        // request additional bet (equal to original)
                                        
                                        secondHand.setInitialBet(firstHand.getInitialBet());

                                        // draw card for first hand

                                        Card firstCard = deck.draw();
                                        firstHand.addCard(firstCard);

                                        // send card

                                        DrawCard firstCardPrep = new DrawCard(new PlayerCard(firstCard.getRank(), firstCard.getSuit()), futureResponse.handIndex);
                                        BlackJackBuffer = mapper.writeValueAsBytes(firstCardPrep);
                                        DatagramPacket firstCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                        socket.send(firstCardPack);

                                        // draw card for second hand

                                        Card secondCard = deck.draw();
                                        secondHand.addCard(secondCard);

                                        // send card

                                        DrawCard secondCardPrep = new DrawCard(new PlayerCard(secondCard.getRank(), secondCard.getSuit()), secondHand.getHandNr());
                                        BlackJackBuffer = mapper.writeValueAsBytes(secondCardPrep);
                                        DatagramPacket secondCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                        socket.send(secondCardPack);
                                    } else {

                                        MalformedRequest errorPrep = new MalformedRequest("Insufficient funds to make this split.");
                                        BlackJackBuffer = mapper.writeValueAsBytes(errorPrep);
                                        DatagramPacket errorPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                        socket.send(errorPack);
                                    }
                                
                                } else{

                                    MalformedRequest errorPrep = new MalformedRequest("Split unpossible");
                                    BlackJackBuffer = mapper.writeValueAsBytes(errorPrep);
                                    DatagramPacket errorPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                    socket.send(errorPack);
                                }

                                // added
                                TurnREQ turnRequestPrep = new TurnREQ(croupierCard);
                                BlackJackBuffer = mapper.writeValueAsBytes(turnRequestPrep);
                                DatagramPacket turnRequestPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                socket.send(turnRequestPack);

                                break;

                            case "double_down": 

                                // double original bet
                                try{
                                    seat.getPlayer().getHand(futureResponse.handIndex).doubleDown();
                                } catch (illegalHandException ih){
                                    
                                    MalformedRequest errorPrep = new MalformedRequest(ih.getMessage());
                                    BlackJackBuffer = mapper.writeValueAsBytes(errorPrep);
                                    DatagramPacket errorPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                    socket.send(errorPack);
                                    
                                    turnRequestPrep = new TurnREQ(croupierCard);
                                    BlackJackBuffer = mapper.writeValueAsBytes(turnRequestPrep);
                                    turnRequestPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                    socket.send(turnRequestPack);
                                    break;
                                }
                                // get 1 more card

                                newCard = deck.draw();
                                seat.getPlayer().getHand(futureResponse.handIndex).addCard(newCard);

                                // send card

                                newCardPrep = new DrawCard(new PlayerCard(newCard.getRank(), newCard.getSuit()), futureResponse.handIndex);
                                BlackJackBuffer = mapper.writeValueAsBytes(newCardPrep);
                                newCardPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                socket.send(newCardPack);

                                // end turn

                                seat.getPlayer().getHand(futureResponse.handIndex).stand();

                                // added
                                if(!seat.getPlayer().isStandingDown()){
                                    turnRequestPrep = new TurnREQ(croupierCard);
                                    BlackJackBuffer = mapper.writeValueAsBytes(turnRequestPrep);
                                    turnRequestPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                                    socket.send(turnRequestPack);
                                }
                                break;

                            case "stand": 
                                seat.getPlayer().getHand(futureResponse.handIndex).stand();
                        }

                        // Debugging
                        // System.out.println(seat.getPlayer().isStandingDown());
                        
                } while (!seat.getPlayer().isStandingDown());

                } catch(IOException iOException){
                    iOException.printStackTrace();
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }

        // croupier turn

        // add additional json format to inform players of the cards the croupier has drawn

        while(true){
            try{
                // display cards
                System.out.println("\ncurrent hand:\n" + croupierHand.toString() + "\ncurrent Points: " +croupierHand.calculatePoints(true)+"\navailable actions: <hit>, <stand>\n");

                if(croupierHand.calculatePoints(true) <= 17){
                    String line = input.readLine();
                    line.trim();
                    switch(line.toLowerCase()){
                        case "hit": 
                            Card newCard = deck.draw();
                            croupierHand.addCard(newCard);
                            System.out.println("new card: " + newCard.toString());
                            break;

                        case "stand":
                            croupierHand.stand();
                            break;
                        default: System.out.println("\"" + line + "\" is not a known command.");
                    }
                } else
                    croupierHand.stand();
                
                if(croupierHand.isStandingDown() || croupierHand.calculatePoints(true) > 17)
                    break;
            } catch(DeckException deckException){
                System.out.println(deckException.getMessage());
            } catch(IOException iOException){
                // generic
            }
        }


        // payout and cleanup
        double earnings = 0;
        for(Seat seat: table.getSeats()){
            if(seat.isOccupied()){
                if(21 < croupierHand.calculatePoints(true)){
                    for(Hand hand : seat.getPlayer().getHands()){
                        earnings += hand.getTotalAmount();
                    } 
                }else{    
                    for(Hand hand: seat.getPlayer().getHands()){
                        if(hand.checkBlackJack())
                            earnings = hand.getTotalAmount()*1.5;
                        else if (hand.calculatePoints(false)<=21 && croupierHand.calculatePoints(true) < hand.calculatePoints(false) && !hand.getBust())
                            earnings += hand.getTotalAmount();
                        else if (21 < hand.calculatePoints(false) || hand.calculatePoints(false) < croupierHand.calculatePoints(true)|| hand.getBust())
                            earnings -= hand.getTotalAmount();
                    }
                }
                seat.getPlayer().payout(earnings);
                System.out.println(earnings >= 0 ? seat.getPlayer().getName()+ " earned " + earnings: seat.getPlayer().getName()+ " lost "+ earnings );
                    

                // get contact
                InetSocketAddress contact = seat.getPlayer().getContact();

                // send results
                try{
                    Result resultPrep = new Result((int) earnings, "Your total earnings are " + (int) earnings);
                    BlackJackBuffer = mapper.writeValueAsBytes(resultPrep);
                    DatagramPacket resultPack = new DatagramPacket(BlackJackBuffer, BlackJackBuffer.length, contact.getAddress(), contact.getPort());
                    socket.send(resultPack);
                } catch(IOException iOException){
                    iOException.printStackTrace();
                }

                // cleanup
                seat.getPlayer().cleanup();
            }
        earnings = 0;
        }
    }

}