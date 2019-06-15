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
        Tree<String> amterm = pt("APP_s(APP_o(xxlikes, xxmary), xxjohn)");
        List leafOrderToStringOrder = [1, 2, 0];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder);
        
        assertIncoming(sent[0], 2, "APP_s", "xxjohn")
        assertIncoming(sent[1], 0, ConllEntry.ROOT_SYM, "xxlikes")
        assertIncoming(sent[2], 2, "APP_o", "xxmary")
    }
    
    @Test
    public void testComplexAmTermToConllSentence() {
        ConllSentence sent = s([e(1, "John"), e(2, "thinks"), e(3, "that"), e(4, "Mary"), e(5, "sleeps")]);
        Tree<String> amterm = pt("APP_s(APP_v(xxthinks, APP_s(xxsleeps, xxMary)), xxJohn)")
        List leafOrderToStringOrder = [1, 4, 3, 0];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder);
        
        assertIncoming(sent[0], 2, "APP_s", "xxJohn")
        assertIncoming(sent[1], 0, ConllEntry.ROOT_SYM, "xxthinks")
        assertIncoming(sent[2], 0, ConllEntry.IGNORE, ConllEntry.DEFAULT_NULL)
        assertIncoming(sent[3], 5, "APP_s", "xxMary")
        assertIncoming(sent[4], 2, "APP_v", "xxsleeps")
    }
    
    public void assertIncoming(ConllEntry entry, int expectedHead, String expectedEdgeLabel, String expectedSupertag) {
        assertEquals(expectedHead, entry.getHead());
        assertEquals(expectedEdgeLabel, entry.getEdgeLabel());
        assertEquals(expectedSupertag, entry.getDelexSupertag());
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

