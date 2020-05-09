/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar.heuristics;

import de.saar.coli.irtg.experimental.astar.Item;

/**
 * An outside estimator which estimates the outside score
 * of each span as zero.
 *
 * @author koller
 */
public class TrivialOutsideEstimator implements OutsideEstimator {
    @Override
    public double evaluate(Item it) {
        return 0;
    }

    @Override
    public void setBias(double bias) {

    }
}
