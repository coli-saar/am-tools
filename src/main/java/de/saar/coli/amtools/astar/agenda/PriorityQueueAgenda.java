/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.astar.agenda;

import de.saar.coli.amtools.astar.Astar;
import de.saar.coli.amtools.astar.Item;
import de.up.ling.irtg.util.NumbersCombine;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;

/**
 *
 * @author koller
 */
public class PriorityQueueAgenda implements Agenda {
    private PriorityQueue<Item> agenda = new ObjectHeapPriorityQueue<>();
    private final ItemCache allDequeuedItems = new ItemCache();
    private final ItemCache allItemsOnAgenda = new ItemCache();
    private final boolean declutterAgenda;

    public PriorityQueueAgenda(boolean declutterAgenda) {
        this.declutterAgenda = declutterAgenda;
    }

    @Override
    public void enqueue(Item item) {
        if (item.getScore() > Astar.FAKE_NEG_INFINITY / 2) {
            // if DECLUTTER_AGENDA is switched on, don't enqueue a previously dequeued item again
            if (!declutterAgenda || !allDequeuedItems.contains(item)) {
                
                // If a copy of this item with a better score is currently on
                // the agenda, don't add it to the agenda again. This incurs
                // a little bit of time overhead, but dramatically improves
                // the ability of the JVM to garbage-collect items, and thus
                // the memory use becomes much more predictable.
                double score = item.getScore();
                Item copyOnAgenda = allItemsOnAgenda.get(item);

                if (copyOnAgenda != null) {
                    if( score < copyOnAgenda.getScore() ) {
                        // agenda already has a better-scoring copy of this item
                        return;
                    }
                    
                    // If a worse-scoring copy of the item is already on
                    // the agenda, it would be nice to decrease-key it
                    // instead of adding a spurious copy. However, this
                    // operation is not supported by the fastutil PQ
                    // implementation. The class FibonacciAgenda implements
                    // this behavior, but the underlying Fibonacci heap is
                    // so much slower than the fastutil queue that it doesn't
                    // seem worth it.
                }
                
                agenda.enqueue(item);
                allItemsOnAgenda.add(item);
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

        Item item = agenda.dequeue();
        
        while (!allDequeuedItems.add(item)) {
            // if same item was previously dequeued, keep reading
            
            if (agenda.isEmpty()) {
                return null;
            }
            
            item = agenda.dequeue();
        }
        
        // remove item from agenda and add to previously dequeued
        allItemsOnAgenda.remove(item);
        allDequeuedItems.add(item);
        
        return item;
    }

    public static class ItemCache {

        private Long2ObjectMap<Long2ObjectMap<Item>> cache;

        public ItemCache() {
            cache = new Long2ObjectOpenHashMap<>();
        }

        private static long getOuterKey(Item item) {
            return NumbersCombine.combine(item.getStart(), item.getEnd());
        }

        private static long getInnerKey(Item item) {
            return NumbersCombine.combine(item.getRoot(), item.getType());
        }

        public boolean add(Item item) {
            long outerKey = getOuterKey(item);
            Long2ObjectMap<Item> innerMap = cache.get(outerKey);

            if (innerMap == null) {
                innerMap = new Long2ObjectOpenHashMap<>();
                cache.put(outerKey, innerMap);
            }

            Item old = innerMap.put(getInnerKey(item), item);
            return old == null; // return true if item was previously unknown
        }

        public Item get(Item item) {
            long outerKey = getOuterKey(item);
            Long2ObjectMap<Item> innerMap = cache.get(outerKey);

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
            Long2ObjectMap<Item> innerMap = cache.get(outerKey);

            if (innerMap != null) {
                innerMap.remove(getInnerKey(item));
            }
        }
    }}
