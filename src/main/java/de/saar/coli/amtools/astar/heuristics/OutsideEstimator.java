/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amtools.astar.heuristics;

import de.saar.coli.amtools.astar.Item;

/**
 *
 * @author koller
 */
public interface OutsideEstimator {
    public double evaluate(Item it);
    public void setBias(double bias);
}
