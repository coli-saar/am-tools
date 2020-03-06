/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import de.saar.basic.Pair;
import de.saar.coli.amrtagging.Alignment.Span;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra.Type;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.tree.ParseException;
import de.up.ling.tree.Tree;
import de.up.ling.tree.TreeBottomUpVisitor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Represents an aligned AM Dependency Tree where each node is a AmConllEntry,
 * i.e. essentially a tuple of (word, supertag + extra stuff). Uses intermediate
 * dummy nodes for edge labels.
 *
 * @author matthias
 */
public class AlignedAMDependencyTree {

    public static final String ALIGNED_SGRAPH_SEP = "@@";

    private Tree<AmConllEntry> tree;

    private AlignedAMDependencyTree() {
    }

    private AlignedAMDependencyTree(Tree<AmConllEntry> t) {
        this.tree = t;
    }

    public Tree<AmConllEntry> getTree() {
        return tree;
    }
    
    /**
     * Returns the AM dependency tree that is rooted in the given entry.
     * @param entry
     * @return 
     */
    public AlignedAMDependencyTree getSubtree(AmConllEntry entry){
        Tree<AmConllEntry> subtree = tree.dfs((Tree<AmConllEntry> currentSubtree, List<Tree<AmConllEntry>> list) -> {
            if (currentSubtree.getLabel().equals(entry)) {
                return currentSubtree;
            }
            for (Tree<AmConllEntry> subt : list){
                if (subt != null) return subt;
            }
            return null;
        });
        if (subtree == null) return null;
        return new AlignedAMDependencyTree(subtree);
    }

    /**
     * Makes a Tree out of the flat representation (AmConllSentence) that might
     * come from a file.
     *
     * @param sent
     * @return
     * @throws de.saar.coli.amrtagging.AlignedAMDependencyTree.ConllParserException
     */
    public static AlignedAMDependencyTree fromSentence(AmConllSentence sent) throws ConllParserException {
        ArrayList<ArrayList<Integer>> trees = new ArrayList();
        for (int i = 0; i < sent.size(); i++) {
            ArrayList<Integer> x = new ArrayList();
            trees.add(x);
        }
        int root = AmConllEntry.NOID;
        for (int i = 0; i < sent.size(); i++) {
            int head = sent.get(i).getHead();
            if (head == 0 && sent.get(i).getEdgeLabel().equals(AmConllEntry.ROOT_SYM)) {
                if (root != AmConllEntry.NOID) { //already assigned a root
                    throw new ConllParserException("Two roots for AmConllSentence. Line " + sent.getLineNr());
                }
                root = i;
            } else if (head != 0 && !sent.get(i).getEdgeLabel().equals(AmConllEntry.IGNORE)) { //ignore those things that are attached to the artifical root but don't have the root label and things with IGNORE label
                trees.get(head - 1).add(i);
            }
        }
        if (root == AmConllEntry.NOID) {
            //System.err.println(this);
            throw new ConllParserException("There seems to be no root for this AmConllSentence. Line " + sent.getLineNr());
        }
        
        AlignedAMDependencyTree ret = new AlignedAMDependencyTree(parse(sent, trees, root));
        return ret;
    }

    /**
     * A helper function for building the tree from the flat structure
     * AmConllSentence. Recursively builds the tree.
     *
     * @param t
     * @param i
     * @return
     */
    private static Tree<AmConllEntry> parse(AmConllSentence sent, ArrayList<ArrayList<Integer>> t, int i) {
        Tree<AmConllEntry> new_t = Tree.create(sent.get(i)); //root of this subtree

        for (int c : t.get(i)) {
            new_t = new_t.addSubtree(parse(sent, t, c));
        }
        return new_t;
    }

    /**
     * Evaluates the AM Dependency Tree (binarizes implictily). align tells if
     * the word position (and nodenames) of each constant are to be included
     * into the node labels so that the alignment information persists. See also
     * binarize. If sources (except root) remain, the corresponding nodes are
     * deleted (enforces empty type).
     *
     * @param align
     * @return
     * @throws ParserException
     * @throws ParseException
     */
    public SGraph evaluate(boolean align) throws ParserException, ParseException {
        return evaluateTermWithPostprocessing(binarize(align, true));
        /*
        ApplyModifyGraphAlgebra amAl = new ApplyModifyGraphAlgebra();
        SGraph g = amAl.evaluate(binarize(align, true)).getLeft();
        //now make sure to remove remaining source nodes
        List<String> sourceNodes = new ArrayList<String>();
        g.getAllSourceNodenames().forEach(sourceNodes::add);
        SGraph g_ = g.forgetSourcesExcept(Collections.singleton("root"));
        String rootNode = g_.getNodeForSource("root");
        for (String nodeName : sourceNodes){
            if (! nodeName.equals(rootNode)){
                g_.removeNode(nodeName);
            }
        }
        return g_;
         */
    }

    // AKAKAK
    public static SGraph evaluateTermWithPostprocessing(Tree<String> amTerm) {
        ApplyModifyGraphAlgebra amAl = new ApplyModifyGraphAlgebra();
        SGraph g = amAl.evaluate(amTerm).getLeft();

        // now make sure to remove remaining source nodes
        List<String> sourceNodes = new ArrayList<String>();
        g.getAllSourceNodenames().forEach(sourceNodes::add);

        SGraph g_ = g.forgetSourcesExcept(Collections.singleton("root"));
        String rootNode = g_.getNodeForSource("root");

        for (String nodeName : sourceNodes) {
            if (!nodeName.equals(rootNode)) {
                g_.removeNode(nodeName);
            }
        }

        return g_;
    }

    public SGraph evaluateWithoutRelex(boolean align) throws ParserException, ParseException {
        ApplyModifyGraphAlgebra amAl = new ApplyModifyGraphAlgebra();
        SGraph g = amAl.evaluate(binarize(align, false)).getLeft();
        //now make sure to remove remaining source nodes
        List<String> sourceNodes = new ArrayList<String>();
        g.getAllSourceNodenames().forEach(sourceNodes::add);
        SGraph g_ = g.forgetSourcesExcept(Collections.singleton("root"));
        String rootNode = g_.getNodeForSource("root");
        for (String nodeName : sourceNodes) {
            if (!nodeName.equals(rootNode)) {
                g_.removeNode(nodeName);
            }
        }
        return g_;
    }
    
    /**
     * Returns the type of the AM term corresponding to this AM dependency tree.
     * @return 
     */
    public ApplyModifyGraphAlgebra.Type evaluateType(){
        ApplyModifyGraphAlgebra amAl = new ApplyModifyGraphAlgebra();
        try {
            return amAl.evaluate(binarize(false, false)).getRight();
        } catch (ParserException ex) {
            Logger.getLogger(AlignedAMDependencyTree.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException ex) {
            Logger.getLogger(AlignedAMDependencyTree.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    /**
     * Returns the type of the AM term that corresponds to the AM dependency tree that is rooted in entry. Returns null if entry is not part of the AM dependency tree (also when it has no semantic contribution).
     * @param entry
     * @return 
     */
    public ApplyModifyGraphAlgebra.Type getTermTypeAt(AmConllEntry entry){
        AlignedAMDependencyTree subt = this.getSubtree(entry);
        if (subt == null) return null;
        return subt.evaluateType();
    }
            

    /**
     * Extracts the alignments from an S-graph that was just produced by
     * invoking evaluate with align=true. Indexing happens on words. Beware: for
     * now, it does not restore lexical nodes (although that's possible with
     * some restructuring)!!!
     *
     * @param sg
     * @return
     */
    public static List<Alignment> extractAlignments(SGraph sg) {
        HashMap<Span, HashSet<String>> s2n = new HashMap<>();
        for (String nodeName : sg.getAllNodeNames()) {
            Pair<Integer, Pair<String, String>> infos = decodeNode(sg.getNode(nodeName));
            int pos = infos.left;
            Span thisSpan = new Span(pos - 1, pos);
            HashSet<String> nodes = s2n.getOrDefault(thisSpan, new HashSet<>());
            nodes.add(nodeName);
            s2n.put(thisSpan, nodes);
        }
        List<Alignment> als = new ArrayList<>();
        for (Span s : s2n.keySet()) {
            als.add(new Alignment(s2n.get(s), s));
        }
        return als;
    }

    /**
     * Strips off the alignment info of the node labels. Useful in conjunction
     * with extractAlignments, so we have a clean graph and clean alignments.
     *
     * @param sg
     */
    public static void stripAlignments(SGraph sg) {
        for (String nodeName : sg.getAllNodeNames()) {
            Pair<Integer, Pair<String, String>> infos = decodeNode(sg.getNode(nodeName));
            String origLabel = infos.right.right;
            sg.getNode(nodeName).setLabel(origLabel);
        }
    }

    /**
     * Returns an isomorphic tree where the nodes in the tree contain the node
     * names of the graph that belong to the constant at the node in the
     * tree.This function basically tells us, at which position in the tree any
     * node of the graph was introduced. This must be called before
     * stripAlignments
     *
     * @param sg
     * @param s2n an alignment from token spans to nodes (generated by
     * extractAlignments)
     * @return
     */
    public Tree<Set<String>> isomorphicTreeWithNodeNames(SGraph sg, List<Alignment> s2n) {
        return this.getTree().dfs(new TreeBottomUpVisitor<AmConllEntry, Tree<Set<String>>>() {
            @Override
            public Tree<Set<String>> combine(Tree<AmConllEntry> treeNode, List<Tree<Set<String>>> childrenValues) {
                int id = treeNode.getLabel().getId();
                Set<String> nodeNames = new HashSet<>();
                for (Alignment al : s2n) {
                    if (al.span.end == id) { //the current node id equals the position in the sentence, just copy the aligned nodes
                        nodeNames.addAll(al.nodes);
                        break;
                    }
                }
                return Tree.create(nodeNames, childrenValues);
            }
        });

    }

    /**
     * Binarizes an AM Dependency Tree to some AM Term that can then be used for
     * evaluation.Based on the fixed tree decoder.align tells if the word
     * position (and nodenames) of each constant are to be included into the
    * node labels so that the alignment information persists. If align=true,
    * the format of the node labels is as follows: wordId SEP nodeName SEP
    * oldNodeLabel, e.g. node u at position 12 with label "Frankenstein"
    * becomes 12@@u@@Frankenstein.
     *
     * @param align
     * @param relex
     * @return an AM term
     * @throws ParserException
     * @throws ParseException
     */
    public Tree<String> binarize(boolean align, boolean relex) throws ParserException, ParseException {
        Stack<Stack<Item<String>>> agenda = new Stack();
        HashMap<Integer, Item<String>> chart = new HashMap();
        //HashSet<Item<String>> visited = new HashSet();

        HashMap<Pair<Integer, Integer>, String> allowedEdges = new HashMap(); //[head,dependent] -> edge label
        Map<Tree<AmConllEntry>, Tree<AmConllEntry>> parentMap = tree.getParentMap();

        int rootIndex = tree.getLabel().getId();
        
        if (tree.getLabel().getType() == null){
            // \bot was selected as supertag for the root.
            throw new IllegalArgumentException("AM dependency tree has type \\bot (meaning no semantic contribution) at the root and thus doesn't represent a graph.");
        }

        for (AmConllEntry leaf : tree.getLeafLabels()) {
            String s;
            SGraph sg;

            if (relex) {
                sg = leaf.relex();
            } else {
                sg = leaf.delexGraph();
            }

            if (align) {
                encodeNodenames(sg, leaf.getId());
                s = sg.toIsiAmrStringWithSources();
            } else {
                s = sg.toIsiAmrStringWithSources();
            }
            
            Tree<String> t = Tree.create(s + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + leaf.getType().toString());
            Item<String> it = new Item<>(leaf.getId(), new HashSet<>(), leaf.getType(), t);
            chart.put(leaf.getId(), it);
        }

        //System.err.println("Chart "+chart);
        for (Tree<AmConllEntry> subt : tree.getAllNodes()) { //all nodes in pre-order!
            if (!subt.equals(tree)) { //not at the root, add edges
                int parentId = parentMap.get(subt).getLabel().getId();
                allowedEdges.put(new Pair<>(subt.getLabel().getHead(), subt.getLabel().getId()), subt.getLabel().getEdgeLabel());
            }
            if (!subt.getChildren().isEmpty()) { //inner node with graph constant (not a leaf)

                HashSet<Integer> childIdx = new HashSet();
                for (Tree<AmConllEntry> c : subt.getChildren()) {
                    childIdx.add(c.getLabel().getId());
                }

                Stack<Item<String>> h = new Stack();
                String s;
                SGraph sg;
                if (relex) {
                    sg = subt.getLabel().relex();
                } else {
                    sg = subt.getLabel().delexGraph();
                }
                if (align) {
                    encodeNodenames(sg, subt.getLabel().getId());
                    s = sg.toIsiAmrStringWithSources();
                } else {
                    s = sg.toIsiAmrStringWithSources();
                }
                h.push(new Item(subt.getLabel().getId(), childIdx, subt.getLabel().getType(), Tree.create(s + ApplyModifyGraphAlgebra.GRAPH_TYPE_SEP + subt.getLabel().getType().toString())));
                agenda.add(h);
            }
        }
        //System.err.println("Agenda "+agenda);

        while (!agenda.empty()) {
            Stack<Item<String>> h = agenda.pop();

            collect_children:
            while (!h.empty()) {
                Item<String> curr = h.pop();

                //if (visited.contains(curr)) continue; //skip //how large is the impact on performance? Probably small.
                //visited.add(curr);
                List<Integer> toBeProcessed = curr.unprocessed.stream().collect(Collectors.toList());
                Collections.shuffle(toBeProcessed); //this is weird, but the decoder has an exponential worst case runtime. If it doesn't finish, just restart, hopefully you're lucky.
                for (int dep_i : toBeProcessed) {
                    String operation = allowedEdges.get(new Pair(curr.index, dep_i));
                    Item<String> dep = chart.get(dep_i);
                    if (dep == null) throw new IllegalArgumentException("Couldn't find a binarization of AMDependencyTree. Couldn't find chart entry for token "+dep_i+". Perhaps different parts of your pipeline use different versions of the AM algebra?");
                    Item<String> deduced = null;
                    //System.err.println("Combine "+curr+" with " + dep); 
                    if (operation.contains(ApplyModifyGraphAlgebra.OP_APPLICATION)) {
                        String source = getAppSource(operation);
                        if (curr.tau.canApplyTo(dep.tau, source)) { //APP is allowed
                            HashSet<Integer> copied = (HashSet<Integer>) curr.unprocessed.clone();
                            copied.remove(dep_i);
                            ArrayList<Tree<String>> children = new ArrayList();
                            children.add(curr.term);
                            children.add(dep.term);
                            deduced = new Item<>(curr.index, copied, curr.tau.performApply(source), Tree.create(operation, children));

                        }
                    } else if (operation.contains(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                        if (curr.tau.canBeModifiedBy(dep.tau, operation.substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length()))) {
                            HashSet<Integer> copied = (HashSet<Integer>) curr.unprocessed.clone();
                            copied.remove(dep_i);
                            ArrayList<Tree<String>> children = new ArrayList();
                            children.add(curr.term);
                            children.add(dep.term);
                            deduced = new Item<>(curr.index, copied, curr.tau, Tree.create(operation, children));
                        }
                    } else {
                        throw new IllegalArgumentException("Unknown operation used at node with Edge " + curr.index + "-->" + dep_i);
                    }

                    if (deduced != null) {
                        if (deduced.isComplete()) {
                            chart.put(deduced.index, deduced);
                            break collect_children;
                        } else {
                            h.push(deduced);
                        }
                    }
                }

            }

        }
        if (chart.containsKey(rootIndex)) {
            return chart.get(rootIndex).term;
        } else {
            throw new IllegalArgumentException("Couldn't find a binarization of AMDependencyTree");
        }
    }

    /**
     * Encodes the id (position in Sentence) and the node names of non-source
     * nodes into the node label.
     *
     * @param sg
     * @param id
     */
    private static void encodeNodenames(SGraph sg, int id) {
        for (String nodeName : sg.getAllNodeNames()) {
            if (sg.getNode(nodeName) != null && sg.getNode(nodeName).getLabel() != null) {
                sg.getNode(nodeName).setLabel(id + ALIGNED_SGRAPH_SEP + nodeName + ALIGNED_SGRAPH_SEP + sg.getNode(nodeName).getLabel());
            }
        }
    }

    /**
     * Undoes the encoding of node names in the label. Returns a "triple":
     * (position, node name, original node label)
     *
     * @param n
     * @return
     */
    public static Pair<Integer, Pair<String, String>> decodeNode(GraphNode n) {
        String[] inf = n.getLabel().split(ALIGNED_SGRAPH_SEP);
        return new Pair<>(Integer.valueOf(inf[0]), new Pair<>(inf[1], inf[2]));
    }

    public static class ConllParserException extends Exception {

        private ConllParserException(String string) {
            super(string);
        }

    }

    /* Helper functions */
    private static String getAppSource(String label) {
        return label.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
    }

    private static String getModSource(String label) {
        return label.substring(ApplyModifyGraphAlgebra.OP_MODIFICATION.length());
    }

    /**
     * An Item for "Parsing" during the binarization step.
     *
     * @param <U>
     */
    private class Item<U> {

        public int index;
        public HashSet<Integer> unprocessed;
        public Type tau;
        public Tree<U> term;

        public Item(int index, HashSet<Integer> pr, Type tau, Tree<U> term) {
            this.index = index;
            this.unprocessed = pr;
            this.tau = tau;
            this.term = term;
        }

        public boolean isComplete() {
            return this.unprocessed.isEmpty();
        }

        public boolean equals(Item<U> other) {
            return (this.index == other.index && this.unprocessed.equals(other.unprocessed) && this.tau.equals(other.tau));
        }

        @Override
        public String toString() {
            return "<" + this.index + "," + this.unprocessed + "," + this.tau + ">";
        }

    }

}
