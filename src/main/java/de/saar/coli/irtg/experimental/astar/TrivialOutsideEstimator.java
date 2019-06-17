/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

/**
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
