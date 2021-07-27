package de.saar.coli.amtools.astar.agenda;

import de.saar.coli.amtools.astar.Item;
import edu.stanford.nlp.util.BinaryHeapPriorityQueue;

import java.util.HashSet;
import java.util.Set;

public class StanfordAgenda implements Agenda {
    private BinaryHeapPriorityQueue<Item> queue;
    private Set<Item> dequeuedItems;

    public StanfordAgenda() {
        queue = new BinaryHeapPriorityQueue<>();
        dequeuedItems = new HashSet<>();
    }

    @Override
    public void enqueue(Item item) {
        if( ! dequeuedItems.contains(item)) {
            queue.relaxPriority(item, item.getScore());
        }
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Item dequeue() {
        Item it = queue.removeFirst();
        dequeuedItems.add(it);
        return it;
    }
}
