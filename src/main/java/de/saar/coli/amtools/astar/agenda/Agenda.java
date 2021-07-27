/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.astar.agenda;

import de.saar.coli.amtools.astar.Item;

/**
 *
 * @author koller
 */
public interface Agenda {
    public void enqueue(Item item);
    public boolean isEmpty();
    public Item dequeue();
}
