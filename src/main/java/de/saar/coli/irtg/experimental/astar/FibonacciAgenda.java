/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.saar.coli.irtg.experimental.astar.FibonacciHeap.Entry;
import de.saar.coli.irtg.experimental.astar.PriorityQueueAgenda.ItemCache;
import de.up.ling.irtg.util.NumbersCombine;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 *
 * @author koller
 */
class FibonacciAgenda implements Agenda {
//    private TreeSet<Item> agenda = new TreeSet<>();
//    private PriorityQueue<Item> agenda = new ObjectHeapPriorityQueue<>();

    private FibonacciHeap<Item> agenda = new FibonacciHeap<>();
    private final ItemCache allDequeuedItems = new ItemCache();
    private final ItemEntryCache allItemsOnAgenda = new ItemEntryCache();
    private final boolean declutterAgenda;

    public FibonacciAgenda(boolean declutterAgenda) {
        this.declutterAgenda = declutterAgenda;
    }

    @Override
    public void enqueue(Item item) {
        if (item.getTotalValue() > Astar.FAKE_NEG_INFINITY / 2) {
            // if DECLUTTER_AGENDA is switched on, don't enqueue a previously dequeued item again
            if (!declutterAgenda || !allDequeuedItems.contains(item)) {
                double score = - item.getScore();
                Entry<Item> copyOnAgenda = allItemsOnAgenda.get(item);

                if (copyOnAgenda != null) {
                    if( score < copyOnAgenda.getPriority() ) {
                        agenda.decreaseKey(copyOnAgenda, score);
                    }
                } else {
                    Entry<Item> entry = agenda.enqueue(item, score);
                    allItemsOnAgenda.add(entry);
                }

//                agenda.add(item);
//                agenda.enqueue(item);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return agenda.isEmpty();
    }

    @Override
    public Item dequeue() {
        if (agenda.isEmpty()) {
            return null;
        }

        Entry<Item> entry = agenda.dequeueMin();
        allItemsOnAgenda.remove(entry.getValue());
        allDequeuedItems.add(entry.getValue());

        return entry.getValue();
    }
    
    private static class ItemEntryCache {

        private Long2ObjectMap<Long2ObjectMap<FibonacciHeap.Entry<Item>>> cache;

        public ItemEntryCache() {
            cache = new Long2ObjectOpenHashMap<>();
        }

        private static long getOuterKey(Item item) {
            return NumbersCombine.combine(item.getStart(), item.getEnd());
        }

        private static long getInnerKey(Item item) {
            return NumbersCombine.combine(item.getRoot(), item.getType());
        }

        public boolean add(Entry<Item> entry) {
            Item item = entry.getValue();
            long outerKey = getOuterKey(item);
            Long2ObjectMap<Entry<Item>> innerMap = cache.get(outerKey);

            if (innerMap == null) {
                innerMap = new Long2ObjectOpenHashMap<>();
                cache.put(outerKey, innerMap);
            }

            Entry<Item> old = innerMap.put(getInnerKey(item), entry);
            return old == null; // return true if item was previously unknown
        }

        public Entry<Item> get(Item item) {
            long outerKey = getOuterKey(item);
            Long2ObjectMap<Entry<Item>> innerMap = cache.get(outerKey);

            if (innerMap == null) {
                return null;
            } else {
                return innerMap.get(getInnerKey(item));
            }
        }

        public boolean contains(Item item) {
            return get(item) != null;
        }

        public void remove(Item item) {
            long outerKey = getOuterKey(item);
            Long2ObjectMap<Entry<Item>> innerMap = cache.get(outerKey);

            if (innerMap != null) {
                innerMap.remove(getInnerKey(item));
            }
        }
    }
}
