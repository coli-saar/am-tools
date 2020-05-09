/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar.heuristics;

import de.saar.coli.irtg.experimental.astar.EdgeProbabilities;
import de.saar.coli.irtg.experimental.astar.Item;
import de.saar.coli.irtg.experimental.astar.SupertagProbabilities;

/**
 *
 * @author koller
 */
public class DynamicOutsideEstimator implements OutsideEstimator {

    private final int N;
    private final EdgeProbabilities edgep;
    private final SupertagProbabilities tagp;

    private double[] bestTagp;       // bestTagp[k]    = max_s tagp[k][s] (for s != NULL)
    private double[] nullTagP;       // nullTagP[k] = tagp[k][NULL]

    @Override
    public double evaluate(Item it) {
        double score = 0;
        double worstEdge = 0;
        
        for( int i = 1; i < it.getStart(); i++ ) {
            double scoreHere = bestTagp[i];
            double bestEdge = edgep.getBestIncomingProbExceptIntoItemNonroot(i, it);
            
            if( scoreHere + bestEdge > nullTagP[i]) {
                score += scoreHere + bestEdge;
                worstEdge = Math.min(worstEdge, bestEdge);
            }
        }
        
        for( int i = it.getEnd(); i <= N; i++ ) {
            double scoreHere = bestTagp[i];
            double bestEdge = edgep.getBestIncomingProbExceptIntoItemNonroot(i, it);
            
            if( scoreHere + bestEdge > nullTagP[i]) {
                score += scoreHere + bestEdge;
                worstEdge = Math.min(worstEdge, bestEdge);
            }
        }
        
        double edgeIntoRoot = edgep.getBestIncomingProbExceptIntoItemNonroot(it.getRoot(), it);
        score += edgeIntoRoot;
        
        worstEdge = Math.min(worstEdge, edgeIntoRoot);
        score -= worstEdge;
        
        return score;
    }

    public DynamicOutsideEstimator(EdgeProbabilities edgep, SupertagProbabilities tagp) {
        this.N = tagp.getLength();              // sentence length
        this.edgep = edgep;
        this.tagp = tagp;

        // calculate best supertag for each token
        bestTagp = new double[N+1];
        for (int k = 1; k <= N; k++) {
            bestTagp[k] = tagp.getMaxProb(k);
        }
        
        // calculate NULL supertag scores
        nullTagP = new double[N+1];
        for( int k = 1; k <= N; k++ ) {
            nullTagP[k] = tagp.get(k, tagp.getNullSupertagId());
        }
    }

    @Override
    public void setBias(double bias) {
    }
}
