package de.saar.coli.irtg.experimental.astar.heuristics;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import de.saar.basic.Pair;
import de.saar.coli.irtg.experimental.astar.EdgeProbabilities;
import de.saar.coli.irtg.experimental.astar.Item;
import de.saar.coli.irtg.experimental.astar.SupertagProbabilities;
import de.up.ling.irtg.signature.Interner;
import de.up.ling.irtg.util.CpuTimeStopwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RootAwareStaticEstimator implements OutsideEstimator {
    private final double[] bestEdgep;      // bestEdgep[k]   = max_{i,o} edgep[i][k][o]
    private final double[] bestTagp;       // bestTagp[k]    = max_s tagp[k][s]
    private final double[] outsideLeft;    // outsideLeft[k] = sum_{0 <= i < k} bestEdgep[i] + bestTagp[i]
    private final double[] outsideRight;   // outsideRight[k] = sum_{k <= i < n} bestEdgep[i] + bestTagp[i]
//    private final double[] worstIncomingLeft; // min score of the best incoming edges in 0...k
//    private final double[] worstIncomingRight; // min score of the best incoming edges in k...n

    private final double[] rootDiff;       // rootDiff[k] = bestEdgep[k] - max prob of incoming edge that is not ROOT; thus rootDiff[k] >= 0, and >0 iff best edge is ROOT
    private final double[] rootDiffLeft;   // rootDiffLeft[k] = max_{0 <= i < k} rootDiff[i]
    private final double[] rootDiffRight;  // rootDiffRight[k] = max_{k <= i < n} rootDiff[i]

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
        double v = left(it.getStart()) + right(it.getEnd()); // supertags and best incoming non-ROOT edges for the left and right context
        v += bestEdgep[it.getRoot()];

        // can now select one ROOT edge into either left context or item or right context;
        // add rootDiff for that edge
        double maxRootDiff = Math.max(rootDiffLeft[it.getStart()], rootDiff[it.getRoot()]);
        if( it.getEnd() <= N ) {
            maxRootDiff = Math.max(maxRootDiff, rootDiffRight[it.getEnd()]);
        }
        v += maxRootDiff;

        return v;
    }

    /**
     * Sums up the best supertags and best incoming edges for all tokens in the
     * range [start,end). This score is stored in onesidedOutsides[k].
     *
     * @param k
     * @param start
     * @param end
     * @param onesidedOutsides
     */
    private void sumContext(int k, int start, int end, double[] onesidedOutsides, double[] onesidedRootDiff) {
        double sum = 0;
        double maxRootDiff = 0;

        for (int i = start; i < end; i++) {
            double bestIncomingEdge = bestEdgep[i];  // best incoming edge score, including IGNORE and ROOT edges
            double bestSupertag = bestTagp[i];       // best supertag, including NULL
            double rootDiffHere = rootDiff[i];

            if( rootDiffHere > maxRootDiff ) {
                maxRootDiff = rootDiffHere;
            }

            sum += bestIncomingEdge + bestSupertag + bias;
        }

        onesidedOutsides[k] = sum;
        onesidedRootDiff[k] = maxRootDiff;
    }

    private Multiset<Integer> bestIncomingEdgeLabels = HashMultiset.create();

    public RootAwareStaticEstimator(EdgeProbabilities edgep, SupertagProbabilities tagp) {
        CpuTimeStopwatch w = new CpuTimeStopwatch();
        w.record();

        N = tagp.getLength();
        this.edgep = edgep;
        this.tagp = tagp;



        // calculate best incoming edge for each token >= 1
        bestEdgep = new double[N+1];
        rootDiff = new double[N+1];
        for (int k = 1; k <= N; k++) {
            bestEdgep[k] = edgep.getBestIncomingEdgeProbExceptWithLabel(k, edgep.getRootEdgeId()); // edgep.getBestIncomingProb(k);   // sum up best non-root edges
            rootDiff[k] = edgep.getBestIncomingProb(k) - bestEdgep[k]; // bonus for selecting ROOT, >= 0   // - edgep.getBestIncomingEdgeProbExceptWithLabel(k, edgep.getRootEdgeId());

            int bestEdgeLabel = edgep.getBestIncomingEdgeExceptWithLabel(k, edgep.getRootEdgeId()).left.getLabelId();
            bestIncomingEdgeLabels.add(bestEdgeLabel);
        }

        // calculate best supertag for each token >= 1
        bestTagp = new double[N+1];
        for (int k = 1; k <= N; k++) {
            bestTagp[k] = tagp.getMaxProb(k);
        }

        // calculate left-side outside estimates
        outsideLeft = new double[N+1];
        rootDiffLeft = new double[N+1];
        for (int k = 1; k <= N; k++) {
            sumContext(k, 1, k, outsideLeft, rootDiffLeft);
        }

        // calculate right-side outside estimates
        outsideRight = new double[N+1];
        rootDiffRight = new double[N+1];
        for (int k = 1; k <= N; k++) {
            sumContext(k, k, N, outsideRight, rootDiffRight);
        }

        w.record();


    }

    public void printTopEdges(Interner<String> edgeLabelLexicon) {
        List<Integer> edgeLabelIds = new ArrayList<>(bestIncomingEdgeLabels.elementSet());
        Collections.sort(edgeLabelIds, Comparator.comparing(x -> -bestIncomingEdgeLabels.count(x))); // sort descending by count
        for( int i = 0; i < 10; i++ ) {
            int edgeLabelId = edgeLabelIds.get(i);
            System.err.printf("%s: %d\n", edgeLabelLexicon.resolveId(edgeLabelId), bestIncomingEdgeLabels.count(edgeLabelId));
        }
    }

    // for debugging:
    // explain why the outside estimate for "it" is as it is
    public void analyze(Item it, Interner<String> supertagLexicon, Interner<String> edgeLabelLexicon) {
        assert edgeLabelLexicon != null;

        double sumSupertags = 0;
        double sumBestEdges = 0;

        for (int i = 1; i < it.getStart(); i++) {
            Pair<Integer, Double> tag = tagp.getBestSupertag(i);
            Pair<EdgeProbabilities.Edge, Double> edge = edgep.getBestIncomingEdge(i);
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
            Pair<EdgeProbabilities.Edge, Double> edge = edgep.getBestIncomingEdge(i);

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
