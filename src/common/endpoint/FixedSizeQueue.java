package common.endpoint;

import java.util.Deque;
import java.util.LinkedList;


public class FixedSizeQueue<T> {

    private int capacity;

    private Deque<T> deque;

    public FixedSizeQueue(int capacity)
    {
        this.capacity = capacity;
        this.deque = new LinkedList<T>();
    }

    public void add(T t)
    {
        deque.addLast(t);
        if(deque.size() > capacity)
        {
            deque.pollFirst();
        }
    }

    public boolean contains(Object o)
    {
        return deque.contains(o);
    }
    
}
