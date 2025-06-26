package common.endpoint;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;

public class InstanceId {
    
    public byte[] id;

    public InstanceId(int port)
    {
        id = new byte[8];
        if( port > 65535 || port < 0)throw new IllegalStateException("maximum number of instances exceeded");   
        byte[] mac_addr = getMacAdress();
        for(int i = 0; i < 6; i++)
        {
            id[i] = mac_addr[i];
        }
        id[6] = (byte) ((port >> 8) & 0xFF);
        id[7] = (byte) ((port) & 0xFF);
        
    }

    public InstanceId(byte[] id)
    {
        if(id.length != 8) throw new IllegalArgumentException("id must have length 8");
        this.id = id;
    }

    private static byte[] getMacAdress()
    {
        byte[] hardwareAddress = new byte[6];
        try
        {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while(networks.hasMoreElements())
            {
                NetworkInterface ni = networks.nextElement();
                if(ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                
                hardwareAddress = ni.getHardwareAddress();
                
                if(hardwareAddress == null) continue;
            }
        }
        catch(SocketException e){System.out.println(e.getMessage());}
        return hardwareAddress;
    }

    @Override
    public boolean equals(Object object)
    {
        if(this == object) return true;
        if(object == null || getClass() != object.getClass()) return false;
        InstanceId temp = (InstanceId) object;
        for(int i = 0; i < 8; i++)
        {
            if(id[i] != temp.id[i]) return false;
        }    
        return true;
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(id);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < id.length; i++) {
            builder.append(String.format("%02X%s", id[i], (i < id.length - 1) ? "-" : ""));
        }
        return builder.toString();
    }
}
