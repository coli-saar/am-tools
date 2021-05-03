package de.saar.coli.amrtagging.formalisms.cogs;

import de.saar.coli.amrtagging.AlignedAMDependencyTree;
import de.saar.coli.amrtagging.Alignment;
import de.saar.coli.amrtagging.AmConllSentence;
import de.saar.coli.amrtagging.MRInstance;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.AllowedFormulaTypes;
import de.up.ling.irtg.algebra.ParserException;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.GraphEdge;
import de.up.ling.irtg.algebra.graph.GraphNode;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.Argument;
import de.saar.coli.amrtagging.formalisms.cogs.COGSLogicalForm.Term;
import de.up.ling.tree.ParseException;
import org.jgrapht.graph.DirectedMultigraph;

import java.util.*;

/**
 * Converts <code>COGSLogicalForm</code> to a <code>SGraph</code> and back (AmConLLSentence to logical form)<br>
 *
 * Version 1: very AMR-like<br>
 * - arguments of a term (<i>x_i, John, a</i>) become nodes<br>
 * - predicate names become edges unless it's a term with only one argument (<i>boy(x_1)</i>), then it's part of the <i>x_i</i> node
 * - iota: we treat iota as some special term: <i>* boy(x_1);</i> transformed to <i>the.iota(the, x_1_boy)</i><br>
 * - prepositions: the <i>nmod.preposition</i> edge belongs to the noun of the PP (not the modified noun!)<br>
 * - primitives: treated as graphs with potentially open sources...<br>
 * TODO: missing implementation:
 * - Alignment: is is 0-indexed or 1-indexed? currently assumes 0-indexed. Check what Alignment wants and maybe change..
 * - refactoring (is there duplicate code or very similar code that could be a method on its own?)
 * - what is a node name (not label) for proper names? need to recover in postprocessing something?
 * TODO: Problems
 * - alignments for determiners and proper names rely on heuristics and hope (see to-do-notes below)
 * - same would hold for prepositions, but the current encoding transforms them to edges (only nodes need alignments)
 * - for non-primitives we have to heuristically select a root node (heuristic: no incoming edges, excluding nmod ones)
 * @author piaw (weissenh)
 * Created April 2021
 */
public class LogicalFormConverter {
    public static final String LEMMA_SEPARATOR = "~~";  // todo currently only used for lambdavar "x_e~~giggle", "x_Ava~~Ava"
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
        assert(logicalForm.getFormulaType() == AllowedFormulaTypes.LAMBDA);
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
        assert (logicalForm.getFormulaType() == AllowedFormulaTypes.IOTA);
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
            assert(!arg.isLambdaVar()); // we are in a non-primitive, there clearly shouldn't be lambda variables
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

    private static boolean isProperName(String token) {
        // todo with full regex match or just first char? how to keep in sync with other conversion direction?
        if (token.length() == 0) {return false;} // todo what to do with empty string?
        char firstChar = token.charAt(0);
        return Character.isUpperCase(firstChar);
    }

    /// Checks: alignment always of span 1, one node per alignment only, valid indices as alignment
    private static void checkForValidAlignments(List<Alignment> alignments, int sentLength) throws RuntimeException, IndexOutOfBoundsException {
        // todo throw more specific exceptions than runtime exception
        for (Alignment a: alignments) {
            if (!a.span.isSingleton()) {
                throw new RuntimeException("Alignments need to be of span 1. Not true for " + a);
            }
            if (a.nodes.size() != 1) {
                throw new RuntimeException("Alignments need to be for one node only. Not true for " + a);
            }
            int start = a.span.start;
            if (!(0 <= start && start < sentLength)) {  // assumes 0-indexed // todo rather ignore invalid ones???
                throw new IndexOutOfBoundsException("Alignment starts need to be valid sentence positions. " +
                        "Not true for " + a + " and sentence length: " + sentLength);
            }
        }
    }

    /// decide on the formula type based on the sentence  (could also do graph, but its harder)
    private static AllowedFormulaTypes decideOnType(List<String> tokens) {
        if (tokens.size() == 1) {
            String firstToken = tokens.get(0);
            assert(firstToken.length()>0);
            if (isProperName(firstToken)) { return AllowedFormulaTypes.NAME; }
            else { return AllowedFormulaTypes.LAMBDA;}
        }
        else { return AllowedFormulaTypes.IOTA; }
    }

    // todo test this
    private static COGSLogicalForm nameToLForm(MRInstance mr) {
        List<String> sentenceTokens = mr.getSentence();
        int sentLength = sentenceTokens.size();
        assert(sentLength == 1);
        SGraph sg = mr.getGraph();
        // List<Alignment> alignments = mr.getAlignments();  // we ignore alignments here todo input validation?
        Collection<String> nodeNames = sg.getAllNodeNames();
        assert(nodeNames.size() == 1);
        String onlyNode = nodeNames.iterator().next();
        String label = sg.getNode(onlyNode).getLabel();  // todo directly label or need to postprocess node label? e.g.e_Ava
        return new COGSLogicalForm(Collections.singletonList(label)); // LF is basically 'Ava' or as token list: ['Ava']
        // return new COGSLogicalForm(mr.getSentence());  // this would be cheating
    }

    // todo test this implementation, also adapt (other direction: lf2graph currently not working)
    private static COGSLogicalForm lambdaToLForm(MRInstance mr) {
        // x_e / "e~~want"     x_b<b>/null
        List<String> sentenceTokens = mr.getSentence();
        int sentLength = sentenceTokens.size();
        assert(sentLength == 1);
        SGraph sg = mr.getGraph();
        DirectedMultigraph<GraphNode, GraphEdge> meg = sg.getGraph();
        // List<Alignment> alignments = mr.getAlignments();  // we ignore alignments here todo input validation?
        Collection<String> nodeNames = sg.getAllNodeNames();

        List<Term> conjuncts = new ArrayList<>();
        List<Argument> lambdavars = new ArrayList<>();

        // Step 1: (Node iter) get all arguments, (and unary predicates: iteration over nodes will get them for free)
        Map<String, Argument> arguments = new HashMap<>();
        String nodelabel; GraphNode node;
        String lemma = null;
        for (String nodename: nodeNames) {
            node = sg.getNode(nodename);
            nodelabel = node.getLabel();
            // (1) create Argument object (lambda var!!) todo check for lambda var and not proper name, index
            String argname;
            // todo maybe transform into a function: getLambdaVarName( node ): either from label or from source
            if (nodelabel == null) {
                // assume that there is a source
                Collection<String> srcs = sg.getSourcesAtNode(nodename);
                assert(srcs.size()==1);
                argname = srcs.iterator().next();
            }
            else {
                // assume no source except root? assume contains ~~   //e~want
                assert(nodelabel.contains(LEMMA_SEPARATOR));
                String[] parts = nodelabel.split(LEMMA_SEPARATOR);
                argname = parts[0];
                assert(argname.equals("a") || argname.equals("b") || argname.equals("e")); // todo do this check here?
                assert(lemma == null);
                lemma = parts[1];
            }
            Argument arg = new Argument(argname);
            lambdavars.add(arg);
            arguments.put(nodename, arg);
        }
        // (2) predicates: if one node only one unary, otherwise only binary
        if (nodeNames.size()==1) {  // unary predicate
            // what about preposition edges? todo assume no prep edges here
            // build term with just one argument
            List<String> pred = new ArrayList<>();
            pred.add(lemma);
            assert(lambdavars.size()==1);
            Term t = new Term(pred, lambdavars);
            conjuncts.add(t);
        }
        else {  // binary predicates
            for (GraphEdge edge: meg.edgeSet()) {  // similar to iota...
                GraphNode source = edge.getSource();
                GraphNode target = edge.getTarget();
                String label = edge.getLabel();  // agent, theme...
                assert(!label.equals(IOTA_EDGE_LABEL));
                assert(lemma != null);  // todo what if no lemma? rather do exception here?
                String fulllabel = lemma+"."+label;
                List<String> pred = Arrays.asList(fulllabel.split("\\."));  // split at literal ., not regex .

                // (2) get the two arguments // todo assert they are in arguments?
                Argument one = arguments.get(source.getName());
                Argument two = arguments.get(target.getName());
                assert(one.isLambdaVar() && two.isLambdaVar());
                // (3) build term and add it to the conjunction
                Term term = new Term(pred, new ArrayList<>(Arrays.asList(one, two)));
                conjuncts.add(term);
            }
        }
        Argument[] lvs = lambdavars.toArray(new Argument[0]);
        return new COGSLogicalForm(lvs, conjuncts);  // lvs are assumed to be sorted in the constructor call
    }

    // todo test this
    private static COGSLogicalForm iotaToLForm(MRInstance mr) {
        List<String> sentenceTokens = mr.getSentence();
        int sentLength = sentenceTokens.size();
        assert(sentLength > 1);
        SGraph sg = mr.getGraph();
        DirectedMultigraph<GraphNode, GraphEdge> meg = sg.getGraph();
        List<Alignment> alignments = mr.getAlignments();  // Alignment is set of nodes and span.start, span.end

        // Input validate alignments todo rather ignore invalid indices???

        // Step 0: (Alignment iter) Convenient map for node names to sentence positions, also for lemmas
        Map<String, Integer> nodeName2Index = new HashMap<>();  // node name -> start of aligned span (span size == 1)
        Map<String, String> nodeName2Lemma = new HashMap<>();  // node name -> lemma (relevant for re-lexicalization)
        for (Alignment a: alignments) {
            assert(a.span.isSingleton());
            assert(a.nodes.size()==1);
            String nodename = a.nodes.iterator().next(); // get first and only element in that set
            Integer start = a.span.start;  // todo assume 0-indexed
            assert(!nodeName2Index.containsKey(nodename));// todo do I need to check for overwrites?
            nodeName2Index.put(nodename, start);
            assert(!nodeName2Lemma.containsKey(nodename));// todo do I need to check for overwrites?
//            String lemma;
            GraphNode node = sg.getNode(nodename);
            nodeName2Lemma.put(nodename, node.getLabel());  // todo label equals lemma: always true?
//            String[] parts = node.getLabel().split(LEMMA_SEPARATOR);
//            if (parts.length == 2) {
//                lemma = parts[1];
//                nodeName2Lemma.put(nodename, lemma);
//            } // if it doesn't have
//            assert (parts.length <= 2);  // todo input validation with exceptions rather than assert?
        } // also assume 0-indexed?
        // todo also see whether other conversion direction can be simplified? found way to access edges

        List<Term> conjuncts = new ArrayList<>();
        List<Term> iotas = new ArrayList<>();

        // Step 1: (Node iter) get all arguments, (and unary predicates: iteration over nodes will get them for free)
        Map<String, Argument> arguments = new HashMap<>();
        Collection<String> nodes = sg.getAllNodeNames();
        String nodelabel; GraphNode node;
        for (String nodename: nodes) {
            node = sg.getNode(nodename);
            nodelabel = node.getLabel();
            // (1) create Argument object
            // - Argument can either be Index, ProperName, the, ...( LambdaVar)
            // - todo where to get String for argument from? Lemma? Full Nodelabel?
            // - todo refactor this into separate function that can also be used my lambda...
            Argument arg;
            String lemmaOrNodeLabel = nodeName2Lemma.getOrDefault(nodename, nodelabel);
            if (isProperName(lemmaOrNodeLabel)) { // todo lemma or full nodelabel?
                arg = new Argument(lemmaOrNodeLabel);
                arg.setIndexForProperName(nodeName2Index.get(nodename));
            }
            else {  // todo here more options: index, propername, iota-the, lambda-var
                arg = new Argument(nodeName2Index.get(nodename));
            }
            arguments.put(nodename, arg);

            // (2) find nodes for which unary predicate should be added.
            // Nodes must have 0 outgoing edges ('nmod'=preposition edges don't count) and shouldn't be proper names
            // (NB: only searching for iota edges would miss the indefinite NPs: 'a cookie' : ..AND cookie(x_1) AND...)
            // outDegree = meg.outDegreeOf(node); // what about preposition edges?
            int outDegree = 0;  // todo transform to method OutDegreeMinusPrepEdge?
            for (GraphEdge edge: meg.outgoingEdgesOf(node)) {
                if (!edge.getLabel().startsWith("nmod")) {  // todo magic string nmod
                    outDegree += 1;
                    break;  // don't care if outdegree 1 or higher: care about 0 vs >0
                }
            } // for outgoing edges
            if (outDegree == 0 && !arg.isProperName()) {
                // build term with just one argument
                List<String> pred = new ArrayList<>();
                pred.add(lemmaOrNodeLabel);  // todo lemma? not Nodelabel
                List<Argument> args = new ArrayList<>();
                args.add(arg);
                Term t = new Term(pred, args);
                // decide whether to put it into prefix (iotas) or in conjunction (conjuncts)
                //  i.e. whether we find an incoming iota edge
                boolean isIota = false;
                for (GraphEdge edge: meg.incomingEdgesOf(node)) {
                    if (edge.getLabel().equals(IOTA_EDGE_LABEL)) {
                        isIota = true;
                        break;
                    }  // if found iota edge
                }  // for incoming edges
                if (isIota) { iotas.add(t); }
                else { conjuncts.add(t); }
            }
        }

        // Step 2: get binary predicates/terms
        for (GraphEdge edge: meg.edgeSet()) {
            GraphNode source = edge.getSource();
            GraphNode target = edge.getTarget();
            String label = edge.getLabel();  // agent, nmod.in ,

            // (0) exclude special iota edges: they are just to signal that their target node is part of the iota prefix
            if (label.equals(IOTA_EDGE_LABEL)) { continue; }
            // (1) add lemma of the source node to the predicate name ('re-lexicalize')
            String lemma = nodeName2Lemma.get(source.getName());
            assert(lemma != null);  // todo what if no lemma? rather do exception here?
            String fulllabel = lemma+"."+label;
            List<String> pred = Arrays.asList(fulllabel.split("\\."));  // split at literal ., not regex .

            // (2) get the two arguments // todo assert they are in arguments?
            Argument one = arguments.get(source.getName());  // todo assert that one is an Index? (except lambda)
            Argument two = arguments.get(target.getName());  // todo assert can be index, name (except lambda)
            // (3) build term and add it to the conjunction
            Term term = new Term(pred, new ArrayList<>(Arrays.asList(one, two)));
            conjuncts.add(term);
        }

        // Step 3: sort terms in prefix and conjunction separately based on index
        // assumed to implicitly happen in constructor of COGSLogicalForm
        // Step 4: Build COGSLogicalForm and return it
        return new COGSLogicalForm(iotas, conjuncts);
    }

    // todo test this!
    // todo alignments extract 0- or 1-based?
    // todo problem [some primitives]: align=true for AlignedAMDependencyTree.evaluate() enforces empty type!
    public static COGSLogicalForm toLogicalForm(AmConllSentence amSent) throws ParserException, ParseException, AlignedAMDependencyTree.ConllParserException {
        Objects.requireNonNull(amSent);
        // makes use to toLogicalForm(MRInstance): to built an MRInstance we need
        // a list of words, a list of alignments and an SGraph
        if (amSent.words().size()==1) {
            // not all primitives have sources beside the root node, but some. and for these, evaluate(true) will
            // remove the sources
            // assuming no artificial root
            System.err.println("toLF: Possible primitive: open sources might get stripped");  // todo!
        }

        // (1) list of words:
        List<String> tokens = amSent.words();  // todo do I have to take care of any artificial root?

        // (2) SGraph and list of alignments:
        // to do: for primitives (diagnose? 1 word sentence?), directly use supertag as SGraph if possible? Alignments are trivial
        AlignedAMDependencyTree amdep = AlignedAMDependencyTree.fromSentence(amSent);
        // todo problem align=true enforces empty type!!!
        SGraph evaluatedGraph = amdep.evaluate(true); // get graphs with alignment annotations in graph nodes
        List<Alignment> alignments = AlignedAMDependencyTree.extractAlignments(evaluatedGraph);
        AlignedAMDependencyTree.stripAlignments(evaluatedGraph);  // get rid of the alignment markers in the graph nodes
        // todo is the evaluate(true)+stripAlignments producing the same graph as evaluate(false) ??
        // todo alignments: 0- or 1-based? do I have to postprocess them? (in/decrement by one?)

        // (3) we have everything to built an MRInstance and call toLogicalForm on that!
        MRInstance mr = new MRInstance(tokens, evaluatedGraph, alignments);
        return toLogicalForm(mr);
    }

    // todo IMPORTANT don't decide on tokens which formula type: remember can't assert that graph matches input
    //  (model could choose to predict a lambda term for a proper name primitive)
    // reverse of toSGraph
    /**
     * Converting an SGraph back to the logical form style of COGS (for post-processing, evaluation)
     *
     * @param mr MRInstance covering the SGraph, the sentence tokens and alignments
     * @return parsed COGSLogicalForm
     */
    public static COGSLogicalForm toLogicalForm(MRInstance mr) {
        Objects.requireNonNull(mr);
        List<String> sentenceTokens = mr.getSentence();
        int sentLength = sentenceTokens.size();
        assert(sentLength > 0);  // todo input validation with exception rather than assert
        // SGraph sg = mr.getGraph();
        List<Alignment> alignments = mr.getAlignments();
        checkForValidAlignments(alignments, sentLength);  // if checks fail can throw exceptions

        // 1. what kind of formula? NAME, LAMBDA, IOTA?
        AllowedFormulaTypes type = decideOnType(sentenceTokens);  // todo decide based on graph!! can't assume we have a 'good' graph
        // todo input validation: if type NAME: only one node expected, ...in LAMBDA no 'iota' edge, ...
        switch (type) {
            case LAMBDA:
                return lambdaToLForm(mr);
            case IOTA:
                return iotaToLForm(mr);
            case NAME:
                return nameToLForm(mr);
            default:
                // assert (false);
                throw new RuntimeException("There must be some formula type added but this method wasn't adapted.");
        }
    }
}
