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
    public void testDecode() {
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

}

