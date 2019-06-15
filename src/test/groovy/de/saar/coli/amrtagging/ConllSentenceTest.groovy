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
        
        assertEquals(2, sent[0].getHead());
        assertEquals("APP_s", sent[0].getEdgeLabel());
        
        assertEquals(0, sent[1].getHead());
        assertEquals("root", sent[1].getEdgeLabel());
        
        assertEquals(2, sent[2].getHead());
        assertEquals("APP_o", sent[2].getEdgeLabel());
    }
    
    @Test
    public void testComplexAmTermToConllSentence() {
        ConllSentence sent = s([e(1, "John"), e(2, "thinks"), e(3, "that"), e(4, "Mary"), e(5, "sleeps")]);
        Tree<String> amterm = pt("APP_s(APP_v(thinks, APP_s(sleeps, Mary)), John)")
        List leafOrderToStringOrder = [1, 4, 3, 0];
        
        sent.setDependenciesFromAmTerm(amterm, leafOrderToStringOrder);
        
        assertEquals(2, sent[0].getHead());
        assertEquals("APP_s", sent[0].getEdgeLabel());
        
        assertEquals(0, sent[1].getHead());
        assertEquals("root", sent[1].getEdgeLabel());
        
        assertEquals(5, sent[3].getHead());
        assertEquals("APP_s", sent[3].getEdgeLabel());
        
        assertEquals(2, sent[4].getHead());
        assertEquals("APP_v", sent[4].getEdgeLabel());
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

