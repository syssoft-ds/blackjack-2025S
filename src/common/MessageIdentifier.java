package common;

import java.nio.ByteBuffer;

import common.endpoint.InstanceId;

public record MessageIdentifier(InstanceId id, int message_id) implements Comparable<MessageIdentifier>
{

    @Override
    public int compareTo(MessageIdentifier o)
    {
        int cmp = Long.compare(ByteBuffer.wrap(this.id.id).order(Protocoll.BYTE_ORDER).getLong(), ByteBuffer.wrap(o.id.id).order(Protocoll.BYTE_ORDER).getLong());
        if(cmp != 0) return cmp;

        return Integer.compare(this.message_id, o.message_id);
    }
    
}