package de.saar.coli.amtools.decomposition.deterministic_sources;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Util;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.*;

/**
 * Source assigner based on UD syntax dependency scores provided by the AM parser.
 */
public class SyntaxSourceAssigner implements SourceAssigner {

    private final Map<IntSet, String> bestLabels;

    public SyntaxSourceAssigner(List<Pair<String, Double>> syntaxEdgeLabelScores) {
        bestLabels = new HashMap<>();
        // find best labels from the given scores
        Map<IntSet, Double> bestScorePerEdge = new HashMap<>();
        for (Pair<String, Double> edgeAndScore : syntaxEdgeLabelScores) {
            Pair<String, Pair<Integer, Integer>> labelSourceTarget = Util.edgeString2Edge(edgeAndScore.left);
            IntSet nodes = new IntOpenHashSet();
            nodes.add(labelSourceTarget.right.left.intValue());// use intValue to avoid deprecated IntList add.
            nodes.add(labelSourceTarget.right.right.intValue());
            if (edgeAndScore.right > bestScorePerEdge.getOrDefault(nodes, 0.0)) {
                bestLabels.put(nodes, syntaxRole2Source(labelSourceTarget.left));
                bestScorePerEdge.put(nodes, edgeAndScore.right);
            }
        }
    }

    private static String syntaxRole2Source(String syntaxRole) {
        //splits off the very finegrained distinction after the colon in UD labels
        //TODO this could be made even less fine-grained (i.e. mapping several similar syntax labels to the same source).
        return syntaxRole.split(":")[0];
    }


    @Override
    public String getSourceName(int parent, int child, String operation) {
        IntSet set = new IntOpenHashSet();
        set.add(parent);
        set.add(child);
        return bestLabels.getOrDefault(set, "NULL");
    }
}
