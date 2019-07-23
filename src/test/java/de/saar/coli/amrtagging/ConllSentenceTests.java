/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.saar.coli.amrtagging;

import com.google.common.collect.Lists;
import de.up.ling.tree.ParseException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import static org.junit.Assert.assertEquals;


/**
 *
 * @author matthias
 */
public class ConllSentenceTests {

    private Map<String,List<AmConllSentence>> files = new HashMap<>(); //amconll files
    
    @Before
    public void reading() throws IOException, ParseException{
        ClassLoader cl = this.getClass().getClassLoader(); 
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
        Resource[] resources = resolver.getResources("classpath:de/saar/coli/amrtagging/examples/*.amconll") ;
        for (Resource resource: resources){
            Reader r = new InputStreamReader(resource.getInputStream());

            List<AmConllSentence> snts = AmConllSentence.read(r);
            files.put(resource.getFilename(),snts);
        }

    }

    @Test
    public void eds(){
        // test EDS token ranges
        AmConllSentence eds = files.get("eds_1.amconll").get(0);
        assertEquals(eds.ranges(), Lists.newArrayList(new TokenRange(0,3), new TokenRange(4,10), new TokenRange(11,17), new TokenRange(18,19),
                new TokenRange(19,21),new TokenRange(22,29),new TokenRange(30,32),new TokenRange(33,35),new TokenRange(36,40),
                new TokenRange(41,46),new TokenRange(45,46),new TokenRange(47,55)));
        //check some head
        assertEquals(eds.get(3).getHead(),3);
        assertEquals(eds.get(5).getLexLabel(),"card__carg=$REPL$");
        //check if relexicalization works
        assertEquals(eds.get(5).getReLexLabel(),"card__carg=1000000");
    }



    
}
