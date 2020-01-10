/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.irtg.experimental.astar;

import de.saar.basic.Pair;
import de.saar.coli.irtg.experimental.astar.EdgeProbabilities.Edge;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.CpuTimeStopwatch;

/**
 * An outside estimator that sums up the best supertag scores and the best
 * scores for incoming edges over all tokens outside of the item.
 *
 * @author koller
 */
public class StaticOutsideEstimator implements OutsideEstimator {

    private final double[] bestEdgep;      // bestEdgep[k]   = max_{i,o} edgep[i][k][o]
    private final double[] bestTagp;       // bestTagp[k]    = max_s tagp[k][s]
    private final double[] outsideLeft;    // outsideLeft[k] = sum_{0 <= i < k} bestEdgep[i] + bestTagp[i]
    private final double[] outsideRight;   // outsideRight[k] = sum_{k <= i < n} bestEdgep[i] + bestTagp[i]
    private final double[] worstIncomingLeft; // min score of the best incoming edges in 0...k
    private final double[] worstIncomingRight; // min score of the best incoming edges in k...n

    private final int N;
    private final EdgeProbabilities edgep;
    private final SupertagProbabilities tagp;

    private double bias = 0;

    /**
     * Adds a bias to the heuristic. This makes the heuristic inadmissible, but
     * may speed up the A* parser. Note: This bias is currently NOT just added
     * to each estimate, but to the contribution to the estimate for each token
     * in the left and right context.
     *
     * @param bias
     */
    @Override
    public void setBias(double bias) {
        this.bias = bias;
    }

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
        // TODO plus best edge into root of item?
        v += bestEdgep[it.getRoot()]; 
        return v;
        
        
//        
//        v += bestEdgep[it.getRoot()];                        // plus best edge into root of item
//
//        double worstIncomingEdgeScore = Math.min(worstIncomingLeft[it.getStart()], Math.min(worstIncomingRight[it.getEnd()], bestEdgep[it.getRoot()])); // worst edge into left, right, or root
//        double ret = v - worstIncomingEdgeScore; // can skip one of the incoming edges by making its target node root
//
//        return ret;
    }

    /**
     * Sums up the best supertags and best incoming edges for all tokens in the
     * range [start,end). This score is stored in onesidedOutsides[k]. 
     * 
     * 
     * NO LONGER TRUE: Also
     * discovers the worst-scored among these best incoming edges, and stores
     * its score in onesidedWorstIncoming[k].
     *
     * @param k
     * @param start
     * @param end
     * @param onesidedOutsides
     * @param onesidedWorstIncoming
     */
    private void sumContext(int k, int start, int end, double[] onesidedOutsides, double[] onesidedWorstIncoming) {
        double sum = 0;
//        double worst = 0;  // score of worst best in-edge

        for (int i = start; i < end; i++) {
            double bestIncomingEdge = bestEdgep[i];  // best incoming edge score, including IGNORE and ROOT edges
            double bestSupertag = bestTagp[i];       // best supertag, including NULL
            // TODO more fine-grained interaction of IGNORE and NULL
            
            sum += bestIncomingEdge + bestSupertag + bias;
        }
            
//            
//            
//            double scoreWithInEdge = bestTagp[i] + bestEdgep[i];
//            double scoreWithIgnore = tagp.get(i, tagp.getNullSupertagId()); // NULL score
//
//            System.err.printf("[%d] bestTagp=%f, bestEdgep=%f, NULL=%f\n", i, bestTagp[i], bestEdgep[i], scoreWithIgnore);
//
//            // AKAKAK should scoreWithIgnore also contain substitutionCost of IGNORE in-edge?
//            // #951: Token 4 doesn't have an in-edge or a NULL supertag, therefore both scores are -INF
//            assert Math.max(scoreWithInEdge, scoreWithIgnore) > Astar.FAKE_NEG_INFINITY / 2 : String.format("No good supertag or in-edge for pos %d (while computing context scores for pos %d): withInEdge=%f, withIgnore=%f\n", i, k, scoreWithInEdge, scoreWithIgnore);
//
//            if (scoreWithInEdge > scoreWithIgnore) {
//                sum += scoreWithInEdge;
//                worst = Math.min(worst, bestEdgep[i]);
//            } else {
//                sum += scoreWithIgnore;
//            }
//
//            sum += this.bias;
//        }

        onesidedOutsides[k] = sum;
//        onesidedWorstIncoming[k] = worst;
    }

    public StaticOutsideEstimator(EdgeProbabilities edgep, SupertagProbabilities tagp) {
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        w.record();

        N = tagp.getLength();
        this.edgep = edgep;
        this.tagp = tagp;

        // calculate best incoming edge for each token >= 1
        bestEdgep = new double[N+1];
        for (int k = 1; k <= N; k++) {
            bestEdgep[k] = edgep.getBestIncomingProb(k);
        }

        // calculate best supertag for each token >= 1
        bestTagp = new double[N+1];
        for (int k = 1; k <= N; k++) {
            bestTagp[k] = tagp.getMaxProb(k);
        }
        
        // calculate left-side outside estimates
        outsideLeft = new double[N+1];
        worstIncomingLeft = new double[N+1];
        for (int k = 1; k <= N; k++) {
            sumContext(k, 1, k, outsideLeft, worstIncomingLeft);
        }

        // for (double elem : outsideLeft) {
        //     System.err.println(elem);
        // }

        // System.err.println("");

        // calculate right-side outside estimates
        outsideRight = new double[N+1];
        worstIncomingRight = new double[N+1];
        for (int k = 1; k <= N; k++) {
            sumContext(k, k, N, outsideRight, worstIncomingRight);
        }
        // for (double elem : outsideRight) {
        //     System.err.println(elem);
        // }

        w.record();
//        w.printMilliseconds("initialize outside estimator");
    }

    // for debugging:
    // explain why the outside estimate for "it" is as it is
    public void analyze(Item it, Interner<String> supertagLexicon, Interner<String> edgeLabelLexicon) {
        assert edgeLabelLexicon != null;

        double sumSupertags = 0;
        double sumBestEdges = 0;

        for (int i = 1; i < it.getStart(); i++) {
            Pair<Integer, Double> tag = tagp.getBestSupertag(i);
            Pair<Edge, Double> edge = edgep.getBestIncomingEdge(i);
            assert edge != null;

            String edgeLabel = edgeLabelLexicon.resolveId(edge.left.getLabelId());
            assert edgeLabel != null;

            System.err.printf("[%2d] best supertag: %d %s (%f) // best in-edge: %s from %d (%f)\n",
                    i,
                    tag.left, supertagLexicon.resolveId(tag.left), tag.right,
                    edgeLabel, edge.left.getFrom(), edge.right);

            sumSupertags += tag.right;
            sumBestEdges += edge.right;
        }

        System.err.printf(">> %s %f\n", it.shortString(), it.getLogProb());

        for (int i = it.getEnd(); i < N; i++) {
            Pair<Integer, Double> tag = tagp.getBestSupertag(i);
            Pair<Edge, Double> edge = edgep.getBestIncomingEdge(i);

            if (edge == null || edge.left == null) {
                System.err.printf("[%2d] best supertag: %d %s (%f) // no in-edge\n",
                        i,
                        tag.left, supertagLexicon.resolveId(tag.left), tag.right);
            } else {
                System.err.printf("[%2d] best supertag: %d %s (%f) // best in-edge: %s from %d (%f)\n",
                        i,
                        tag.left, supertagLexicon.resolveId(tag.left), tag.right,
                        edgeLabelLexicon.resolveId(edge.left.getLabelId()), edge.left.getFrom(), edge.right);
            }

            sumSupertags += tag.right;
            sumBestEdges += edge.right;
        }

        System.err.printf("\nSum outside tag scores=%f, edge scores=%f, total=%f\n", sumSupertags, sumBestEdges, evaluate(it));
    }
}
