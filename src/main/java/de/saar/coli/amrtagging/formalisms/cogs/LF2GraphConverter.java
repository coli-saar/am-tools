package de.saar.coli.amrtagging.formalisms.cogs;

import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.MRInstance;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.Argument;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.Term;
import org.apache.commons.lang.NotImplementedException;

import java.util.*;

/**
 * Converts <code>COGSLogicalForm</code> to a <code>SGraph</code><br>
 *
 * Version 1: very AMR-like<br>
 * - arguments of a term (<i>x_i, John, a</i>) become nodes<br>
 * - predicate names become edges unless it's a term with only one argument (<i>boy(x_1)</i>), then it's part of the <i>x_i</i> node
 * - iota: we treat iota as some special term: <i>* boy(x_1);</i> transformed to <i>the.iota(the, x_1_boy)</i><br>
 * - prepositions: the <i>nmod.preposition</i> edge belongs to the noun of the PP (not the modified noun!)<br>
 * - primitives: treated as graphs with open sources...<br>
 * TODO: missing implementation:
 * - Alignment: is is 0-indexed or 1-indexed? currently assumes 0-indexed. Check what Alignment wants and maybe change..
 * - refactoring (is there duplicate code or very similar code that could be a method on its own?)
 * - what is a node name (not label) for proper names? need to recover in postprocessing something?
 * TODO: Problems
 * - alignments for determiners and proper names rely on heuristics and hope (see to-do-notes below)
 * - same would hold for prepositions, but the current encoding transforms them to edges (only nodes need alignments)
 * - for non-primitives we have to heuristically select a root node (heuristic: no incoming edges)
 * @author piaw (weissenh)
 * Created April 2021
 */
public class LF2GraphConverter {
    public static final String LEMMA_SEPARATOR = "~~";  // "x_1~~boy", "x_4~~want", "x_e~~giggle", "x_Ava~~Ava"
    public static final String IOTA_EDGE_LABEL = "iota";
    public static final String IOTA_NODE_LABEL = "the";
    public static final String NODE_NAME_PREFIX = "x_";

    /// Method for converting 1-word primitive to an SGraph (plus alignments and sentence tokens) eg. `Ava\tAva`
    private static MRInstance NameToSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        assert(sentenceTokens.size() == 1);
        List<Alignment> alignments = new ArrayList<>();
        SGraph graph = new SGraph();
        Argument propername = logicalForm.getNamePrimitive();
        // ** Graph: add node with the proper name as label and make it the root
        GraphNode node = graph.addNode(NODE_NAME_PREFIX+"0", propername.getName()); // todo what about lemma? needed?
        // TODO at first ,this node was an anonymous one, but that lead to NullPointerException:
        // GraphNode node = graph.addAnonymousNode(propername.getName());
        graph.addSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME, node.getName());
        // ** Alignments: align to first and only word in the sentence
        alignments.add(new Alignment(node.getName(), 0));
        return new MRInstance(sentenceTokens, graph, alignments);
    }

    /// Method to convert primitive with lambdas to an SGraph (plus alignments and sentence tokens)
    private static MRInstance LambdaToSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        // e.g.    hold   LAMBDA a . LAMBDA b . LAMBDA e . hold . agent ( e , b ) AND hold . theme ( e , a )
        assert(logicalForm.getFormulaType() == COGSLogicalForm.AllowedFormulaTypes.LAMBDA);
        assert(sentenceTokens.size() == 1);
        List<Alignment> alignments = new ArrayList<>();
        SGraph graph = new SGraph();
        // ** Graph
        // - node for each lambda variable
        Set<Argument> lambdaargs = logicalForm.getArgumentSet();
        for (Argument arg: lambdaargs) { graph.addNode(NODE_NAME_PREFIX+arg.getName(), null); }
        // * need to find which node 'aligns' with the lemma (e.g. `e` as it is the first argument in each term):
        //  this becomes the root node (and latter also lemma added to the node label)
        Argument lexarg = logicalForm.getLexicalArgumentForLambda();
        GraphNode lexicalNode = graph.getNode(NODE_NAME_PREFIX+lexarg.getName());
        String lexnodename = lexicalNode.getName();
        graph.addSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME, lexnodename);
        // * for each predicate (if binary) add edge
        //   on the target node of that edge we add a source (=lambda variable!)
        String lemma = null;
        for (Term t: logicalForm.getAllTerms()) {
            String tmp = t.getPredicate().getLemma();
            if (lemma == null) { lemma = tmp;}
            else { assert(lemma.equals(tmp)); }
            if (t.hasTwoArguments()) {
                Argument firstArg = t.getArguments().get(0);
                GraphNode firstNode = graph.getNode(NODE_NAME_PREFIX+firstArg.getName());
                Argument secondArg = t.getArguments().get(1);
                GraphNode secondNode = graph.getNode(NODE_NAME_PREFIX+secondArg.getName());
                String label = t.getPredicate().getDelexPredAsString();
                // todo assert(term.pred.lemma == firstArgNode lemma)
                graph.addEdge(firstNode, secondNode, label);
                // we also know that the target Node should contain a source (lambda variable as source!!)
                graph.addSource(secondArg.getName(), secondNode.getName());
            }
        }
        // * add the lemma to the lexical(= root) node
        assert(lemma != null);  // should have seen at least one term
        lexicalNode.setLabel(lexarg.getName()+LEMMA_SEPARATOR+lemma);
        // ** Alignments
        //  We align all nodes (the 'lemma' node and even the unlabeled nodes with just sources) to the token.
        //  There is only one word in the input, so at position 0.
        for (String nodename: graph.getAllNodeNames()) {
            alignments.add(new Alignment(nodename, 0));
        }
        return new MRInstance(sentenceTokens, graph, alignments);
    }

    // todo giant method: refactor into smaller ones if possible? (maybe together with lambdatosgraph)?
    /// Method to convert non-primitive logical form (>= 0 terms as prefix) to SGraph (plus alignments and sentence)
    private static MRInstance IotaToSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        assert (logicalForm.getFormulaType() == COGSLogicalForm.AllowedFormulaTypes.IOTA);
        assert (sentenceTokens.size() > 0);
        List<Alignment> alignments = new ArrayList<>();
        SGraph graph = new SGraph();
        // while building the graph we take note of nodes which can't be root nodes (why? see further below)
        Set<GraphNode> notCandidatesForRoot = new HashSet<>();
        // ** Graph
        // * node for each distinct argument (including proper names!)
        for (Argument arg: logicalForm.getArgumentSet()) {
            GraphNode n = graph.addNode(NODE_NAME_PREFIX+arg.getName(), null); // indices don't receive a label (yet)
            if (arg.isProperName()) { n.setLabel(arg.getName());}  // but proper names do
            assert(!arg.isLambdaVar()); // we are in a non-primtive, there clearly shouldn't be lambda variables
        }
        // * iotas:
        /* - node for each iota (node has label "the")
         * - iota edge (label: "iota")
         * - label the noun node with the noun lemma
         * - align the noun node according to the index
         * - neither ne noun node nor the determiner node can be candidates for the root node
         */
        GraphNode nounNode;
        for (Term t: logicalForm.getPrefixTerms()) {  // for each iota term
            // get the 'noun' node
            assert(t.getValency()==1);  // not binary predicate, but unary one
            Argument nounArgument = t.getArguments().get(0);
            assert(nounArgument.isIndex());
            nounNode = graph.getNode(NODE_NAME_PREFIX+nounArgument.getName());
            // add lemma to the 'noun' node
            assert(nounNode.getLabel() == null);
            nounNode.setLabel(t.getLemma());
            // add alignment for the 'noun' node
            alignments.add(new Alignment(nounNode.getName(), nounArgument.getIndex()));
            // add new determiner node
            int detindx = nounArgument.getIndex()-1;
            GraphNode determinerNode = graph.addNode(NODE_NAME_PREFIX+detindx, IOTA_NODE_LABEL);
            // TODO at first ,this node was an anonymous one, but that lead to a NullPointerException
            // GraphNode determinerNode = graph.addAnonymousNode(IOTA_NODE_LABEL);
            // todo for future extensions this heuristic may become a problem:
            /* How to align the determiner node?
             * - the logical form doesn't specify (no index for it!) which token it belongs to
             * - also: there can be more than one definite determiner in a sentence...
             * - our simple heuristic: we align the determiner node to the token *right before* the noun.
             * - IMPORTANT: we can only use this heuristic faithfully because we know that the COGS dataset
             *   doesn't contain any pre-nominal modifiers like adjectives
             * */
            alignments.add(new Alignment(determinerNode.getName(), detindx));
            // add an iota-edge
            graph.addEdge(determinerNode, nounNode, IOTA_EDGE_LABEL);
            // the determiner node isn't going to be the root
            // the noun is also not a root (should be a verb, right?) todo check this assumption?
            notCandidatesForRoot.add(determinerNode);
            notCandidatesForRoot.add(nounNode);
        }
        // * add lemma as a label for each node corresponding to the first argument in some term
        // * edge for each term excluding unary
        // * target nodes of edges can't be candiates for the root node (root node should have no incoming edges)
        GraphNode lemmaNode;
        for (Term t: logicalForm.getConjunctionTerms()) {
            // set lemma for the source node
            Argument firstArg = t.getArguments().get(0);
            assert(firstArg.isIndex());
            lemmaNode = graph.getNode(NODE_NAME_PREFIX+firstArg.getName());
            assert(lemmaNode.getLabel() == null || lemmaNode.getLabel().equals(t.getLemma()));
            // if node doesn't have this lemma label already: add it and also add alignment
            if (lemmaNode.getLabel() == null) {
                lemmaNode.setLabel(t.getLemma());
                // add alignment for the lemma node
                alignments.add(new Alignment(lemmaNode.getName(), firstArg.getIndex()));
            }
            // if there is a second argument, we add an edge:
            if (t.hasTwoArguments()) {
                Argument targetArg = t.getArguments().get(1);
                GraphNode targetNode = graph.getNode(NODE_NAME_PREFIX+targetArg.getName());
                graph.addEdge(lemmaNode, targetNode, t.getPredicate().getDelexPredAsString());
                // the target node can't be a root node because it has an incoming edge (the one just created)
                // note: this assumes that there are no 'reverse' edges.
                // prepositions are not an exception:  cookie.nmod.beside(x_cookie, x_noun) : x_noun is not a root node
                notCandidatesForRoot.add(targetNode);
            }
        }
        // ** Alignment heuristic for proper names:
        /* we just *hope* that each name only appears once in a sentence:
         * Because we search for the first index in the sentence tokens list that equals the Name and that we
         * align accordingly
         * also note how we defined equals() on Argument
         * todo IMPORTANT: here is another alignment heuristic which can become problematic
         */
        // note: worst case quadratic in the length of the sentence (for each proper name we go iterate over the list of tokens)
        GraphNode properNameNode;
        for (Argument argument: logicalForm.getArgumentSet()) {
            if (argument.isProperName()) {
                properNameNode = graph.getNode(NODE_NAME_PREFIX+argument.getName());
                int token_position = sentenceTokens.indexOf(argument.getName());
                assert(token_position != -1);
                alignments.add(new Alignment(properNameNode.getName(), token_position));
                // todo proper name nodes are also not roots, need a verb:
                notCandidatesForRoot.add(properNameNode);
            }
        }
        // ** determine to which node to add the special root source
        /*
        * Unfortunately, the SGraph and GraphNode classes don't provide the option to search for nodes based on the
        * number of incoming edges. Therefore, I decided to -while building the graph- note down which nodes can't be
        * root nodes (see <code>notCandidatesForRoot</code>). The remaining nodes are root candidates and we hope that
        * there is always just one root candidate, otherwise throws a RuntimeException
        */
        Set<GraphNode> rootCandidates = new HashSet<>();
        for (String nodename: graph.getAllNodeNames()) {
            GraphNode n = graph.getNode(nodename);
            if (!notCandidatesForRoot.contains(n)) { rootCandidates.add(n); }
        }
        if (rootCandidates.size()!= 1) {  // 0 or more than 1 node that could function as a root node
            throw new RuntimeException("Need a single node as the root node: couldn't decide on one. " +
                    "number of root candidate nodes: " + rootCandidates.size());
        }
        else { // exactly one element
            GraphNode rootNode = rootCandidates.iterator().next();
            graph.addSource(ApplyModifyGraphAlgebra.ROOT_SOURCE_NAME, rootNode.getName());
        }
        return new MRInstance(sentenceTokens, graph, alignments);
    }

    /**
     * The method transforms a logicalForm to an SGraph, plus alignments to the sentenceTokens
     *
     * @param logicalForm parsed COGS logical form
     * @param sentenceTokens input tokens: needed for the alignments
     * @return MRInstance covering the SGraph, the alignments and the sentenceTokens
     * TODO: input validation (currently done as assertions instead of exceptions, also in sub-methods...)
     */
    public static MRInstance toSGraph(COGSLogicalForm logicalForm, List<String> sentenceTokens) {
        Objects.requireNonNull(logicalForm);
        Objects.requireNonNull(sentenceTokens);
        if (sentenceTokens.size() == 0 ) { throw new RuntimeException("Empty sentence not allowed"); }
        switch (logicalForm.getFormulaType()) {
            case LAMBDA:
                return LambdaToSGraph(logicalForm, sentenceTokens);
            case IOTA:
                // todo checking validity of indices: shouldn't be done with assert (public!) but with exception!
                int length = sentenceTokens.size();
                for (Argument arg: logicalForm.getArgumentSet()) {
                    assert !arg.isIndex() || (0 <= arg.getIndex() && arg.getIndex() < length);
                }
                return IotaToSGraph(logicalForm, sentenceTokens);
            case NAME:
                return NameToSGraph(logicalForm, sentenceTokens);
            default:
                // assert (false);
                throw new RuntimeException("There must be some formula type added but this method wasn't adapted.");
        }
    }

    public static COGSLogicalForm toLogicalForm(MRInstance mr) {
        // reverse of toSGraph
        // what kind of formula? LAMBDA, NAME or IOTA?
        // - split graphs  (uh: primitives, preposition...)
        // - revert iota
        // - add unary predicates (no outgoing edges (modula nmod ones) and not a proper Name? ): either put in conjunction or in prefix?
        // - re-lexicalize edges
        // - sort terms based on indices
        throw new NotImplementedException("Not implemented yet");
    }
}
