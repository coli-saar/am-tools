/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package de.saar.coli.amrtagging

import static org.junit.Assert.*
import org.junit.*
import static de.up.ling.irtg.util.TestingTools.*;
import de.up.ling.tree.Tree


/**
 *
 * @author koller
 */
class ConllSentenceTest {
    
    @Test
    public void testAmTermToConllSentence() {
        ConllSentence sent = s([e(1, "John"), e(2, "likes"), e(3, "Mary")]);
        Tree<String> amterm = pt("APP_s(APP_o(likes, mary), john)");
        List leafOrderToStringOrder = [1, 2, 0];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder);
        
        assertIncoming(sent[0], 2, "APP_s")
        assertIncoming(sent[1], 0, ConllEntry.ROOT_SYM)
        assertIncoming(sent[2], 2, "APP_o")
    }
    
    @Test
    public void testComplexAmTermToConllSentence() {
        ConllSentence sent = s([e(1, "John"), e(2, "thinks"), e(3, "that"), e(4, "Mary"), e(5, "sleeps")]);
        Tree<String> amterm = pt("APP_s(APP_v(thinks, APP_s(sleeps, Mary)), John)")
        List leafOrderToStringOrder = [1, 4, 3, 0];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder);
        
        assertIncoming(sent[0], 2, "APP_s")
        assertIncoming(sent[1], 0, ConllEntry.ROOT_SYM)
        assertIncoming(sent[2], 0, ConllEntry.IGNORE)
        assertIncoming(sent[3], 5, "APP_s")
        assertIncoming(sent[4], 2, "APP_v")
    }
    
    public void assertIncoming(ConllEntry entry, int expectedHead, String expectedEdgeLabel) {
        assertEquals(expectedHead, entry.getHead());
        assertEquals(expectedEdgeLabel, entry.getEdgeLabel());
    }
    
    public ConllSentence s(List<ConllEntry> entries) {
        ConllSentence ret = new ConllSentence();
        ret.addAll(entries);
        return ret;
    }
    
    public ConllEntry e(int pos, String form) {
        return new ConllEntry(pos, form);
    }
}

