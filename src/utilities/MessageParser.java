package utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import json.BlackJackMessage;
import utilities.exceptions.UnknownJsonTypeException;

public class MessageParser{

    // This class implements a simple parser for BlackJackMessages

    private static final ObjectMapper mapper = new ObjectMapper();

    public static BlackJackMessage parse(String json) throws Exception{

        JsonNode root = mapper.readTree(json);

        JsonNode typeNode = root.get("type");
        if(typeNode == null || typeNode.isNull())
            throw new UnknownJsonTypeException("Missing 'type' field in JSON: " + json);

        String type = typeNode.asText();
        
        Class<? extends BlackJackMessage> messageClass = MessageRegistry.JSONTYPES.get(type);

        if(messageClass == null) {
            throw new UnknownJsonTypeException("Unknown message type: " + type);
        }
        return mapper.treeToValue(root, messageClass);
    }
}