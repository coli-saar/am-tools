/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.up.ling.irtg.util.CpuTimeStopwatch;

/**
 *
 * @author koller
 */
public class SupertagOnlyOutsideEstimator implements OutsideEstimator {

    private double[] bestTagp;       // bestTagp[k]    = max_s tagp[k][s]
    private double[] outsideLeft;    // outsideLeft[k] = sum_{0 <= i < k} bestEdgep[i] + bestTagp[i]
    private double[] outsideRight;   // outsideRight[k] = sum_{k <= i < n} bestEdgep[i] + bestTagp[i]

    private final int N;
    private final SupertagProbabilities tagp;

    private double left(int i) {
        return outsideLeft[i];
    }

    private double right(int i) {
        if (i >= N) {
            return 0;
        } else {
            return outsideRight[i];
        }
    }

    /**
     * Returns an outside estimate of the given item.
     *
     * @param it
     * @return
     */
    @Override
    public double evaluate(Item it) {

        double v = left(it.getStart()) + right(it.getEnd()); // supertags and best incoming edges for the left and right context
//        v += bestEdgep[it.getRoot()];                        // plus best edge into root of item

//        double worstIncomingEdgeScore = Math.min(worstIncomingLeft[it.getStart()], Math.min(worstIncomingRight[it.getEnd()], bestEdgep[it.getRoot()]));

//        System.err.printf("\nevaluate %s: base v=%f (left=%f, right=%f, root_edge=%f\n", it.toString(), v, left(it.getStart()), right(it.getEnd()), bestEdgep[it.getRoot()]);
//        System.err.printf("worst incoming edge scores: left=%f, right=%f, root=%f, worst=%f\n", worstIncomingLeft[it.getStart()], worstIncomingRight[it.getEnd()], bestEdgep[it.getRoot()], worstIncomingEdgeScore);
//        double ret = v - worstIncomingEdgeScore; // can skip one of the incoming edges by making its target node root

        // TODO - this really only makes sense if the item decided to assign a non-NULL supertag to the item root.
        // Subtracting the best incoming edge is optimistic, so heuristic should still be admissible.
        // But we should get a tighter bound at some point.
//        System.err.printf("  eval %s -> outside est=%f\n", it, ret);
        return v;
    }
    


    private void sumContext(int k, int start, int end, double[] onesidedOutsides) {
        double sumBias = -0; //bias to speed up the process. Set to 0 for normal A* parsing. Set to -0.5 to reproduce bug
        double sum = 0;
//        double worst = 0;  // score of worst best in-edge

        for (int i = start; i < end; i++) {
            double scoreWithInEdge = bestTagp[i];
            double scoreWithIgnore = tagp.get(i, tagp.getNullSupertagId()); // NULL score
            double bestScore = Math.max(scoreWithIgnore, scoreWithInEdge);

            if (bestScore < Astar.FAKE_NEG_INFINITY / 2) {
                System.err.printf("No good supertag for pos %d (while computing left scores for pos %d): withInEdge=%f, withIgnore=%f\n", i, k, scoreWithInEdge, scoreWithIgnore);
                System.exit(1);
            }

//                System.err.printf("  left @%d: score from i=%d with_ignore=%f, with_in_edge=%f\n", k, i, scoreWithIgnore, scoreWithInEdge);

            sum += bestScore;
            
//            
//            if (scoreWithInEdge > scoreWithIgnore) {
//                sum += scoreWithInEdge;
//            } else {
//                sum += scoreWithIgnore;
//                
//            }

            sum += sumBias;//add a bias to speed up the process
        }

        onesidedOutsides[k] = sum;
    }

    public SupertagOnlyOutsideEstimator(SupertagProbabilities tagp) {
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        w.record();
        
        N = tagp.getLength();              // sentence length
        this.tagp = tagp;


        // calculate best supertag for each token
        bestTagp = new double[N];
        for (int k = 1; k < N; k++) {
            bestTagp[k] = tagp.getMaxProb(k);
//            System.err.printf("best tag @%d: %f\n", k, bestTagp[k]);
        }

        // calculate left-side outside estimates
        outsideLeft = new double[N];
        for (int k = 1; k < N; k++) {
            sumContext(k, 1, k, outsideLeft);
        }

        // calculate right-side outside estimates
        outsideRight = new double[N + 1];
        for (int k = 1; k <= N; k++) {
            sumContext(k, k, N, outsideRight);
        }
        
        w.record();
//        w.printMilliseconds("initialize outside estimator");
    }

    @Override
    public void setBias(double bias) {

    }
}
