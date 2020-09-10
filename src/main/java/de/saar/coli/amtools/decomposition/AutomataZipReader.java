package de.saar.coli.amtools.decomposition;

import de.saar.basic.Pair;
import de.up.ling.irtg.automata.Rule;
import de.up.ling.irtg.automata.TreeAutomaton;
import de.up.ling.irtg.codec.BinaryIrtgInputCodec;
import de.up.ling.irtg.codec.BinaryIrtgOutputCodec;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class AutomataZipReader {

    private static final BinaryIrtgInputCodec codec = new BinaryIrtgInputCodec();
    private final ZipFile zipFile;

    public AutomataZipReader(String filepath) throws IOException {
        zipFile = new ZipFile(filepath, StandardCharsets.UTF_8);
    }

    public Map<Rule, Pair<Integer, String>> readSupertagMap(int i, TreeAutomaton<String> auto) throws IOException, ClassNotFoundException {
        ZipEntry zipEntry = zipFile.getEntry(i+".supertagmap");
        InputStream inputStream = this.zipFile.getInputStream(zipEntry);
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        HashMap<Rule, Pair<Integer, String>> fileObj = (HashMap<Rule, Pair<Integer, String>>) objectInputStream.readObject();
        objectInputStream.close();
        Map<Rule, Rule> ruleIdentityResolver = new HashMap<>();
        for (Rule rule : auto.getRuleSet()) {
            ruleIdentityResolver.put(rule, rule);
        }
        HashMap<Rule, Pair<Integer, String>> ret = new HashMap<>();
        for (Map.Entry<Rule, Pair<Integer, String>> entry : fileObj.entrySet()) {
            ret.put(ruleIdentityResolver.get(entry.getKey()), entry.getValue());
        }
        return ret;
    }

    public Map<Rule, Pair<Pair<Integer, Integer>, String>> readEdgeMap(int i, TreeAutomaton<String> auto) throws IOException, ClassNotFoundException {
        ZipEntry zipEntry = zipFile.getEntry(i+".edgemap");
        InputStream inputStream = this.zipFile.getInputStream(zipEntry);
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        HashMap<Rule, Pair<Pair<Integer, Integer>, String>> fileObj = (HashMap<Rule, Pair<Pair<Integer, Integer>, String>>) objectInputStream.readObject();
        objectInputStream.close();
        Map<Rule, Rule> ruleIdentityResolver = new HashMap<>();
        for (Rule rule : auto.getRuleSet()) {
            ruleIdentityResolver.put(rule, rule);
        }
        HashMap<Rule, Pair<Pair<Integer, Integer>, String>> ret = new HashMap<>();
        for (Map.Entry<Rule, Pair<Pair<Integer, Integer>, String>> entry : fileObj.entrySet()) {
            ret.put(ruleIdentityResolver.get(entry.getKey()), entry.getValue());
        }
        return ret;
    }

    public TreeAutomaton<String> readAutomaton(int i) throws IOException {
        ZipEntry zipEntry = zipFile.getEntry(i+".irtb");
        InputStream inputStream = this.zipFile.getInputStream(zipEntry);
        return codec.read(inputStream).getAutomaton();
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        AutomataZipReader reader = new AutomataZipReader("C:\\Users\\Jonas\\Documents\\Work\\experimentData\\unsupervised2020\\dm\\automataData.zip");
        TreeAutomaton<String> auto = reader.readAutomaton(0);
        System.out.println(auto);
        System.out.println(reader.readSupertagMap(0, auto));
        System.out.println(reader.readEdgeMap(0, auto));
    }

}
