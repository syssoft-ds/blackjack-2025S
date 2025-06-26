package counter;

import java.util.PriorityQueue;

public class DecisionMaxHeap {

    PriorityQueue<Decision> heap;

    public DecisionMaxHeap()
    {
        heap = new PriorityQueue<>(
            (a, b) -> Double.compare(b.prioriy(), a.prioriy())
        );
    }

    public void insert(Decision a)
    {
        heap.add(a);
    }

    public Decision peekMax()
    {
        return heap.peek();
    }
    
    public Decision extractMax()
     
    {
        return heap.poll();
    }

}
