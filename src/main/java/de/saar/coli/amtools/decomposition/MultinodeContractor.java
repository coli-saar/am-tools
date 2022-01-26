package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.saar.coli.amtools.decomposition.formalisms.decomposition_packages.DecompositionPackage;
import de.up.ling.irtg.algebra.graph.AMDependencyTree;
import de.up.ling.irtg.algebra.graph.ApplyModifyGraphAlgebra;
import de.up.ling.irtg.algebra.graph.SGraph;
import de.up.ling.irtg.util.Counter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultinodeContractor {


    public static AMDependencyTree contractMultinodeConstant(AMDependencyTree amDep, Set<String> nodesInConstant,
                                                             DecompositionPackage decompositionPackage) {


        if (amDep == null) {
            return null; // we handle that elsewhere
        }

        List<AMDependencyTree> attachInThisTree = new ArrayList<>();
        Set<Pair<String, AMDependencyTree>> replaceThis = new HashSet<>();

        Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> result;
        try {
            result = buildContractedTree(amDep, attachInThisTree,
                    replaceThis, nodesInConstant, decompositionPackage, false);
        } catch (IllegalArgumentException ex) {
            System.err.println("illegal MOD move in constant contraction");
            System.err.println(nodesInConstant.toString());
            System.err.println(amDep.toString());
//            outcomeCounter.add("subfail illegal MOD move");
            throw ex;
        }
        AMDependencyTree toBeInserted = result.left;
        List<Pair<String, AMDependencyTree>> attachBelow = result.right;

        int virtuallyAttachAtTop = isInNodeset(amDep.getHeadGraph(), decompositionPackage, nodesInConstant)? 1 : 0;

        if (attachInThisTree.size() +virtuallyAttachAtTop > 1) {
//            outcomeCounter.add("subfail disconnected alignment");
            throw new IllegalArgumentException("Constant to be contracted is disconnected");
        } else {
            try {
                toBeInserted.evaluate();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Constant to be contracted leads to illegal evaluation");
            }
            AMDependencyTree replacement = new AMDependencyTree(toBeInserted.evaluate(), attachBelow.toArray(new Pair[attachBelow.size()]));
            if (attachInThisTree.isEmpty()) {
                return replacement;
            } else {
                AMDependencyTree attachHere = attachInThisTree.iterator().next();
                attachHere.removeEdge(replaceThis.iterator().next().left, replaceThis.iterator().next().right);
                attachHere.addEdge(replaceThis.iterator().next().left, replacement);
                return amDep;
            }
        }

    }


    /**
     * returns the AMDependency tree that will give the contracted constant when evaluated, as well as the list of edges
     * that need to be attached at its top. Calls itself recursively.
     * @param amDep
     * @param attachInThisTree attach the contraction result below this tree (is a set to determine invalid results:
     * @param replaceThis
     * @param nodesInConstant
     * @param decompositionPackage
     * @return
     */
    private static Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> buildContractedTree(AMDependencyTree amDep, List<AMDependencyTree> attachInThisTree, Set<Pair<String, AMDependencyTree>> replaceThis,
                                                                                                   Set<String> nodesInConstant, DecompositionPackage decompositionPackage, boolean percolationRequired) {

        if (isInNodeset(amDep.getHeadGraph(), decompositionPackage, nodesInConstant)) {
            AMDependencyTree ret = new AMDependencyTree(amDep.getHeadGraph());
            //then this node is in the constant to be contracted
            List<Pair<String, AMDependencyTree>> attachBelow = new ArrayList<>();
            for (Pair<String, AMDependencyTree> opAndChild : amDep.getOperationsAndChildren()) {
                Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> rec =buildContractedTree(opAndChild.right,
                        attachInThisTree, replaceThis, nodesInConstant, decompositionPackage, true);
                if (isInNodeset(opAndChild.right.getHeadGraph(), decompositionPackage, nodesInConstant)) {
                    AMDependencyTree retHere = rec.left;
                    attachBelow.addAll(rec.right);
                    ret.addEdge(opAndChild.left, retHere);
                    // fix type for evaluation later
                    if (opAndChild.left.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                        for (Pair<String, AMDependencyTree> opAndChildBelow : rec.right) {
                            ApplyModifyGraphAlgebra.Type newHeadType = addAllToType(amDep.getHeadGraph().right, opAndChildBelow.right.evaluate().right, getSource(opAndChildBelow.left), null);
                            ret.setHeadGraph(new Pair<>(ret.getHeadGraph().left, newHeadType));
                        }
                    } else {
                        // OP_APPLICATION
                        for (Pair<String, AMDependencyTree> opAndChildBelow : rec.right) {
                            ApplyModifyGraphAlgebra.Type newHeadType = addAllToType(amDep.getHeadGraph().right, opAndChildBelow.right.evaluate().right, getSource(opAndChildBelow.left), getSource(opAndChild.left));
                            ret.setHeadGraph(new Pair<>(ret.getHeadGraph().left, newHeadType));
                        }
                    }
                } else {
                    if (percolationRequired && opAndChild.left.startsWith(ApplyModifyGraphAlgebra.OP_MODIFICATION)) {
                        throw new IllegalArgumentException("Constant to be contracted leads to illegal MOD percolation");
                    }
                    attachBelow.add(opAndChild);
                }
            }
            return new Pair<>(ret, attachBelow);
        } else {
            //then this node is not in the constant to be contracted. We call the function recursively on the children (passing the result up if there is one)
            // and note whether the whole thing should be attached here.
            Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> ret = null; // will pass up any result from below
            for (Pair<String, AMDependencyTree> opAndChild : amDep.getOperationsAndChildren()) {
                Pair<AMDependencyTree, List<Pair<String, AMDependencyTree>>> rec = buildContractedTree(opAndChild.right,
                        attachInThisTree, replaceThis, nodesInConstant, decompositionPackage, false);
                if (rec != null) {
                    ret = rec; // don't need to worry about overwriting / multiple results here; that will happen in the parent function (by checking that attachInThisTree is a singleton).
                }
                if (isInNodeset(opAndChild.right.getHeadGraph(), decompositionPackage, nodesInConstant)) {
                    attachInThisTree.add(amDep);
                    replaceThis.add(opAndChild);
                }
            }
            return ret;
        }

    }


    private static boolean isInNodeset(Pair<SGraph, ApplyModifyGraphAlgebra.Type> asgraph, DecompositionPackage decompositionPackage, Set<String> nodesInConstant) {
        return nodesInConstant.contains(decompositionPackage.getLexNodeFromGraphFragment(asgraph.left).getName());
    }

    private static String getSource(String applyOperation) {
        return applyOperation.substring(ApplyModifyGraphAlgebra.OP_APPLICATION.length());
    }


    private static ApplyModifyGraphAlgebra.Type addAllToType(ApplyModifyGraphAlgebra.Type origType, ApplyModifyGraphAlgebra.Type typeToAdd, String parentSourceToAdd, String addAsChildOfThisSource) {
        ApplyModifyGraphAlgebra.Type ret = origType;
        ret = ret.addSource(parentSourceToAdd);
        if (addAsChildOfThisSource != null) {
            ret = ret.setDependency(addAsChildOfThisSource, parentSourceToAdd, parentSourceToAdd);
        }
        for (String srcToAdd : typeToAdd.getAllSources()) {
            ret = ret.addSource(srcToAdd);
            ret = ret.setDependency(parentSourceToAdd, srcToAdd, srcToAdd);
        }
        for (ApplyModifyGraphAlgebra.Type.Edge edge : typeToAdd.getAllEdges()) {
            ret = ret.setDependency(edge.getSource(), edge.getTarget(), edge.getLabel());
        }
        return ret;
    }


}
