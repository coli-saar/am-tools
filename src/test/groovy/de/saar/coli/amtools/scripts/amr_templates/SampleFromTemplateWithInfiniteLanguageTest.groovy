/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.saar.coli.amtools.scripts.amr_templates

import de.saar.coli.amtools.script.amr_templates.SampleFromTemplateWithInfiniteLanguage
import de.up.ling.tree.Tree
import org.junit.Test

import static de.up.ling.irtg.util.TestingTools.pt

/**
 *
 * @author koller
 */
class SampleFromTemplateWithInfiniteLanguageTest {


    @Test
    public void testDescendantsAncestorsBasic() {
        Tree<String> tree = pt("a(b, a(b), c(b, b))");
        Set<String> ancestors = new HashSet<>();
        ancestors.add("a");
        Set<String> descendants = new HashSet<>();
        descendants.add("b");
        int count = SampleFromTemplateWithInfiniteLanguage.countAncestorDescendantPairsInTree(tree,
                                                                                              ancestors,
                                                                                              descendants,
                                                                                              false);
        assert count == 5;

        int countIgnoreRightmostBranch = SampleFromTemplateWithInfiniteLanguage.countAncestorDescendantPairsInTree(tree,
                ancestors,
                descendants,
                true);
        assert countIgnoreRightmostBranch == 2;
    }

    @Test
    public void testDescendantsAncestorsFailCase() {
        Tree<String> tree = pt("Sent(SubjCtrlTbar(attempted,VbarSubjCtrl(Coord_Subj_Ctrl_V(and_subj_control_verb,want,love),attend)),we)");
        Set<String> andInfRuleLabels = new HashSet<>();
        andInfRuleLabels.add("Coord_Open_S_inf");
        andInfRuleLabels.add("Coord_3_Open_S_inf");
        andInfRuleLabels.add("Coord_Subj_Ctrl_V");
        andInfRuleLabels.add("Coord_3_Subj_Ctrl_V");

        Set<String> forbiddenRuleLabels = new HashSet<>();
        forbiddenRuleLabels.add("VbarSubjCtrl");
        forbiddenRuleLabels.add("VbarObjCtrl");
        int count = SampleFromTemplateWithInfiniteLanguage.countAncestorDescendantPairsInTree(tree,
                forbiddenRuleLabels,
                andInfRuleLabels,
                false);
        assert count == 1;
    }


}

