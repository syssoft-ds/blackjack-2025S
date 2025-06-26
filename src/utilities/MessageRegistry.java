package utilities;

import java.util.HashMap;
import java.util.Map;

import json.BlackJackMessage;
import json.BetACK;
import json.BetREQ;
import json.DrawCard;
import json.MalformedRequest;
import json.RegACK;
import json.RegREQ;
import json.Result;
import json.StartGame;
import json.SurrenderACK;
import json.SurrenderREQ;
import json.TurnACK;
import json.TurnREQ;

public class MessageRegistry {

    // This class implements a simple register for BlackJackMessage types
    
    public static final Map <String, Class<? extends BlackJackMessage>> JSONTYPES;
    
    static {
        JSONTYPES = new HashMap<>();
        JSONTYPES.put("bet", BetACK.class);
        JSONTYPES.put("make_bet", BetREQ.class);
        JSONTYPES.put("receive_card", DrawCard.class);
        JSONTYPES.put("error", MalformedRequest.class);
        JSONTYPES.put("ACK", RegACK.class);
        JSONTYPES.put("register", RegREQ.class);
        JSONTYPES.put("result", Result.class);
        JSONTYPES.put("game_started", StartGame.class);
        JSONTYPES.put("surrender", SurrenderACK.class);
        JSONTYPES.put("offer_surrender", SurrenderREQ.class);
        JSONTYPES.put("action", TurnACK.class);
        JSONTYPES.put("your_turn", TurnREQ.class);
};
}
